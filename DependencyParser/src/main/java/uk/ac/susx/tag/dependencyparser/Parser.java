package uk.ac.susx.tag.dependencyparser;

import com.google.common.collect.Ordering;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import uk.ac.susx.tag.dependencyparser.classifiers.Classifier;
import uk.ac.susx.tag.dependencyparser.datastructures.SparseBinaryVector;
import uk.ac.susx.tag.dependencyparser.datastructures.StringIndexer;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;

import java.io.*;
import java.util.*;

/**
 * The main top-level execution stage.
 *
 * Functions for training and parsing are here. See method comments.
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class Parser {

    // If the parser is done with a sentence, and left tokens without a head, then the ROOT token is assigned as their
    // head with the relation below. This is consistent with the Stanford dependency scheme.
    private static final String rootRelation = "root";

    /*
      The following are what constitute the information that the parser leans on during parsing and training.
      Efforts have been made such that these objects are either stateless, or are only READ FROM during PARSE-TIME
      (index and classifier are modified during train-time).  Hopefully this means that "parseSentence" can be called
      concurrently without problem.
     */
    private Index index;               // Features and Transitions are indexed, the index maintains a 2-way mapping for each.
    private Classifier classifier;     // This is the machine learning model used to predict the correct transition to make.
    private FeatureTable featureTable; // This represents the specification of what happens during feature extraction.
    private ParseStyle parseStyle;     // This represents how parsing is to be accomplish (i.e. what datastructure, what transitions)

/*
 * Prediction functionality
 */

    /**
     * Convenience method with sensible defaults.
     */
    public Parser (File index, File model) throws IOException {
        this.index = Index.load(index);
        classifier = Options.getClassifier("linear-svm");
        classifier.load(model);
        featureTable = new FeatureTable(Resources.getResource("feature_table.txt").openStream()); // The FeatureTable ensures that resource is closed.
        parseStyle = Options.getParserStyle("arc-eager");
    }

    /**
     * Construct a parser in this manner, when you one already trained, and wish to load its model and do some
     * prediction. Otherwise use the static train methods to train one first.
     */
    public Parser(File index,
                  File model,
                  File featureTable,
                  String classifierType,
                  String parseStyle) throws IOException {

        this.index = Index.load(index);
        classifier = Options.getClassifier(classifierType);
        classifier.load(model);
        this.featureTable = new FeatureTable(featureTable);
        this.parseStyle = Options.getParserStyle(parseStyle);
    }

    public List<Token> parseSentence(List<Token> sentence) {
        return parseSentence(sentence, "", "confidence");
    }

    /**
     * Parse a sentence with a trained parser.
     */
    public List<Token> parseSentence(List<Token> sentence, String classifierOptions, String transitionSelectionMethod) {
        // Get the suitable parser state for this style of parsing
        ParserState state = parseStyle.getNewParserState();

        // Initialise the state for this sentence (e.g. loading buffer with sentence, put ROOT on the stack)
        state.initialise(sentence);

        // Place to temporarily store unseen feature IDs (instead of just increasing the size of Index indefinitely, and introducing concurrency issues for future
        StringIndexer temporaryFeatureIDs = new StringIndexer(index.getFeatureIndexer());

        // Keep finding next transition until state is terminal
        while (!state.isTerminal()) {

            // Get the feature vector of this state, only temporarily storing new feature IDs
            SparseBinaryVector v = getFeatureVector(state, temporaryFeatureIDs);

            // Place to store the decision scores for each transition (obtained from classifier during prediction)
            Int2DoubleMap decisionScores = new Int2DoubleOpenHashMap();

            // Get the prediction for the best transition (and fill the decision scores map)
            ParseStyle.Transition t = index.getTransition(classifier.predict(v, classifierOptions, decisionScores));

            // If we can perform best transition, great, otherwise do the next best
            applyBestTransition(t, state, decisionScores, transitionSelectionMethod);
        }

        // Any token whose head has been left unassigned by the parser, is automatically assigned to the root.
        // Except the root of course, which we are expecting to have no head.
        for (int i = 1; i < sentence.size(); i++) {
            Token token = sentence.get(i);
            if (token.getHead() == null)
                token.setHead(sentence.get(0), rootRelation);
        }

        // The tokens are modified in place, but returned anyway for convenience.
        return sentence;
    }

    /**
     * Select and execute the chosen method of picking the best transition. If one wanted to come up with another way
     * of doing this, a function similar to "confidenceMethod" would be created, and the case for it would be added
     * to the switch in this method.
     */
    private void applyBestTransition(ParseStyle.Transition classifierRecommends, ParserState state, Int2DoubleMap decisionScores, String method) {
        switch (method) {
            case "confidence": confidenceMethod(classifierRecommends, state, decisionScores); break;
            default: throw new RuntimeException("unrecognised method for selecting  best transition.");
        }
    }

    /**
     * Procedure:
     *   1. Try the transition that the classifier recommends
     *   2. If that is impossible, find transition of each type that got the largest positive number as its score.
     *   3. Try the best of each type from best to worst. (A good parse style would have a transition that is always
     *      possible in a non-terminal state, so one of these should work. If it don't throw and exception).
     *
     *   This method is sensible for dealing with the output of the Liblinear SVM package, as far as I know. Each score
     *   represents how far the decision was from the margin, so a bigger score is better because the decision was
     *   easier.
     *
     *   Each score represents the outcome of a 2 way classification: the transition of interest versus all other transitions.
     *   Currently my thinking is that a positive number is a classification in favour of the transition of interest.
     *   But I need to do a test to confirm this. Test idea:
     *
     *   //TODO Do a prediction. The classifer outputs the prediction of the best transition, plus the decision scores. Have a look and see whether the decision score for the best transition is positive or negative (I may have already done this test in the past, but i can't remember...)
     */
    private void confidenceMethod(ParseStyle.Transition classifierRecommends, ParserState state, Int2DoubleMap decisionScores){
        // Try the transition that the classifier thinks is best.
        if (parseStyle.transition(state, classifierRecommends, true)) return;

        // If the transition is not possible, then select the next best based on the decision scores returned from the classifier
        Map<String, Integer> bestIDPerBaseTrans = new HashMap<>();
        Map<String, Double> bestScorePerBaseTrans = new HashMap<>();

        // First we find the best transition (the one the classifier was most sure about) for each DISTINCT type of transition
        // e.g. the best right arc, the best left arc,...
        for(Int2DoubleMap.Entry entry : decisionScores.int2DoubleEntrySet()) {
            ParseStyle.Transition t = index.getTransition(entry.getIntKey());
            if (!bestScorePerBaseTrans.containsKey(t.transitionName) || entry.getDoubleValue() > bestScorePerBaseTrans.get(t.transitionName)) {
                bestScorePerBaseTrans.put(t.transitionName, entry.getDoubleValue());
                bestIDPerBaseTrans.put(t.transitionName, entry.getIntKey());
            }
        }
        // Sort the set of distinct arcs by their score, and trying them best to worst to see which one is possible.
        for (Map.Entry<String, Double> entry : new ScoredTransitionTypeOrdering().reverse().immutableSortedCopy(bestScorePerBaseTrans.entrySet())){
            if(parseStyle.transition(state, index.getTransition(bestIDPerBaseTrans.get(entry.getKey())), true))
                return;
        }
        // If we've reached this point, then all types of transition have been tried and none were possible
        throw new RuntimeException("No transition was possible. This should be impossible. Perhaps the parse style was implemented incorrectly.");
    }

    /**
     * Ordering for sorting a list of entries by their values.
     */
    private static class ScoredTransitionTypeOrdering extends Ordering<Map.Entry<String,Double>> {
        @Override
        public int compare(Map.Entry<String, Double> entry1, Map.Entry<String, Double> entry2) {
            return entry1.getValue().compareTo(entry2.getValue());
        }
    }


/*
 * Training functionality.
 */
    public static void main(String[] args) throws IOException {
//        if (args.length < 1) throw new RuntimeException("Specify the path to the training data.");
//        train(new File(args[0]));

        String testTraining = "/Volumes/LocalDataHD/adr27/EclipseProjects/workspace/ParsingSuite/treebank3-npbrac-stanforddeps-conll-twittertags-training.txt";
        train(new File(testTraining));
    }

    /**
     * Convenience method with sensible defaults.
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
                     "arc-eager");
    }

    /**
     * Convenience method with sensible defaults.
     */
    public static Parser train(File trainingData, File featureTable) throws IOException {

        File index = new File(trainingData.getAbsolutePath()+"-index");
        File model = new File(trainingData.getAbsolutePath()+"-model");
        return train(trainingData, "id, form, pos, head, deprel", new FeatureTable(featureTable), index, model,
                     "-s 4 -c 0.1 -e 0.1 -B -1",
                     "linear-svm",
                     "arc-eager");
    }

    /**
     * Train from a file instead of java object. The file should be in a vaguely CoNLL format (see CoNLLReader class).
     * See other train method for descriptions of all parameters.
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
                               String parseStyle) throws IOException {

        try (CoNLLReader reader = new CoNLLReader(trainingData, dataFormat))  {
            return train(reader, featureTable, indexOutput, modelOutput, classifierOptions, classifierType, parseStyle);
        }
    }

    /**
     * Provide iterable format support for training sentences.
     */
    public static Parser train(Iterable<List<Token>> trainingSentences,
                               FeatureTable featureTable,
                               File indexOutput,
                               File modelOutput,
                               String classifierOptions,
                               String classifierType,
                               String parseStyle) throws IOException {
        return train(trainingSentences.iterator(), featureTable, indexOutput, modelOutput, classifierOptions, classifierType, parseStyle);
    }

    /**
     * Train a new parser. The relevant files are automatically saved, but the trained parser is also returned from this
     * function.
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
     */
    public static Parser train(Iterator<List<Token>> trainingSentences,
                               FeatureTable featureTable,
                               File indexOutput,
                               File modelOutput,
                               String classifierOptions,
                               String classifierType,
                               String parseStyle) throws IOException {

        // Use private constructor (see below) to make empty untrained parser
        Parser parser = new Parser(classifierType,
                                   featureTable==null? new FeatureTable(Resources.getResource("feature_table.txt").openStream()) : featureTable,
                                   parseStyle);

        // First convert the training data into feature vectors and put in temporary file

        // Create temp file. ensure it'll get deleted.
        File convertedTrainingData = File.createTempFile("featureVectors", null);
        convertedTrainingData.deleteOnExit();

        // Get a parser state appropriate for the requested style
        ParserState state = parser.parseStyle.getNewParserState();

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

        System.out.println("Starting the classifier training...");

        // Then pass responsibility to the classifier for the machine learning (the model gets saved in this process)
        parser.classifier.train(convertedTrainingData, modelOutput, classifierOptions);

        // Save the feature/transition index
        parser.index.save(indexOutput);

        // Try a delete of the temporary file.
        if (!convertedTrainingData.delete()) System.err.println("WARNING: converted training temp file not deleted: " + convertedTrainingData.getAbsolutePath());

        // Return the trained parser
        return parser;
    }

    private Parser(String classifierType, FeatureTable featureTable, String parseStyle) throws IOException {
        this.index = new Index();
        this.classifier = Options.getClassifier(classifierType);
        this.featureTable = featureTable;
        this.parseStyle = Options.getParserStyle(parseStyle);
    }


/*
 * General functionality
 */

    private SparseBinaryVector getFeatureVector(ParserState state) {
        return getFeatureVector(state, null);
    }

    /**
     * Extract a feature vector from the current parse state. If the StringIndexer argument is not null, then this
     * indexer will be used for any features that aren't present in the parser's index field. If it is null, then
     * the new IDs will be permanently added to the main index.
     *
     * As of 18/04/2014:
     *   during training the index field is allowed to grow, but during parsing the main index is used as read-only
     *   in an effort to go toward parsing in parallel.
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
}
