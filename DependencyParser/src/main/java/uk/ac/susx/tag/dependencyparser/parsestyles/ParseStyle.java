package uk.ac.susx.tag.dependencyparser.parsestyles;

import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents a style of parsing.
 *
 * The style governs what underlying data structures are used to represent the parser state,
 * and it governs what transitions the parser can perform.
 *
 * Choice of machine learning classifier is an entirely orthogonal issue.
 *
 * Created by Andrew D. Robertson on 11/04/2014.
 */
public abstract class ParseStyle {

    /**
     * Return the appropriate parser state.
     * You should return a concrete new instance of an implementation of ParserState available to you in the
     * parserstates package.
     */
    public abstract ParserState getNewParserState();

    /**
     * Return true if transition t is possible. If perform==true, then also perform
     * the transition.
     */
    public abstract boolean transition(ParserState s, Transition t, boolean perform);

    /**
     * Given the current state of the parser, and data gleaning from being initialised
     * with a training sentence, ascertain the optimum next transition.
     */
    public abstract Transition optimumTrainingTransition(ParserState s, TrainingData data);

    /**
     * Return a String representing the style of parsing. E.g. "arc-eager". This allows
     * the user to specify a style (or extent the package with a new style). See the
     * Options class.
     */
    public abstract String key();

    /**
     * Class representing the definition of a transition. A transition has a name and
     * optionally a arc label (the type of dependency relation assigned during the
     * transition).
     */
    public static class Transition {

        private static final Pattern splitter = Pattern.compile("|");

        public String transitionName;
        public String arcLabel;

        public Transition(String transitionName, String arcLabel) {
            this.transitionName = transitionName;
            this.arcLabel = arcLabel;
        }

        public Transition(String transitionName){
            this.transitionName = transitionName;
            arcLabel = null;
        }

        public String toString() {
            return arcLabel==null? transitionName : transitionName+"|"+arcLabel;
        }

        public static Transition interpretTransition(String representation){
            if (representation.contains("|")) {
                String[] transitionDefinition = splitter.split(representation);
                return new Transition(transitionDefinition[0], transitionDefinition[1]);
            } else return new Transition(representation);
        }
    }

    /**
     * Collect data necessary for acquiring training transitions from a gold standard sentence.
     * This data is passed during calls to optimumTrainingTransition().
     *
     * WARNING: Ensure that the training sentence contains the artificial root (see Token class),
     *          That each Token has its gold standard head and deprel, and that the IDs of the
     *          tokens go in order from 0 to N consistently (including ROOT as 0). Typically,
     *          these assurances are put in place automatically using a factory method for
     *          creating a sentence from a list of token attributes.
     */
    public TrainingData getTrainingData(List<Token> trainingSentence) {
        return new TrainingData(trainingSentence);
    }

    /**
     * Data necessary for acquiring training transition from a gold standard sentence.
     * Use getTrainingData() to acquire.
     */
    public static class TrainingData {

        // Map listing the proper heads of each token.
        private Map<Token, Token> arcs;  // Dependant --> Head (Because many --> one relation)

        // Record of how many dependants each token should have.
        private Map<Token, Integer> dependantCounts;

        public TrainingData(List<Token> trainingSentence) {
            if (Token.hasAllGoldRelations(trainingSentence)){
                for (Token token : trainingSentence) {
                    Token head = trainingSentence.get(token.getGoldHead());
                    arcs.put(token, head);
                    if (dependantCounts.containsKey(head))
                        dependantCounts.put(head, 1+dependantCounts.get(head));
                    else dependantCounts.put(head, 1);
                }
            } else throw new RuntimeException("Training sentence doesn't have artificial root and/or all gold relations annotated.");
        }

        /**
         * Return true if the training data confirms that there is a relation that
         * goes from head to dependant.
         */
        public boolean hasRelation(Token head, Token dependant) {
            return !dependant.isRoot() && arcs.get(dependant).equals(head);
        }

        /**
         * Return true if the training data confirms that the parser has assigned all of
         * the dependants that *token* expects.
         */
        public boolean hasAllDependantsAssigned(Token token) {
            return dependantCounts.get(token) == token.numDependants();
        }

    }
}
