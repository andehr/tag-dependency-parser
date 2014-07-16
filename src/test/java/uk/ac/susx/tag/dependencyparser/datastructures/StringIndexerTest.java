package uk.ac.susx.tag.dependencyparser.datastructures;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test that the StringIndexer functions as expected.
 *
 * User: Andrew D. Robertson
 * Date: 16/07/2014
 * Time: 14:15
 */
public class StringIndexerTest {
    /**
     * Check that indexes begin at specified int and count up properly. Check that the values can be acquired from these indices.
     */
    @Test
    public void indexing() {
        StringIndexer stringIndexer = new StringIndexer(5);

        for(int i = 5; i < 20; i++) {
            assertThat(stringIndexer.getIndex("test"+i), is(i));
        }

        for(int i = 5; i < 20; i++) {
            assertThat(stringIndexer.getValue(i), is("test"+i));
        }
    }

    /**
     * Test serialisation and deserialisation.
     */
    @Test
    public void serialisation() throws IOException, ClassNotFoundException {

        File tempfile = File.createTempFile("serialisationTest", null);
        tempfile.deleteOnExit();

        StringIndexer stringIndexer = new StringIndexer(5);

        stringIndexer.getIndex("test1");
        stringIndexer.getIndex("test2");
        stringIndexer.getIndex("test3");
        stringIndexer.getIndex("test4");
        stringIndexer.getIndex("test5");

        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(tempfile), "UTF-8"))){
            writer.beginObject();
            writer.name("index"); stringIndexer.writeJson(writer);
            writer.endObject();
        }

        StringIndexer deserialised = new StringIndexer();

        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(tempfile), "UTF-8"))){
            reader.beginObject();
            while(reader.hasNext()){
                String name = reader.nextName();
                switch(name) {
                    case "index": deserialised = StringIndexer.readJson(reader); break;
                }
            } reader.endObject();
        }


        if(!tempfile.delete()) throw new RuntimeException("Couldn't delete temp file: " + tempfile.getAbsolutePath());

        assertThat(deserialised.getIndex("test1", false), is(stringIndexer.getIndex("test1", false)));
        assertThat(deserialised.getIndex("test2", false), is(stringIndexer.getIndex("test2", false)));
        assertThat(deserialised.getIndex("test3", false), is(stringIndexer.getIndex("test3", false)));
        assertThat(deserialised.getIndex("test4", false), is(stringIndexer.getIndex("test4", false)));
        assertThat(deserialised.getIndex("test5", false), is(stringIndexer.getIndex("test5", false)));
    }

}
