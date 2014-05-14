package uk.ac.susx.tag.dependencyparser.parsestyles;

import uk.ac.susx.tag.dependencyparser.Options;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents a style of parsing.
 *
 * The style governs what underlying data structures are used to represent the parser state,
 * and it governs what transitions the parser can perform.
 *
 * Choice of machine learning classifier is an entirely orthogonal issue. See classifiers package.
 *
 * Transitions should be set out in the following way:
 *
 *  - Each transitions have a set of pre-conditions that must be met for the transition to take place
 *  - The transition should always return false if the transition is impossible, true otherwise
 *  - The transition should have a boolean "perform" parameter, which when true, the transition is also executed rather
 *    than just checked for feasibility.
 *
 * IMPORTANT NOTE: Subclasses should probably be stateless (or immutable state) for safety. And they need to be instantiated
 *                 with no args.
 *
 * Created by Andrew D. Robertson on 11/04/2014.
 */
public abstract class ParseStyle implements Options.Option {

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
     * Given the current state of the parser, and data gleaned from being initialised
     * with a training sentence, ascertain the optimum next transition.
     */
    public abstract Transition optimumTrainingTransition(ParserState s, TrainingData data);

    /**
     * Class representing the definition of a transition. A transition has a name and
     * optionally a arc label (the type of dependency relation assigned during the
     * transition).
     */
    public static class Transition {

        private static final Pattern splitter = Pattern.compile("\\|");

        public String transitionName; // Base type of transition
        public String arcLabel;       // Optionally, the label assigned to an arc during this transition

        public Transition(String transitionName, String arcLabel) {
            this.transitionName = transitionName;
            this.arcLabel = arcLabel;
        }

        public Transition(String transitionName){
            this.transitionName = transitionName;
            arcLabel = null;
        }

        /**
         * Return a representation of this Transition that can be indexed as a class for the classifier to
         * choose. "interpretTransition" should be able to reconstruct the full original transition from
         * this string.
         */
        public String toString() {
            return arcLabel==null? transitionName : transitionName+"|"+arcLabel;
        }

        /**
         * Given the output of a Transition.toString(), instantiate a Transition
         * instance from these details.
         */
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
     * WARNING: Ensure that the IDs of the tokens go in order from 1 to N consistently.
     *          Ensure that each token has its gold standard relations marked.
     *          Using the Sentence class with tokens that have "head" and "deprel" attributes
     *          ensures this for you.
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

        private TrainingData(List<Token> trainingSentence) {
            arcs = new HashMap<>();
            dependantCounts = new HashMap<>();
            Token root = Token.newRootToken(); // Because hashing of Tokens is done by ID only (and all root tokens created in this manner have ID==0), this root token will equate to any root token that the parser uses when calling hasRelation() or hasAllDependantsAssigned(), which saves us having to enforce that the root is acquired from the parse state here.

            if (Token.hasAllGoldRelations(trainingSentence)){
                for (int i = 0; i < trainingSentence.size(); i++) {
                    Token token = trainingSentence.get(i);
                    if (token.getID()!=i+1) throw new RuntimeException("Training sentence has inconsistent IDs");
                    int headID = token.getGoldHead();
                    Token head = headID == 0 ? root : trainingSentence.get(headID - 1);
                    arcs.put(token, head);
                    if (dependantCounts.containsKey(head))
                        dependantCounts.put(head, 1 + dependantCounts.get(head));
                    else dependantCounts.put(head, 1);
                }
            } else throw new RuntimeException("Training sentence doesn't have all gold relations annotated.");
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
            if (dependantCounts.containsKey(token))
                return dependantCounts.get(token) == token.numDependants();
            else return 0 == token.numDependants();
        }

    }
}
