package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.ArrayList;
import java.util.Map;

/**
 * Convenience class.
 *
 * Imagine you have some list of objects where each object has a FORM and a POS, and you want to turn this into a
 * list of tokens with appropriate IDs, so you can pass it to the parseSentence function of the parser.
 *
 *   Sentence sentence = new Sentence()
 *   for (Object o : listOfObjects)
 *       sentence.add(o.FORM, o.POS);
 *
 *   parser.parseSentence(sentence);
 *
 * You can also do this if the objects only have the FORM attribute, or if it's a complete map of attributes, using
 * the various add methods.
 *
 * NOTE: if you use the map of attributes, then "deprel" and "head" are reserved features that the Tokens know about.
 *       So if you have some gold standard tokens (that already have their deprel and head attributes), then you don't
 *       have to worry about the parser cheating. Because if the Token sees "deprel" or "head" in the attributes map
 *       it will remove them and store them in a separate place ear-marked for gold standard.
 *
 * Created by Andrew D. Robertson on 18/04/2014.
 */
public class Sentence extends ArrayList<Token> {

    public Sentence() {
        super();
    }

    public Sentence(Iterable<Map<String, String>> tokenAttributeList) {
        super();
        for (Map<String, String> attributes : tokenAttributeList)
            add(attributes);
    }

    public void add(String form) {
        super.add(new Token(size() + 1, form));
    }

    public void add(String form, String pos) {
        super.add(new Token(size() + 1, form, pos));
    }

    public void add(Map<String, String> attributes){
        super.add(new Token(size() + 1, attributes));
    }
}
