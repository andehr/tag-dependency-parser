package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a token being parsed. Generally, the creation and management of
 * attributes is handled internally, so these tokens aren't actually passed
 * to the parser by the user (see Sentence class).
 *
 * Typically a token is created from a map of attributes. So, the user would
 * pass in the equivalent of, for example :
 *
 *  {
 *    "form": The,
 *    "pos" : D
 *  }
 *
 * These attributes can then be referenced in the feature table that specifies
 * which features the parser will extract.
 *
 * Typically, the user doesn't even create individual tokens like this. If you want to set up a list of Tokens
 * to be parsed, create a Sentence object and use its convenience methods to do the lifting for you (i.e.
 * instantiating tokens with sensible IDs). Or read them from file to Token using the CoNLLReader class.
 *
 * Notes:
 *   - Equality and hashing uses only the ID field. So is only valid when comparing tokens
 *     from the same sentence (since ID is unique within sentence only). This makes many things much easier.
 *
 * Created by Andrew D Robertson on 11/04/2014.
 */
public class Token {

    // ID within the sentence.
    private int id;

    // Attributes from which features may be drawn
    private Map<String, String> attributes;

    // Mid parse decisions (can be accessed during feature extraction using addressing functions, see FeatureTable class)
    private Token head = null;
    private String deprel = null;
    private Token leftmostChild = null;
    private Token rightmostChild = null;
    private int lDeps = 0;
    private int rDeps = 0;

    // Gold standard, if present (this is where "head" and "deprel" attributes are moved if the Token finds them during instantiation
    private int goldHead = 0;
    private String goldDeprel = null;


    /**
     * Convenience method for token with only the "form" attribute.
     */
    public Token(int id, String form) {
        this.id = id;
        attributes = new HashMap<>();
        attributes.put("form", form);
    }

    /**
     * Convenience method for token with only the "form" and "pos" attributes.
     */
    public Token(int id, String form, String pos) {
        this.id = id;
        attributes = new HashMap<>();
        attributes.put("form", form);
        attributes.put("pos", pos);
    }

    /**
     * @param id ID within the sentence (starts from 1), 0 is reserved for root token.
     * @param attributes Map of attributes that the tokens possesses (if you specify "head" and "deprel" attributes
     *                   these will be considered as the gold standard (so you don't have to worry that the parser might
     *                   have access to these features during parse time).
     */
    public Token(int id, Map<String, String> attributes ) {
        this.id = id;
        this.attributes = attributes;
        extractGoldRelationIfPresent();
    }

    /**
     * Get a Token representing the artificial root token.
     */
    public static Token newRootToken(){
        return new Token(0, "ROOT");
    }

    /**
     * Check whether a token is the root token (based on the token ID).
     */
    public boolean isRoot() {
        return id == 0;
    }

    /**
     * Return the ID of the token (valid within a sentence, 0 is the root token, 1 and up are the tokens in the sentence).
     */
    public int getID() { return id; }


    /**
     * Check if a token comes before this token in the sentence.
     */
    public boolean comesBefore(Token other) {
        return id < other.id;
    }

    /**
     * Check if a token comes after this token in the sentence.
     */
    public boolean comesAfter(Token other){
        return id > other.id;
    }

    /**
     * Get an attribute from the map of attributes that are accessible during feature extraction.
     * This does not include gold standard annotations or ID.
     */
    public String getAtt(String attribute) {
        return attributes.get(attribute);
    }
    public boolean hasAtt(String attribute) {
        return attributes.containsKey(attribute);
    }

    /*
     * Related to gold standard relations, if present.
     */

    public void setGoldRelation(int head, String deprel) {
        goldHead = head;
        goldDeprel = deprel;
    }
    public boolean hasGoldRelation() { return goldDeprel != null; }
    public int getGoldHead() { return goldHead; }
    public String getGoldDeprel() { return goldDeprel; }


    /*
     * Related to the parser's during-parse decisions
     */

    public int numLeftDependants()  { return lDeps; }
    public int numRightDependants() { return rDeps; }
    public int numDependants()  { return lDeps + rDeps; }

    public void setHead(Token head, String deprel) {
        this.head = head;
        this.deprel = deprel;
        head.setDependant(this);
    }

    public Token getHead() {
        return head;
    }
    public String getDeprel() { return deprel; }
    public Token getLeftmostChild() { return leftmostChild; }
    public Token getRightmostChild() {return rightmostChild; }

    /*
      Convenience method for getting the id of the head of this token. Usually needed by end-user, so returns 0 if
      the head is somehow null, given that that's what the parser would do if it had left it unparsed.
     */
    public int getHeadID() { return head == null? 0 : head.id; }




    /**
     * Return a copy of this token where all head/dependant relation info (except gold standard) is erased.
     *
     * This is unnecessary if you're trying to get a copy of the root token without any children attached. Just
     * use the newRootToken() method.
     *
     * This is part of the set of functions that ultimately allow a parser state to make a complete clone of itself.
     * A parser state includes the decisions that it's made, and those decisions are annotated directly onto the
     * input tokens. So in order to properly clone a parser state, the input sentence must be cloned too.
     *
     * See the copy() method of the ParserState abstract class for discussion.
     */
    public Token unparsedShallowCopy() {
        Token clone = new Token(id, attributes);
        clone.goldHead = goldHead;
        clone.goldDeprel = goldDeprel;
        return clone;
    }

    /**
     * Given the original sentence, and artificial root node (and therefore any parsing decisions that they have
     * been annotated with), plus a copy of the sentence, and a new artificial root node; copy the parsing decisions
     * made so far from the original sentence to the copy (including the new root). Recall that Tokens store
     * information about their head tokens AND their child tokens, which is why the root token has relation info.
     *
     * Probably no need to call this method yourself. Instead use the Sentence.parsedCopy() method.
     *
     * This is part of the set of functions that ultimately allow a parser state to make a complete clone of itself.
     * A parser state includes the decisions that it's made, and those decisions are annotated directly onto the
     * input tokens. So in order to properly clone a parser state, the input sentence must be cloned too.
     *
     * See the copy() method of the ParserState abstract class for discussion.
     */
    public static void copyParsingDecisions(List<Token> originalSentence, Token originalRoot, List<Token> copySentence, Token copyRoot){

        // Copy root children info
        copyRoot.leftmostChild = originalRoot.leftmostChild==null? null : copySentence.get(originalRoot.leftmostChild.id -1);
        copyRoot.rightmostChild = originalRoot.rightmostChild==null? null :copySentence.get(originalRoot.rightmostChild.id -1);
        copyRoot.lDeps = originalRoot.lDeps;
        copyRoot.rDeps = originalRoot.rDeps;

        for (int i = 0; i < copySentence.size(); i++) {
            Token copyToken = copySentence.get(i);
            Token originalToken = originalSentence.get(i);

            if (originalToken.getHead()!=null){
                int headID = originalToken.getHeadID();
                copyToken.head = (headID == 0)? copyRoot : copySentence.get(headID-1);
                copyToken.deprel = originalToken.deprel;

            } else { // Defensive, in case "copySentence" has been interfered with, and has relations where it shouldn't.
                copyToken.head = null;
                copyToken.deprel = null;
            }

            // Should never be root, so we don't bounds check the array for ID == 0
            copyToken.leftmostChild = originalToken.leftmostChild==null? null : copySentence.get(originalToken.leftmostChild.id -1);
            copyToken.rightmostChild = originalToken.rightmostChild==null? null : copySentence.get(originalToken.rightmostChild.id -1);

            copyToken.lDeps = originalToken.lDeps;
            copyToken.rDeps = originalToken.rDeps;
        }
    }

    /*
     * Static validation methods
     */
    public static boolean areIDsConsistent(List<Token> sentence) {
        for (int i = 0; i < sentence.size(); i++) {
            if (sentence.get(i).getID() != i) return false;
        } return true;
    }

    public static boolean hasAllGoldRelations(List<Token> sentence) {
        for (Token t : sentence) {
            if (!t.hasGoldRelation()) return false;
        } return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Token token = (Token) o;

        return id == token.id;
    }
    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return id + attributes.values().toString();
    }

    private void extractGoldRelationIfPresent() {
        if (hasAtt("head") && hasAtt("deprel")) {
            goldHead = Integer.parseInt(getAtt("head"));
            goldDeprel = getAtt("deprel");
        }
        attributes.remove("deprel");
        attributes.remove("head");
    }

    private void setDependant(Token dependant) {
        if (this.comesAfter(dependant))
            lDeps++;
        else rDeps++;

        if (leftmostChild == null || dependant.comesBefore(leftmostChild))
            leftmostChild = dependant;
        if (rightmostChild == null || dependant.comesAfter(rightmostChild))
            rightmostChild = dependant;
    }
}
