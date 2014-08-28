package uk.ac.susx.tag.dependencyparser;

/*
 * #%L
 * Parser.java - dependencyparser - CASM Consulting - 2,014
 * %%
 * Copyright (C) 2014 CASM Consulting
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import uk.ac.susx.tag.dependencyparser.classifiers.Classifier;
import uk.ac.susx.tag.dependencyparser.datastructures.*;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;
import uk.ac.susx.tag.dependencyparser.textmanipulation.CoNLLReader;
import uk.ac.susx.tag.dependencyparser.textmanipulation.CoNLLWriter;
import uk.ac.susx.tag.dependencyparser.transitionselectionmethods.SelectionMethod;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * The main top-level execution stage.
 *
 * Functions for training and parsing are here. See method comments. The class is divided into 3 selections:
 *  1. Parsing/Prediction functionality
 *  2. Training functionality
 *  3. General functionality
 *
 * See the Options class for information on options available to the parser and what they mean (classifiers, parse styles,
 * transition selection methods).
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
 * CONCURRENCY NOTE:
 *  "parseSentence" methods can be called concurrently.
 *  "parseFile" methods can be called concurrently, provided that they are operating on different input/output files.
 *
 *  All functions with "batch" in the name do some form of parallel parsing. They typically have more overhead (expect
 *  to need about 2GB of RAM), but have more throughput (the more processing cores that are available).
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class Parser {

    // If the parser is done with a sentence, and left tokens without a head, then the ROOT token is assigned as the
    // head with the relation below. This is consistent with the Stanford dependency scheme.
    private static final String rootRelation = "root";

    // The number of days to let a thread pool attempt to parse data before giving up (only applies to parallel
    // batch functions
    private static final int timeoutDays = 100;

    /*
      The following are what constitute the information that the parser leans on during parsing and training.
      Efforts have been made such that these objects are either stateless, or are only READ FROM during parse-time
      (index and classifier are modified during train-time).  Hopefully this means that "parseSentence" can be called
      concurrently without problems.
     */
    private Index index;               // Features and Transitions are indexed, the index maintains a 2-way mapping for each.
    private Classifier classifier;     // This is the machine learning model used to predict the correct transition to make.
    private FeatureTable featureTable; // This represents the specification of what happens during feature extraction.
    private ParseStyle parseStyle;     // This represents how parsing is to be accomplished (i.e. what datastructure, what transitions)
    private SelectionMethod selectionMethod;  // This represents how we use the classifier output to select the next transition

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

    public static Parser parserWithCMUPosAndStanfordDeprels() throws IOException {
        return new Parser("full_wsj_cmu_pos_stanford_dep");
    }

    public static Parser parserWithPennPosAndStanfordDeprels() throws IOException {
        return new Parser("full_wsj_penn_pos_stanford_dep");
    }

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

    public Parser(String parserName) throws IOException {
        this(parserName, Options.getSelectionMethod("confidence"));
    }

    public Parser(SelectionMethod method) throws IOException {
        this("full_wsj_cmu_pos_stanford_dep", method);
    }

    /**
     * Convenience method with same defaults as above except that the user can specify another index and model to be
     * found in the jar's resources. The index file is expected to be in the resources folder with name:
     * parserName+"-index", and the model with name: parserName+"-model".
     */
    public Parser(String parserName, SelectionMethod method) throws IOException {

        // Create a temporary file, into which we will stream the classifier model from JAR resources.
        File model = File.createTempFile("model", null);
        model.deleteOnExit();  // Ensure that temporary file is deleted once execution is completed.

        // Copy classifier model to temporary file
        try (BufferedOutputStream modelStream = new BufferedOutputStream(new FileOutputStream(model)) ){
            Resources.copy(Resources.getResource(parserName+"-model"), modelStream);
        }

        // Get appropriate classifier instance and use it to load the model
        classifier = Options.getClassifier("linear-svm");
        classifier.load(model);
        if (!model.delete()) System.err.print("WARNING: model temp file was not deleted: "+ model.getAbsolutePath());

        // Load and set index to read only
        this.index = Index.load(Resources.getResource(parserName + "-index").openStream());
        this.index.setReadOnly(true);

        // Load feature table
        featureTable = new FeatureTable(Resources.getResource("feature_table.txt").openStream());

        // Ascertain parse style
        parseStyle = Options.getParserStyle("arc-eager");

        // Ascertain transition selection method
        selectionMethod = method;
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

    /**
     * Change the way that the parser interprets the classifier's output in order to select a transition.
     *
     * For most use cases, you should just pass the correct selection method to the parser constructor upon creation.
     *
     * This is the only aspect of the parser's state that I could envision you might want to be able to change without
     * having to reload a whole new model (and therefore just using a constructor). Maybe you're comparing several
     * methods, and don't want the overhead of reading in the model more than once.
     */
    public void setTransitionSelectionMethod(String method){
        selectionMethod = Options.getSelectionMethod(method);
    }


    public void parseFile(File data, File output) throws IOException {
        parseFile(data, output, "id, form, pos, head, deprel", "");
    }

    /**
     * Parse sentences in a file, and create an output file with the parsed sentences.
     *
     * Only ever holds one sentence in memory at a time.
     *
     * Parsing is done in SERIAL.
     *
     * Expect ~1100 sentences parsed per second.
     *
     * See batchParseFile() for parallel parsing of file.
     *
     * @param data File containing sentences to be parsed.
     * @param output File to output parsed sentences.
     * @param dataFormat Format of the input and output file. See CoNLLReader and CoNLLWriter
     * @param classifierOptions Options passed to the classifier at prediction time
     */
    public void parseFile(File data, File output, String dataFormat, String classifierOptions) throws IOException {
        try (CoNLLWriter out = new CoNLLWriter(output, dataFormat);
             CoNLLReader in = new CoNLLReader(data, dataFormat)) {

            printStatus("Parsing file: " + data.getAbsolutePath());
            while(in.hasNext()) {
                out.write(parseSentence(in.next(), classifierOptions));
            }
            printStatus("\n  File parsed: " + data.getAbsolutePath() +
                        "\n  Output: " + output.getAbsolutePath());
        }
    }

    public void parseFileWithConfidence(File data, File output, File confidenceOut, String dataFormat, String classifierOptions) throws IOException {
        try (CoNLLWriter out = new CoNLLWriter(output, dataFormat);
             CoNLLReader in = new CoNLLReader(data, dataFormat);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(confidenceOut), "UTF-8"))) {

            printStatus("Parsing file: " + data.getAbsolutePath());
            while(in.hasNext()) {
                List<Double> confidences = new ArrayList<>();
                out.write(parseSentenceWithConfidence(in.next(), classifierOptions, confidences));
                bw.write(Joiner.on(",").join(confidences)); bw.write("\n");
            }
            printStatus("\n  File parsed: " + data.getAbsolutePath() +
                    "\n  Output: " + output.getAbsolutePath());
        }
    }

    /**
     * Parse sentence with sensible defaults.
     */
    public List<Token> parseSentence(List<Token> sentence) {
        return parseSentence(sentence, "");
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
     * @param classifierOptions Options passed to the classifier at prediction time (empty string is fine)
     */
    public List<Token> parseSentence(List<Token> sentence, String classifierOptions) {
        // Get the suitable type of parser state for this style of parsing
        ParserState state = parseStyle.getNewParserState();

        // Initialise the state for this sentence (e.g. loading buffer with sentence, put ROOT on the stack)
        state.initialise(sentence);

        // Place to temporarily store unseen feature IDs (instead of just increasing the size of Index indefinitely, and introducing concurrency issues for future)
        StringIndexer temporaryFeatureIDs = new StringIndexer(index.getFeatureIndexer());

        // Keep finding next transition until state is terminal
        while (!state.isTerminal()) {

            // Get the feature vector of this state, only temporarily storing new (previously unseen) feature IDs
            SparseBinaryVector v = getFeatureVector(state, temporaryFeatureIDs);

            // Place to store the decision scores for each transition (obtained from classifier during prediction)
            Int2DoubleMap decisionScores = new Int2DoubleOpenHashMap();

            // Get the prediction for the best transition (and fill the decision scores map)
            int recommendedTransition = classifier.predict(v, classifierOptions, decisionScores);

            // Apply the best transition that we can, given what the classifier suggests, and the decision scores it outputs
            selectionMethod.applyBestTransition(recommendedTransition, decisionScores, state, parseStyle, index);
        }

        // Any token whose head has been left unassigned by the parser, is automatically assigned to the root.
        for (Token token : sentence) {
            if (!token.hasHead())
                token.setHead(state.getRootToken(), rootRelation);
        }

        // The tokens are modified in-place, but returned anyway for convenience.
        return sentence;
    }

    public List<Token> parseSentenceWithConfidence(List<Token> sentence, String classifierOptions, List<Double> confidences){
        // Get the suitable type of parser state for this style of parsing
        ParserState state = parseStyle.getNewParserState();

        // Initialise the state for this sentence (e.g. loading buffer with sentence, put ROOT on the stack)
        state.initialise(sentence);

        // Place to temporarily store unseen feature IDs (instead of just increasing the size of Index indefinitely, and introducing concurrency issues for future)
        StringIndexer temporaryFeatureIDs = new StringIndexer(index.getFeatureIndexer());

        // Keep finding next transition until state is terminal
        while (!state.isTerminal()) {

            // Get the feature vector of this state, only temporarily storing new (previously unseen) feature IDs
            SparseBinaryVector v = getFeatureVector(state, temporaryFeatureIDs);

            // Place to store the decision scores for each transition (obtained from classifier during prediction)
            Int2DoubleMap decisionScores = new Int2DoubleOpenHashMap();

            // Get the prediction for the best transition (and fill the decision scores map)
            int recommendedTransition = classifier.predict(v, classifierOptions, decisionScores);

            // Apply the best transition that we can, given what the classifier suggests, and the decision scores it outputs
            confidences.add((double)selectionMethod.applyBestTransition(recommendedTransition, decisionScores, state, parseStyle, index));
        }

        // Any token whose head has been left unassigned by the parser, is automatically assigned to the root.
        for (Token token : sentence) {
            if (!token.hasHead())
                token.setHead(state.getRootToken(), rootRelation);
        }

        // The tokens are modified in-place, but returned anyway for convenience.
        return sentence;
    }

    /**
     * Any method that is of the form [batch]parse[dataStructure]BearingTokens is designed to take as input a sentence
     * which implements one of the corresponding interfaces in the Sentence. The aim being that those objects will be
     * directly annotated with the result of the parse (rather than returning Token objects from which you must extract
     * the parse yourself).
     *
     * See Sentence.ParsableWithPoSAndForm
     */
    public <E extends Sentence.ParsableWithPoSAndForm> List<E> parsePoSandFormBearingTokens(List<E> sentence, String classifierOptions) {
        Sentence parsed = Sentence.createFromPoSandFormBearingTokens(sentence);
        parseSentence(parsed, classifierOptions);
        for (int i = 0; i < parsed.size(); i++){
            Token parsedToken = parsed.get(i);
            E originalToken = sentence.get(i);
            originalToken.setDeprel(parsedToken.getDeprel());
            originalToken.setHead(parsedToken.getHeadID());
        }
        return sentence;
    }

    /**
     * Any method that is of the form [batch]parse[dataStructure]BearingTokens is designed to take as input a sentence
     * which implements one of the corresponding interfaces in the Sentence. The aim being that those objects will be
     * directly annotated with the result of the parse (rather than returning Token objects from which you must extract
     * the parse yourself).
     *
     * See Sentence.ParsableWithAttributeMap
     */
    public <E extends Sentence.ParsableWithAttributeMap> List<E> parseAttributeMapBearingTokens(List<E> sentence, String classifierOptions){
        Sentence parsed = Sentence.createFromAttributeMapBearingTokens(sentence);
        parseSentence(parsed, classifierOptions);
        for (int i = 0; i < parsed.size(); i++){
            Token parsedToken = parsed.get(i);
            E originalToken = sentence.get(i);
            originalToken.setDeprel(parsedToken.getDeprel());
            originalToken.setHead(parsedToken.getHeadID());
        }
        return sentence;
    }

    /**
     * Any method that is of the form [batch]parse[dataStructure]BearingTokens is designed to take as input a sentence
     * which implements one of the corresponding interfaces in the Sentence. The aim being that those objects will be
     * directly annotated with the result of the parse (rather than returning Token objects from which you must extract
     * the parse yourself).
     *
     * See Sentence.ParsableWithPoSAndForm
     */
    public <E extends Sentence.ParsableWithPoSAndForm> void batchParsePoSandFormBearingTokens(Iterable<List<E>> sentences, final String classifierOptions){
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (final List<E> sentence : sentences) {
            pool.execute(new Runnable() {
                public void run() {
                    parsePoSandFormBearingTokens(sentence, classifierOptions);
                }
            });
        } pool.shutdown();
        try {
            pool.awaitTermination(timeoutDays, TimeUnit.DAYS);
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    /**
     * Any method that is of the form [batch]parse[dataStructure]BearingTokens is designed to take as input a sentence
     * which implements one of the corresponding interfaces in the Sentence. The aim being that those objects will be
     * directly annotated with the result of the parse (rather than returning Token objects from which you must extract
     * the parse yourself).
     *
     * See Sentence.ParsableWithAttributeMap
     */
    public <E extends Sentence.ParsableWithAttributeMap> void batchParseAttributeMapBearingTokens(Iterable<List<E>> sentences, final String classifierOptions){
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (final List<E> sentence : sentences) {
            pool.execute(new Runnable() {
                public void run() {
                    parseAttributeMapBearingTokens(sentence, classifierOptions);
                }
            });
        } pool.shutdown();
        try {
            pool.awaitTermination(timeoutDays, TimeUnit.DAYS);
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    public void batchParseSentences(Iterable<List<Token>> sentences, final String classifierOptions){
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (final List<Token> sentence : sentences){
            pool.execute(new Runnable() {
                public void run() {
                    parseSentence(sentence, classifierOptions);
                }
            });
        } pool.shutdown();
        try {
            pool.awaitTermination(timeoutDays, TimeUnit.DAYS);
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    /**
     * Parse a file, putting the results in an output file.
     */
    public void batchParseFile(File data,
                               File output,
                               final String inputFormat,
                               final String outputFormat,
                               final String classifierOptions) throws IOException {

        final PriorityBlockingQueue<ConcurrencyUtils.ParsedSentence> processedData = new PriorityBlockingQueue<>();
        final BlockingQueue<ConcurrencyUtils.ParsedSentence> outputReadyData = new LinkedBlockingQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        int id = 0;

        // Consumes processed data, and produces sentences as they are ready to be written in the original order
        new Thread(new ConcurrencyUtils.SentenceConsumerProducerInOriginalOrder(outputReadyData, processedData)).start();

        // Consumes the ready-to-be-written data and writes it to file.
        Thread writer = new Thread(new ConcurrencyUtils.SentenceConsumerToFile(outputReadyData, output, outputFormat));
        writer.start();

        try (CoNLLReader reader = new CoNLLReader(data, inputFormat)){
            printStatus("Parsing file: " + data.getAbsolutePath());
            while(reader.hasNext()) {
                final int processID = id;
                final List<Token> sentence = reader.next();
                pool.execute(
                    new Runnable() {
                        public void run() {
                            processedData.put(
                               new ConcurrencyUtils.ParsedSentence(
                                    processID,
                                    parseSentence(sentence, classifierOptions)));
                        }
                    }
                );
                id++;
            }
        }
        // Graceful shutdown of pool allows submitted tasks to keep running
        pool.shutdown();
        try {
            // Block until tasks are done
            pool.awaitTermination(timeoutDays, TimeUnit.DAYS);
        } catch (InterruptedException e) { throw new RuntimeException(e); }

        // Empty sentence tells the consumers to shutdown.
        processedData.put(new ConcurrencyUtils.ParsedSentence(id, new ArrayList<Token>()));

        // Wait for the consumer that writes to file to finish executing before exiting.
        try {
            writer.join();
        } catch (InterruptedException e) { e.printStackTrace(); }

        printStatus("\n  File parsed: " + data.getAbsolutePath() +
                    "\n  Output: " + output.getAbsolutePath());
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
     * Allows specification of just the training file and format of the training data.
     */
    public static Parser train(File trainingData, String dataFormat) throws IOException {
        File index = new File(trainingData.getAbsolutePath()+"-index");
        File model = new File(trainingData.getAbsolutePath()+"-model");
        return train(trainingData,
                     dataFormat,
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
     * Allows specification of just the training file, the format of the training data,
     * and the file that specifies how feature extraction is to be done.
     */
    public static Parser train(File trainingData, String dataFormat, File featureTable) throws IOException {

        File index = new File(trainingData.getAbsolutePath()+"-index");
        File model = new File(trainingData.getAbsolutePath()+"-model");
        return train(trainingData, dataFormat, new FeatureTable(featureTable), index, model,
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

        // Try a delete of the temporary vector format file.
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
     * Extract a feature vector from the current parse state. If the StringIndexer argument is not null, then this
     * indexer will be used for any features that aren't present in the parser's index field. If it is null, then
     * the new IDs will be permanently added to the main index.
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
     * Convert one format to another.
     */
    public static void convert(File input, String inputFormat, File output, String outputFormat) throws IOException {
        try (CoNLLWriter outfile = new CoNLLWriter(output, outputFormat);
             CoNLLReader infile  = new CoNLLReader(input, inputFormat)){

            // See the "ghead" and "gdeprel" explanations in the CoNLLWriter comments for what this is doing.
            // We ensure that we're only copying over gold standard stuff, not parser output (since there is none).
            outfile.replaceParserOutputWithGoldRelations();

            while (infile.hasNext()){
                outfile.write(infile.next());
            }
        }
    }

    public void evaluate(File goldStandard) throws IOException, InterruptedException {
        evaluate(goldStandard, "perl", "id,form,ignore,ignore,pos,ignore,ignore,ignore,head,ignore,deprel,ignore,ignore,ignore", "");
    }

    public void evaluate(File goldStandard, String perlLocation) throws IOException, InterruptedException {
        evaluate(goldStandard, perlLocation, "id,form,ignore,ignore,pos,ignore,ignore,ignore,head,ignore,deprel,ignore,ignore,ignore", "");
    }

    public void evaluate(File goldStandard, String perlLocation, String dataFormat) throws IOException, InterruptedException {
        evaluate(goldStandard, perlLocation, dataFormat, "");
    }

    public void evaluate(File goldStandard, String perlLocation, String dataFormat, String classifyOptions) throws IOException, InterruptedException {
        // This is the format that the eval script expects.
        final String requiredFormat = "id,form,ignore,ignore,pos,ignore,ignore,ignore,head,ignore,deprel,ignore,ignore,ignore";

        File goldData = goldStandard;

        // If the data is not of this format, then create a temporary converted version
        if (!dataFormat.equals(requiredFormat)) {
            goldData = File.createTempFile("goldstandard", null);
            goldData.deleteOnExit();
            // Note that we're careful to copy over the "ghead" and "gdeprel" (the gold standard versions, because by default, the CoNLLWriter assumes by "head" and "deprel" in the format string, that you mean the output of the parser; but the parser hasn't done anything yet, we're just copying over the gold standard data
            convert(goldStandard, dataFormat, goldData, requiredFormat);
        }

        // Copy the eval script to a temporary file ready for execution
        File evalScript = File.createTempFile("evalScript", null);
        evalScript.deleteOnExit();  // Ensure that temporary file is deleted once execution is completed.
        try (BufferedOutputStream modelStream = new BufferedOutputStream(new FileOutputStream(evalScript)) ){
            Resources.copy(Resources.getResource("eval.pl"), modelStream);
        }

        // Create a temporary file to which we will write the parsed version of the data
        File parsedData = File.createTempFile("parsedData", null);
        parsedData.deleteOnExit();

        // Parse the gold standard, writing out the results to the temporary file
        parseFile(goldData, parsedData, requiredFormat, classifyOptions);

        // Build command for execute evaluation script
        ProcessBuilder pb = new ProcessBuilder(perlLocation, evalScript.getAbsolutePath(),
                                                "-g", goldData.getAbsolutePath(),
                                                "-s", parsedData.getAbsolutePath());

        // Redirect the output of the script to standard out of this parent process
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        // Execute and wait for the script to finish before cleaning up temporary files
        Process p = pb.start();
        p.waitFor();

        // Explicitly try to delete temporary files, reporting any failures.
        if (!dataFormat.equals(requiredFormat) && !goldData.delete()) System.err.print("WARNING: temporary gold standard file was not deleted: " + goldData.getAbsolutePath());
        if (!parsedData.delete()) System.err.print("WARNING: temporary parsed gold standard file was not deleted: " + parsedData.getAbsolutePath());
        if (!evalScript.delete()) System.err.print("WARNING: temporary eval script was not deleted: " + evalScript.getAbsolutePath());
    }

    /**
     * duh.
     */
    public static void printHelpfileAndOptions() throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Resources.getResource("helpfile.txt").openStream(), "UTF-8"))){
            String line;
            while ((line=br.readLine()) != null){
                System.out.println(line);
            }
        }
        // Print a summary of the options the parser finds at run time (should include any options you've defined to be in the appropriate package hierarchy. See Options class.
        Options.printAvailableOptionsSummary();
    }

    /**
     * Run tests from command line with default settings.
     *
     * There are 5 modes to choose from:
     *
     *   0. Output help-file and available options for parsing. (0 args)
     *   1. Train a new parser (1 arg)
     *   2. Parse a file and write out result (2 args)
     *   3. With a given parser model name, parse a file and write out the result (3 args)
     *   4. Convert a file from one format to another (4 args)
     *
     * See code for specification of args.
     */
    public static void main(String[] args) throws Exception {

        // 0. If no args (or too many), then print help-file.
        if (args.length < 1 || args.length > 4) {
            printHelpfileAndOptions();
        }

        /*
         * 1. Train cycle with all defaults, supply only the training data file path.
         *    Args: training file path
         */
        else if (args.length == 1){
            train(new File(args[0]));
        }

        /*
         * 2. Predict cycle on a file. All defaults, supply input file and output file path.
         *    Args: path of file to be parsed, path of output file
         */
        else if (args.length == 2) {
            new Parser().parseFile(new File(args[0]), new File(args[1]));
        }


        /*
         * Predict cycle on a file. The additional argument should be the name of a parser
         * model in the resources folder. So for example, if there is a parser model consisting
         * of the two files:
         *
         *  1. full_wsj_penn_pos_stanford_dep-model
         *  2. full_wsj_penn_pos_stanford_dep-index
         *
         * Then the corresponding parser model name (which should be passed as the first argument)
         * is: full_wsj_penn_pos_stanford_dep
         */
        else if (args.length == 3) {
            new Parser(args[0]).parseFile(new File(args[1]), new File(args[3]));
        }

        /*
         * 4. Convert the format of the sentences in a file
         *    Args: input file path, input file format, output file path, output file format
         */
        else if (args.length == 4) {
            convert(new File(args[0]), args[1], new File(args[2]), args[3]);
        }

        else throw new RuntimeException("Unrecognised arguments. See main method of Parser class in top-level package.");
    }


}
