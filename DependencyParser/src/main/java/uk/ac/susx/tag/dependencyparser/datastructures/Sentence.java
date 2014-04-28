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
 * If you want to create the whole sentence in one go, then you should look to the static factory methods, which return
 * a full sentence. You've got 3 options:
 *
 *  1. Pass in an iterable of maps, where each map is the attribute map of a single token to be created.
 *  2. Have your tokens implement the PoSandFormBearing interface defined below, so that the Sentence knows that it can acquire PoS and Form from your tokens
 *  3. Have your tokens implement the AttributeMapBearing interface defined below, so that the Sentence knows that it can acquire an attribute map from your tokens
 *
 * Then call the relevant factory method.
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

    public static Sentence createFromAttributeList(Iterable<Map<String, String>> tokenAttributes) {
        Sentence s = new Sentence();
        for (Map<String, String> attMap : tokenAttributes)
            s.add(attMap);
        return s;
    }

    public static Sentence createFromPoSandFormBearingTokens(Iterable<PoSandFormBearing> tokens){
        Sentence s = new Sentence();
        for(PoSandFormBearing token : tokens)
            s.add(token.getForm(), token.getPos());
        return s;
    }

    public static Sentence createFromAttributeMapBearingTokens(Iterable<AttributeMapBearing> tokens){
        Sentence s = new Sentence();
        for(AttributeMapBearing token : tokens)
            s.add(token.getAtts());
        return s;
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


    public static interface PoSandFormBearing {

        public String getForm();

        public String getPos();
    }

    public static interface AttributeMapBearing {

        public Map<String, String> getAtts();
    }
}
