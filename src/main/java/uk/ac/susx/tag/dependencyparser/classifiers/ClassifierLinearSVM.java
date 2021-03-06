package uk.ac.susx.tag.dependencyparser.classifiers;

/*
 * #%L
 * ClassifierLinearSVM.java - dependencyparser - CASM Consulting - 2,014
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

import de.bwaldvogel.liblinear.*;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import uk.ac.susx.tag.dependencyparser.Parser;
import uk.ac.susx.tag.dependencyparser.datastructures.SparseBinaryVector;

import java.io.File;
import java.io.IOException;

/**
 * This is an interface to the java implementation of LIBLINEAR. See Classifier class.
 *
 * Created by Andrew D. Robertson on 14/04/2014.
 */
public class ClassifierLinearSVM implements Classifier {

    private Model model = null; // Actual liblinear model

    @Override
    public void train(File trainingData, File outputModel, String options) {
        TrainOpenAccess trainer = new TrainOpenAccess();  // Class based on Liblinear "Train" class, but opens up some package-private methods.
        trainer.parseCommandline(options, trainingData, outputModel);  // Used for parsing out the options.
        try {
            Parser.printStatus("Reading in vectors to the classifier...");
            trainer.readProblem(trainingData.getAbsolutePath());

            Parser.printStatus("Performing training...");
            model = Linear.train(trainer.getProblem(), trainer.getParameter());

            Parser.printStatus("Saving SVM model...");
            save(outputModel);
        } catch (IOException | InvalidInputDataException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem with loading training data.", e);
        }
    }

    /**
     * Does not alter the model's state, as requested by interface.
     */
    @Override
    public int predict(SparseBinaryVector featureVector, String options, Int2DoubleMap decisionScores) {
        if (model == null) throw new RuntimeException("No model loaded or trained.");
        double[] decisionValues = new double[model.getNrClass()];
        int bestClass = (int)Linear.predictValues(model, vector2FeatureArray(featureVector), decisionValues);
        // If user wants the decision scores for each class
        if (decisionScores != null) {
            // Produce a mapping from each class ID to its decision score
            int[] labelOrder = model.getLabels();
            for (int i = 0; i < decisionValues.length; i++){
                decisionScores.put(labelOrder[i], decisionValues[i]);
            }
        } return bestClass;
    }

    /**
     * Convert our feature vector representation to the representation demanded by liblinear
     */
    private FeatureNode[] vector2FeatureArray(SparseBinaryVector v) {
        FeatureNode[] features = new FeatureNode[v.numNonZero()];
        int featureNum = 0;
        for (int index : v.indices()) {
            features[featureNum++] = new FeatureNode(index, v.get(index));
        } return features;
    }

    public void save(File output) throws IOException {
        Linear.saveModel(output, model);
    }

    @Override
    public void load(File input) throws IOException {
        model = Linear.loadModel(input);
    }

    @Override
    public String key() {
        return "linear-svm";
    }
}
