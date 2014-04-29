package uk.ac.susx.tag.dependencyparser.classifiers;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import uk.ac.susx.tag.dependencyparser.Options;
import uk.ac.susx.tag.dependencyparser.datastructures.SparseBinaryVector;

import java.io.File;
import java.io.IOException;

/**
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public interface Classifier extends Options.Option {

    /**
     * Train the classifier on a file of vectors (in the same format as Libsvm + liblinear expects.
     * Save the model to the outputModel file. A classifier should be ready to receive calls to predict()
     * as soon as train() is done executing. Use the string options as you see fit.
     */
    public void train(File trainingSet, File outputModel, String options);

    /**
     * Provide an integer ID representing the best action to take given the feature vector provided.
     * If the decisionScores is null, do nothing else. Otherwise fill the map with a mapping from every known
     * action ID to a classification score of some design. See the Parser.applyBestTransition() method.
     */
    public int predict(SparseBinaryVector featureVector, String options, Int2DoubleMap decisionScores);

    /**
     * Load up a trained model. The classifier should be ready to receive calls to predict() after this
     * function is done.
     */
    public void load(File model) throws IOException;
}
