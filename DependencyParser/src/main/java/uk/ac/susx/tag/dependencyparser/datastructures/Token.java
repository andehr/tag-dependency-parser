package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a token being parsed. Generally, the creation and management of
 * attributes is handled internally, so these tokens aren't actually passed
 * to the parser by the user.
 *
 * Typically a token is created from a map of attributes. So, the user would
 * pass in the equivalent of, for example :
 *
 *  {
 *    "form": The,
 *    "pos" : D
 *  }
 *
 *  These attributes can then be referenced in the feature table that specifies
 *  which features the parser will extract.
 *
 *  Typically, the user doesn't even create individual tokens like this. If you want to set up a list of Tokens
 *  to be parsed, create a Sentence object and use it's convenience methods to do the lifting for you (i.e.
 *  instantiating tokens with sensible IDs). Or read them from file to Token using the CoNLLReader class.
 *
 * Notes:
 *   - Equality and hashing uses only the ID field. So is only valid when comparing tokens
 *     from the same sentence (since ID is unique within sentence only).
 *
 * Created by Andrew D Robertson on 11/04/2014.
 */
public class Token {

    // ID within the sentence.
    private int id;

    // Attributes from which features may be drawn
    private Map<String, String> attributes;

    // Mid parse decisions:
    private Token head = null;
    private String deprel = null;
    private Token leftmostChild = null;
    private Token rightmostChild = null;
    private int lDeps = 0;
    private int rDeps = 0;

    // Gold standard, if present.
    private int goldHead = 0;
    private String goldDeprel = null;

    public Token(int id, String form) {
        this.id = id;
        attributes = new HashMap<>();
        attributes.put("form", form);
    }

    public Token(int id, String form, String pos) {
        this.id = id;
        attributes = new HashMap<>();
        attributes.put("form", form);
        attributes.put("pos", pos);
    }

    public Token(int id, Map<String, String> attributes ) {
        this.id = id;
        this.attributes = attributes;
        extractGoldRelationIfPresent();
    }

    public static Token newRootToken(){
        return new Token(0, "ROOT");
    }

    public boolean isRoot() {
        return id == 0;
    }

    public int getID() { return id; }

    public boolean comesBefore(Token other) {
        return id < other.id;
    }
    public boolean comesAfter(Token other){
        return id > other.id;
    }

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
