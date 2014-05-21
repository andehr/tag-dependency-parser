package uk.ac.susx.tag.dependencyparser.classifiers;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import uk.ac.susx.tag.dependencyparser.Options;
import uk.ac.susx.tag.dependencyparser.datastructures.SparseBinaryVector;

import java.io.File;
import java.io.IOException;

/**
 * It is the classifier's job to taken a feature vector representing the current parser state, and produce
 * the ID of the transition that it recommends performing. It should also give a mapping from each
 * transition ID to the decision score for that ID.
 *
 * The classifier should be capable of training from a file containing feature vectors mapped to the appropriate
 * transition to take.
 *
 * NOTE: Subclasses should ensure that their only state is the actual model. And that during calls to the
 *       predict method, the model is not changed in any way. The classifiers need to be instantiated with
 *       no args.
 *
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
     * Provide an transition ID representing the best action to take given the feature vector provided.
     * If the decisionScores is null, do nothing else. Otherwise fill the map with a mapping from every known
     * action ID to a classification score of some design.
     *
     * DO NOT modify the model during this call if you can help it. If you do so, then you invalidate the ability to
     * call the Parser.parseSentence() concurrently. No parallelism for you.
     */
    public int predict(SparseBinaryVector featureVector, String options, Int2DoubleMap decisionScores);

    /**
     * Load up a trained model. The classifier should be ready to receive calls to predict() after this
     * function is done.
     */
    public void load(File model) throws IOException;
}
