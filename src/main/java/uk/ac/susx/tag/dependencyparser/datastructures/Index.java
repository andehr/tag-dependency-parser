package uk.ac.susx.tag.dependencyparser.datastructures;

/*
 * #%L
 * Index.java - dependencyparser - CASM Consulting - 2,014
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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;

import java.io.*;

/**
 * Tracks the mappings between:
 *
 *  - Transitions and their IDs
 *  - Features and their IDs
 *
 * Each mapping is represented using StringIndexer instances.
 *
 * This allows features and transitions to be reduced to integer values (which machine learning classifiers can
 * easily work with).
 *
 * Provides saving and loading functionality.
 *
 * Maintains a "readOnly" property, so it is possible to lock the mappings against modification, in order to
 * guarantee safety for concurrency for example.
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class Index {

    private StringIndexer features;
    private StringIndexer transitions;
    private transient boolean readOnly;  // The read only property is not remembered during saving/loading

    public Index() {
        features = new StringIndexer();
        transitions = new StringIndexer();
        readOnly = false;
    }

    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly;}
    public boolean isReadOnly() { return readOnly; }

    public StringIndexer getFeatureIndexer() {return features;}
    public StringIndexer getTransitionIndexer() {return transitions;}

    /**
     * Get the ID for a particular transition. If addIfNotPresent is true, then if the transition is not in the index,
     * then it will be added and a new ID will be generated and returned (unless the Index is set to readOnly, in which
     * case an error will be thrown).
     */
    public int getTransitionID(ParseStyle.Transition transition, boolean addIfNotPresent) {
        if (readOnly && addIfNotPresent) throw new RuntimeException("addIfNotPresent is set to true on an Index marked as read-only. The index is read-only during parse-time to enable concurrency.");
        return transitions.getIndex(transition.toString(), addIfNotPresent);
    }

    /**
     * Check if the ID of a transition is present in the index.
     */
    public boolean isTransitionIDPresent(ParseStyle.Transition transition){
        return transitions.contains(transition.toString());
    }

    /**
     * Given the ID of a transition get the appropriate transition.
     * Throws index out of range error if an inappropriate ID is specified.
     */
    public ParseStyle.Transition getTransition(int id) {
        return ParseStyle.Transition.interpretTransition(transitions.getValue(id));
    }

    /**
     * Get ID of a particular feature. If addIfNotPresent and there is no ID for that feature, then the feature is
     * added to the index, given a new ID and the ID is returned (unless the index is read only, in which case
     * an error is thrown).
     */
    public int getFeatureID(String feature, boolean addIfNotPresent) {
        if (readOnly && addIfNotPresent) throw new RuntimeException("addIfNotPresent is set to true on an Index marked as read-only. The index is read-only during parse-time to enable concurrency.");
        return features.getIndex(feature, addIfNotPresent);
    }

    /**
     * Check if feature ID is present in the index.
     */
    public boolean isFeatureIDPresent(String feature) {
        return features.contains(feature);
    }

    /*
     *  JSON saving an loading for better flexibility.
     *
     *  The StringIndexers know how to JSON-ise themselves.
     */

    public void save(File jsonOutput) throws IOException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonOutput), "UTF-8"))){
            writer.beginObject();
                writer.name("featureIndex"); features.writeJson(writer);
                writer.name("transitionIndex"); transitions.writeJson(writer);
            writer.endObject();
        }
    }

    public static Index load(File jsonInput) throws IOException {
        return load(new FileInputStream(jsonInput));
    }

    public static Index load(InputStream jsonInput) throws IOException {
        Index index = new Index();
        try (JsonReader reader = new JsonReader(new InputStreamReader(jsonInput, "UTF-8"))){
            reader.beginObject();
            while(reader.hasNext()){
                String name = reader.nextName();
                switch(name) {
                    case "featureIndex": index.features = StringIndexer.readJson(reader); break;
                    case "transitionIndex": index.transitions = StringIndexer.readJson(reader); break;
                }
            } reader.endObject();
        } return index;
    }

    /**
     * For curious parties. You can convert the JSON file of a saved index, into a
     * pretty-printed version. This allows you to see the features that are extracted
     * and all the possible transitions a parser with this index can make.
     */
    public static void prettyPrint(File jsonInput, File jsonOutput) throws IOException {
        Index index = load(jsonInput);
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonOutput), "UTF-8"))){
            writer.setIndent("  ");
            writer.beginObject();
                writer.name("featureIndex"); index.features.writeJson(writer);
                writer.name("transitionIndex"); index.transitions.writeJson(writer);
            writer.endObject();
        }
    }
}
