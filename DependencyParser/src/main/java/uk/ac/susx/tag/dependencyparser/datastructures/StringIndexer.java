package uk.ac.susx.tag.dependencyparser.datastructures;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a two-way mapping between strings and IDs.
 *
 * The IDs start from 1 by default (avoids some nasty LIBLINEAR and LIBSVM limitations; indexing from 0 causes unexplained crashes).
 *
 * You can start the ID from where another StringIndexer left off by passing the other indexer to the constructor.
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class StringIndexer {

    private Object2IntOpenHashMap<String> stringIndex = new Object2IntOpenHashMap<>();
    private List<String> strings = new ArrayList<>();
    private int idStart = 1;

    public StringIndexer(){
        this(1);
    }

    /**
     * Start the IDs from a given number.
     */
    public StringIndexer(int idStart) {
        this.idStart = idStart;
    }

    /**
     * Start the IDs from where another indexer left off.
     */
    public StringIndexer(StringIndexer i){
        this.idStart = i.idStart + i.strings.size();
    }

    /**
     * Specify from which IDs to start, and initialise with a list of strings
     */
    public StringIndexer(int idStart, List<String> stringList){
        this.idStart = idStart;
        for (String s : stringList){
            getIndex(s, true);
        }
    }

    /**
     * Get the ID of a string. If addIfNotPresent is true, then if the string is not found, it is added to the index
     * and given an ID, otherwise an ID of -1 is returned. Alternatively, you can first check using the "contains"
     * method.
     */
    public int getIndex(String item, boolean addIfNotPresent){
        int index = -1;
        if (stringIndex.containsKey(item)){
            index = stringIndex.getInt(item);
        } else if (addIfNotPresent) {
            index = strings.size()+idStart;
            stringIndex.put(item, index);
            strings.add(item);
        }
        return index;
    }

    public int getIndex(String item) {
        return getIndex(item, true);
    }

    /**
     * Get the String value of an index.
     */
    public String getValue(int index){
        return strings.get(index-1);
    }

    /**
     * Check to see whether a String value is present in the mapping.
     */
    public boolean contains(String value) {
        return stringIndex.containsKey(value);
    }

    /**
     * Check to see whether an index is present in the mapping.
     */
    public boolean contains(int index) {
        return index > 0 && index <= strings.size();
    }

    public String toString() {
        return "IndexMap: " + stringIndex + "\n" + "ItemList: " + strings;
    }


    /*
       JSON provides a more flexible and reusable serialisation here.
     */

    public void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("idStart").value(idStart);
        writer.name("strings");
        writer.beginArray();
        for (String s : strings) {
            writer.value(s);
        } writer.endArray();
        writer.endObject();
    }

    public static StringIndexer readJson(JsonReader reader) throws IOException {
        int idStart = 1;
        List<String> strings = new ArrayList<>();
        reader.beginObject();
        while(reader.hasNext()) {
            String name = reader.nextName();
            switch(name) {
                case "idStart": idStart = reader.nextInt(); break;
                case "strings":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        strings.add(reader.nextString());
                    } reader.endArray();
            }
        } return new StringIndexer(idStart, strings);
    }
}
