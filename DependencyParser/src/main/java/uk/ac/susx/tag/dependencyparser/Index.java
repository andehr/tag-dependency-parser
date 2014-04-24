package uk.ac.susx.tag.dependencyparser;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import uk.ac.susx.tag.dependencyparser.datastructures.StringIndexer;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;

import java.io.*;

/**
 * Tracks the mappings between:
 *
 *  - Transitions and their IDs
 *  - Features and their IDs
 *
 * Provides saving and loading functionality.
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class Index {

    private StringIndexer features;
    private StringIndexer transitions;

    public Index() {
        features = new StringIndexer();
        transitions = new StringIndexer();
    }

    public StringIndexer getFeatureIndexer() {return features;}
    public StringIndexer getTransitionIndexer() {return transitions;}

    /**
     * Get the ID for a particular transition. Use boolean for deciding whether to add new ID if one aint present.
     */
    public int getTransitionID(ParseStyle.Transition transition, boolean addIfNotPresent) {
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
     */
    public ParseStyle.Transition getTransition(int id) {
        return ParseStyle.Transition.interpretTransition(transitions.getValue(id));
    }

    /**
     * Get ID of a particular feature. Use switch to determine if new ID is added if one aint present.
     */
    public int getFeatureID(String feature, boolean addIfNotPresent) {
        return features.getIndex(feature, addIfNotPresent);
    }

    /**
     * Check if feature ID is present in the index.
     */
    public boolean isFeatureIDPresent(String feature) {
        return features.contains(feature);
    }

    /*
       JSON saving an loading for better flexibility.
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
        Index index = new Index();
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(jsonInput), "UTF-8"))){
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
}
