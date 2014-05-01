package uk.ac.susx.tag.dependencyparser;

import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import uk.ac.susx.tag.dependencyparser.classifiers.Classifier;
import uk.ac.susx.tag.dependencyparser.datastructures.SparseBinaryVector;
import uk.ac.susx.tag.dependencyparser.datastructures.StringIndexer;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;
import uk.ac.susx.tag.dependencyparser.transitionselectionmethods.SelectionMethod;

import java.io.*;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * The main top-level execution stage.
 *
 * Functions for training and parsing are here. See method comments. The class is divided into 3 selections:
 *  1. Parsing/Prediction functionality
 *  2. Training functionality
 *  3. General functionality
 *
 * TRAINING:
 *  Look for a static train methods. There is one train method that has all the functionality, and a bunch of others
 *  with fewer or different arguments that assign sensible defaults and delegate to the main train method. Each train
 *  method saves any relevant model data, and also returns an instance of the trained Parser.
 *
 * PARSING:
 *  If you want to parse something with a trained parser, then you should use one of the constructors with the
 *  appropriate arguments in the top half of the class. Then pass your data to a "parseSentence" or "parseFile" method.
 *
 *
 * CONCURRENCY NOTE:
 *  I have made every effort to ensure that "parseSentence" methods on this class can be called concurrently, but this
 *  functionality remains to be tested. TODO
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class Parser {

    // If the parser is done with a sentence, and left tokens without a head, then the ROOT token is assigned as the
    // head with the relation below. This is consistent with the Stanford dependency scheme.
    private static final String rootRelation = "root";

    /*
      The following are what constitute the information that the parser leans on during parsing and training.
      Efforts have been made such that these objects are either stateless, or are only READ FROM during PARSE-TIME
      (index and classifier are modified during train-time).  Hopefully this means that "parseSentence" can be called
      concurrently without problems.
     */
    private Index index;               // Features and Transitions are indexed, the index maintains a 2-way mapping for each.
    private Classifier classifier;     // This is the machine learning model used to predict the correct transition to make.
    private FeatureTable featureTable; // This represents the specification of what happens during feature extraction.
    private ParseStyle parseStyle;     // This represents how parsing is to be accomplished (i.e. what datastructure, what transitions)
    private SelectionMethod selectionMethod; // This represents how we use the classifier output to select the next transition




/***********************************************************************************************************************
 *
 * Prediction functionality
 *
 *   This section consists of constructors and parse methods. In order to get parsing, you'll construct the Parser
 *   instance, then call one of the parse methods.
 *
 *   The responsibility of the constructor is to establish the type of parsing, classifier, and transition selection
 *   methods, and read in the feature/transition index and parser model.
 *
 *   Then the parse methods use this data to slap dependencies on tokens.
 *
 *   Alternatively, you can look at the training section, which trains a parser from scratch, and returns a constructed
 *   parser as its output. That parser will be ready to receive calls to its parse methods.
 *
 ***********************************************************************************************************************/

    /**
     * All Defaults:
     *
     *  feature table = (as found in resources) mostly PoS based, with some deprel and form. Enough to evaluate at
     *                  around 90% unlabelled attachment score if trained on the training section of WSJ and tested
     *                  on the development. Bear in mind that the default model for this parser is trained on ALL of WSJ.
     *  classifier  = linear svm
     *  parse style = arc eager
     *  transition selection method = confidence-based (using classifier's decision scores)
     *  model = training on all of WSJ, using CMU PoS tags and Stanford dependencies.
     */
    public Parser() throws IOException {
        this("full_wsj_cmu_pos_stanford_dep");
    }

    /**
     * Convenience method with same defaults as above except that the user can specify another index and model to be
     * found in the jar's resources. The index file is expected to be in the resources folder with name:
     * trainingData+"-index", and the model with name: trainingData+"-model".
     */
    public Parser(String trainingData) throws IOException {

        // Get type and load classifier model
        File model = File.createTempFile("model", null);
        model.deleteOnExit();

        try (BufferedOutputStream modelStream = new BufferedOutputStream(new FileOutputStream(model)) ){
            Resources.copy(Resources.getResource(trainingData+"-model"), modelStream);
        }
        classifier = Options.getClassifier("linear-svm");
        classifier.load(model);
        if (!model.delete()) System.err.print("WARNING: model temp file was not deleted: "+ model.getAbsolutePath());

        // Load and set index to read only
        this.index = Index.load(Resources.getResource(trainingData+"-index").openStream());
        this.index.setReadOnly(true);

        // Load feature table
        featureTable = new FeatureTable(Resources.getResource("feature_table.txt").openStream());

        // Ascertain parse style
        parseStyle = Options.getParserStyle("arc-eager");

        // Ascertain transition selection method
        selectionMethod = Options.getSelectionMethod("confidence");
    }

    /**
     * Convenience method with sensible defaults. Same defaults as above, except the index and model can be specified
     * as file locations.
     */
    public Parser (File index, File model) throws IOException {
        this(new FileInputStream(index), model);
    }

    /**
     * Convenience method with sensible defaults. Same defaults as above, except the index is specified as an inputstream.
     * (I would have done the same with the model argument, but liblinear doesn't support this, so I haven't made my
     *  classifiers try to support this).
     */
    public Parser (InputStream index, File model) throws IOException {
        // Load and set Index to read-only
        this.index = Index.load(index);
        this.index.setReadOnly(true);

        // Get type and load classifier
        classifier = Options.getClassifier("linear-svm");
        classifier.load(model);

        // Load feature table
        featureTable = new FeatureTable(Resources.getResource("feature_table.txt").openStream()); // The FeatureTable ensures that resource is closed.

        // Ascertain parse style
        parseStyle = Options.getParserStyle("arc-eager");

        // Ascertain transition selection method
        selectionMethod = Options.getSelectionMethod("confidence");
    }

    /**
     * Construct a parser that has been trained already, ready for parsing. Assume no defaults.
     * @param index The file containing the ID-->String mappings for transitions and features.
     * @param model The file containing the classifier model
     * @param featureTable The file containing the specification of how feature extraction should be done. See FeatureTable class.
     * @param classifierType The type of classifier to be used (should match the output of the classifier.key() method of the suitable classifier).
     * @param parseStyle The style of parsing to be used (what transitions are allowed, what data structures are used). Should match the key() method's output of the suitable style.
     */
    public Parser(File index,
                  File model,
                  File featureTable,
                  String classifierType,
                  String parseStyle,
                  String transitionSelectionMethod) throws IOException {

        // Load and set Index to read-only
        this.index = Index.load(index);
        this.index.setReadOnly(true);

        // Get type and load classifier
        classifier = Options.getClassifier(classifierType);
        classifier.load(model);

        // Load feature table
        this.featureTable = new FeatureTable(featureTable);

        // Ascertain parse style
        this.parseStyle = Options.getParserStyle(parseStyle);

        // Ascertain transition selection method
        this.selectionMethod = Options.getSelectionMethod(transitionSelectionMethod);
    }


    public void parseFile(File data, File output) throws IOException {
        parseFile(data, output, "id, form, pos, head, deprel", "", "confidence");
    }

    /**
     * Parse sentences in a file, and create an output file with the parsed sentences.
     * @param data File containing sentences to be parsed.
     * @param output File to output parsed sentences.
     * @param dataFormat Format of the input file. //TODO create an output format
     * @param classifierOptions Options passed to the classifier at prediction time
     * @param transitionSelectionMethod Method of deciding which transition to select
     */
    public void parseFile(File data, File output, String dataFormat, String classifierOptions, String transitionSelectionMethod) throws IOException {
        try (CoNLLWriter out = new CoNLLWriter(output);
             CoNLLReader in = new CoNLLReader(data, dataFormat)) {

            while(in.hasNext()) {
                out.write(parseSentence(in.next(), classifierOptions, transitionSelectionMethod));
            }
        }
    }

    public List<Token> parseSentence(List<Token> sentence) {
        return parseSentence(sentence, "", "confidence");
    }

    /**
     * Parse a sentence with a trained parser. For an easy way to construct a list of Tokens (i.e. have the ID assignment
     * and token creation done for you) see the Sentence class in the datastructures package).
     *
     * INTERPRETING OUTPUT:
     *
     * The parser's output can be found on each Token object after parsing, using the token.getHead() and token.getDeprel()
     * methods on each token. The getHead() will actually return the Token instance that is this tokens head. If you only
     * wanted the ID of the head, then you could call getID() on the head token, or getHeadID() on the original token.
     *
     * @param sentence The sentence to be parsed.
     * @param classifierOptions Options passed to the classifier at prediction time
     * @param transitionSelectionMethod The method by which transitions are selected given the classifier output.
     */
    public List<Token> parseSentence(List<Token> sentence, String classifierOptions, String transitionSelectionMethod) {
        // Get the suitable type of parser state for this style of parsing
        ParserState state = parseStyle.getNewParserState();

        // Initialise the state for this sentence (e.g. loading buffer with sentence, put ROOT on the stack)
        state.initialise(sentence);

        // Place to temporarily store unseen feature IDs (instead of just increasing the size of Index indefinitely, and introducing concurrency issues for future)
        StringIndexer temporaryFeatureIDs = new StringIndexer(index.getFeatureIndexer());

        // Keep finding next transition until state is terminal
        while (!state.isTerminal()) {

            // Get the feature vector of this state, only temporarily storing new feature IDs
            SparseBinaryVector v = getFeatureVector(state, temporaryFeatureIDs);

            // Place to store the decision scores for each transition (obtained from classifier during prediction)
            Int2DoubleMap decisionScores = new Int2DoubleOpenHashMap();

            // Get the prediction for the best transition (and fill the decision scores map)
            ParseStyle.Transition t = index.getTransition(classifier.predict(v, classifierOptions, decisionScores));

            // Apply the best transition that we can, given what the classifier suggests, and the decision scores it outputs
            selectionMethod.applyBestTransition(t, decisionScores, state, this);
        }

        // Any token whose head has been left unassigned by the parser, is automatically assigned to the root.
        for (Token token : sentence) {
            if (token.getHead() == null)
                token.setHead(state.getRootToken(), rootRelation);
        }

        // The tokens are modified in-place, but returned anyway for convenience.
        return sentence;
    }


/***********************************************************************************************************************
 *
 * Training functionality.
 *
 *   All of the methods in this section train a parser from scratch, save the parser's index and model files, and
 *   return the trained parser as their output.
 *
 ***********************************************************************************************************************/

    /**
     * Convenience method with sensible defaults.
     * Allows specification of just the training file.
     */
    public static Parser train(File trainingData) throws IOException {
        File index = new File(trainingData.getAbsolutePath()+"-index");
        File model = new File(trainingData.getAbsolutePath()+"-model");
        return train(trainingData,
                     "id, form, pos, head, deprel",
                     new FeatureTable(Resources.getResource("feature_table.txt").openStream()),
                     index,
                     model,
                     "-s 4 -c 0.1 -e 0.1 -B -1",
                     "linear-svm",
                     "arc-eager",
                     "confidence");
    }

    /**
     * Convenience method with sensible defaults.
     * Allows specification of just the training file and the file that specifies how feature extraction is to be done.
     */
    public static Parser train(File trainingData, File featureTable) throws IOException {

        File index = new File(trainingData.getAbsolutePath()+"-index");
        File model = new File(trainingData.getAbsolutePath()+"-model");
        return train(trainingData, "id, form, pos, head, deprel", new FeatureTable(featureTable), index, model,
                     "-s 4 -c 0.1 -e 0.1 -B -1",
                     "linear-svm",
                     "arc-eager",
                     "confidence");
    }

    /**
     * The key differences between the last 3 fully-parameterised training methods (including this) is the type of the
     * object being used to pass in the training data.
     *
     * Here you training from a file, whilst specifying how that file is laid out using the dataFormat parameter.
     * (see CoNLLReader class).
     *
     * @param trainingData The file from which to train.
     * @param dataFormat The formatting string passed to the CoNLL Reader (see CoNLLReader class).
     */
    public static Parser train(File trainingData,
                               String dataFormat,
                               FeatureTable featureTable,
                               File indexOutput,
                               File modelOutput,
                               String classifierOptions,
                               String classifierType,
                               String parseStyle,
                               String transitionSelectionMethod) throws IOException {

        try (CoNLLReader reader = new CoNLLReader(trainingData, dataFormat))  {
            return train(reader, featureTable, indexOutput, modelOutput, classifierOptions, classifierType, parseStyle, transitionSelectionMethod);
        }
    }

    /**
     * The key differences between the last 3 fully-parameterised training methods (including this) is the type of the
     * object being used to pass in the training data.
     *
     * Here you provide an Iterable object over Lists of tokens (where each list is a sentence), having read-in the
     * tokens yourself somehow.
     */
    public static Parser train(Iterable<List<Token>> trainingSentences,
                               FeatureTable featureTable,
                               File indexOutput,
                               File modelOutput,
                               String classifierOptions,
                               String classifierType,
                               String parseStyle,
                               String transitionSelectionMethod) throws IOException {
        return train(trainingSentences.iterator(), featureTable, indexOutput, modelOutput, classifierOptions, classifierType, parseStyle, transitionSelectionMethod);
    }

    /**
     * The key differences between the last 3 fully-parameterised training methods (including this) is the type of the
     * object being used to pass in the training data.
     *
     * Here you provide an Iterator over lists of tokens (where each list is a sentence), having read-in the
     * tokens yourself somehow.
     *
     * @param trainingSentences Collection of sentences, where each sentence is a properly formatted list of Tokens
     *                          as the Token.buildSentence() function would produce. I.e. with the first token being
     *                          the root, and the gold standard fields assigned. This can be done from CoNLL format
     *                          data. See other training method.
     * @param featureTable see FeatureTable class. Specification of the features to be extracted. (set to null for default)
     * @param indexOutput Where to save the index tracking feature IDs
     * @param modelOutput Where to save the classifier model
     * @param classifierOptions Options to pass to the classifier
     * @param classifierType The type of classifier to use (see classifiers package)
     * @param parseStyle The style of parsing to be performed (see parserstyles package)
     * @param transitionSelectionMethod How to select the best transition given classifier output (applicable in the finished parser,
     *                                  not actually used in training, but allows this method to return a full parser as output)
     */
    public static Parser train(Iterator<List<Token>> trainingSentences,
                               FeatureTable featureTable,
                               File indexOutput,
                               File modelOutput,
                               String classifierOptions,
                               String classifierType,
                               String parseStyle,
                               String transitionSelectionMethod) throws IOException {

        // Use private constructor (see below) to make empty untrained parser
        Parser parser = new Parser(classifierType,
                                   featureTable==null? new FeatureTable(Resources.getResource("feature_table.txt").openStream()) : featureTable,
                                   parseStyle,
                                   transitionSelectionMethod);

        // First convert the training data into feature vectors and put in temporary file

        // Create temp file. ensure it'll get deleted.
        File convertedTrainingData = File.createTempFile("featureVectors", null);
        convertedTrainingData.deleteOnExit();

        // Get a parser state appropriate for the requested style
        ParserState state = parser.parseStyle.getNewParserState();

        printStatus("Converting training data to transition-based feature vectors...");

        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(convertedTrainingData), "UTF-8"))){
            // For each training sentence, initialise a parser state, and use the training data to hand-hold the parser through the correct transitions
            while (trainingSentences.hasNext()) {
                List<Token> sentence = trainingSentences.next();
                // Initialise state with new sentence
                state.initialise(sentence);
                // Get hand-holding data from the training information
                ParseStyle.TrainingData trainingData = parser.parseStyle.getTrainingData(sentence);
                // Proceed as we would during a usual parse.
                while (!state.isTerminal()) {
                    // Get feature vector
                    SparseBinaryVector v = parser.getFeatureVector(state);
                    // Get the best transition according to our training data
                    ParseStyle.Transition t = parser.parseStyle.optimumTrainingTransition(state, trainingData);
                    // Write out the transition ID + the feature vector with sorted indices
                    bw.write(parser.index.getTransitionID(t, true)+ " " + v.toString() + "\n");
                }
            }
        }

        // Then pass responsibility to the classifier for the machine learning (the model gets saved in this process)
        parser.classifier.train(convertedTrainingData, modelOutput, classifierOptions);

        printStatus("Saving indexes...");

        // Save the feature/transition index
        parser.index.save(indexOutput);

        // Try a delete of the temporary file.
        if (!convertedTrainingData.delete()) System.err.println("WARNING: converted training temp file not deleted: " + convertedTrainingData.getAbsolutePath());

        printStatus("Done.");

        // Ensure that no further modifications can be made to the parser's index, this should allow concurrent calling of parse methods.
        parser.index.setReadOnly(true);

        // Return the trained parser
        return parser;
    }

    /**
     * Used only during training in order to make an incomplete parser that is then trained to completeness before its
     * index is set to read-only manually.
     */
    private Parser(String classifierType, FeatureTable featureTable, String parseStyle, String transitionSelectionMethod) throws IOException {
        this.index = new Index();
        this.classifier = Options.getClassifier(classifierType);
        this.featureTable = featureTable;
        this.parseStyle = Options.getParserStyle(parseStyle);
        this.selectionMethod = Options.getSelectionMethod(transitionSelectionMethod);
    }




/***********************************************************************************************************************
 *
 *  General functionality (including main method)
 *
 ***********************************************************************************************************************/

    /**
     * The ParseStyle is used for actually checking the feasibility of and performing transitions.
     */
    public ParseStyle getParseStyle(){ return parseStyle; }

    /**
     * The Index is used for indexing and de-indexing transitions and features.
     */
    public Index getIndex() { return index; }


    /**
     * Extract a feature vector from the current parse state. If the StringIndexer argument is not null, then this
     * indexer will be used for any features that aren't present in the parser's index field. If it is null, then
     * the new IDs will be permanently added to the main index.
     *
     * NOTE:
     */
    private SparseBinaryVector getFeatureVector(ParserState state, StringIndexer unseenFeatures){
        SparseBinaryVector v = new SparseBinaryVector();
        Collection<String> features = featureTable.extractFeatures(state);
        for (String feature : features) {
            if (unseenFeatures==null){
                v.put(index.getFeatureID(feature, true));
            } else {
                if (index.isFeatureIDPresent(feature)){
                    v.put(index.getFeatureID(feature,false));
                } else {
                    v.put(unseenFeatures.getIndex(feature));
                }
            }
        } return v;
    }

    private SparseBinaryVector getFeatureVector(ParserState state) {
        return getFeatureVector(state, null);
    }

    /**
     * Write a message to standard output with the date and time prefixed.
     */
    public static void printStatus(String message) {
        System.out.println("<" + new Date() + ">: " + message);
    }


    /**
     * Run tests from command line with default settings.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new RuntimeException("Specify the path to the training data.");

        // 1. Train cycle with all defaults, supply only the training data file path.
        if (args.length == 1)
            // trainingFile
            train(new File(args[0]));

        // 2. Predict cycle on a file. All defaults, supply input file and output file path.
        else if (args.length == 2)
            // fileToBeParsed outputFile
            new Parser().parseFile(new File(args[0]), new File(args[1]));

        else throw new RuntimeException("Unrecognised arguments");
    }
}
