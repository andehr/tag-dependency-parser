package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.ArrayList;
import java.util.List;
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

    public Sentence(int initialCapacity) { super(initialCapacity); }

    /*
     * Creation methods for building an entire sentence in one fell swoop. Inputs must be either direct
     * attribute maps to be made into tokens, or be objects that implement one of the relevant interfaces
     * at the bottom of this class.
     */

    public static Sentence createFromAttributeList(Iterable<Map<String, String>> tokenAttributes) {
        Sentence s = new Sentence();
        for (Map<String, String> attMap : tokenAttributes)
            s.add(attMap);
        return s;
    }

    public static Sentence createFromPoSandFormBearingTokens(Iterable<? extends PoSandFormBearing> tokens){
        Sentence s = new Sentence();
        for(PoSandFormBearing token : tokens)
            s.add(token);
        return s;
    }

    public static Sentence createFromAttributeMapBearingTokens(Iterable<? extends AttributeMapBearing> tokens){
        Sentence s = new Sentence();
        for(AttributeMapBearing token : tokens)
            s.add(token);
        return s;
    }

    /*
     * Add methods for building a sentence token per token
     */

    public void add(String form) {
        super.add(new Token(size() + 1, form));
    }

    public void add(String form, String pos) {
        super.add(new Token(size() + 1, form, pos));
    }

    public void add(Map<String, String> attributes){
        super.add(new Token(size() + 1, attributes));
    }

    public void add(PoSandFormBearing token){
        add(token.getForm(), token.getPos());
    }

    public void add(AttributeMapBearing token){
        add(token.getAtts());
    }


    /**
     * Make a copy of a sentence, where the copy does not retain any of the parsing decisions made by the parser
     * (though it will contain any gold standard info)
     */
    public static Sentence unparsedCopy(List<Token> original) {
        Sentence clone = new Sentence(original.size());
        for (Token token : original){
            clone.add(token.unparsedShallowCopy());
        }
        return clone;
    }

    /**
     * Make an exact duplicate of a sentence and any parse decisions assigned to that sentence by the parser.
     * For this to be possible, the current artificial root token must be passed in (you can acquire this by
     * calling parseState.getRootToken() on your current ParseState instance. You also need to pass in a new
     * root token (Can be acquired through Token.getNewRoot).
     *
     * Typically this function will be used by a ParserState implementing its copy() function, since a ParserState is
     * where the artificial root node is created and tracked.
     */
    public static Sentence parsedCopy(List<Token> original, Token originalRoot, Token newRoot){
        Sentence copy = unparsedCopy(original);
        Token.copyParsingDecisions(original, originalRoot, copy, newRoot);
        return copy;
    }

    /**
     * This interface tells the parser that subclasses have a PoS and Form with which to construct a Token object.
     * Implementing this interface allows the use of a creation method (found at top of class) to directly make a full
     * sentence.
     */
    public static interface PoSandFormBearing {

        public String getForm();
        public String getPos();
    }

    /**
     * This interface tells the parser that subclasses have a map of attributes that can be used to instantiate a
     * Token object.
     * Implementing this interface allows the use of a creation method (found at top of class) to directly make a full
     * sentence.
     */
    public static interface AttributeMapBearing {

        public Map<String, String> getAtts();
    }

    /**
     * This interface tells the parser that subclasses have a PoS and Form to do feature extraction with, and that
     * the parsing decisions can be directly annotated on to the subclass.
     */
    public static interface ParsableWithPoSAndForm extends PoSandFormBearing {

        public void setDeprel(String relationType);
        public void setHead(int headID);
    }

    /**
     * This interface tells the parser that subclasses have a map of attributes to select from, and that the
     * parsing decisions can be directly annotated on to the subclass.
     */
    public static interface ParsableWithAttributeMap extends AttributeMapBearing
    {
        public void setDeprel(String relationType);
        public void setHead(int headID);
    }
}
