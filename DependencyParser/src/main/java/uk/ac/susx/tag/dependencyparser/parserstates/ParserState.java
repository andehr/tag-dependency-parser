package uk.ac.susx.tag.dependencyparser.parserstates;

import uk.ac.susx.tag.dependencyparser.datastructures.Token;

import java.util.List;

/**
 * Represents the state of a parser.
 *
 * A parser can perform transitions on the state, including transitions which assign arcs to the dependency tree.
 *
 * A state also has the duty of exposing data structures to the feature extraction process (see getToken() ).
 *
 * Created by Andrew D Robertson on 11/04/2014.
 */
public abstract class ParserState {

    /**
     * Initialise the parser with a new sentence.
     */
    public abstract void initialise(List<Token> sentence);

    /**
     * return true if parser is in terminal state.
     */
    public abstract boolean isTerminal();

    /**
     * get the artificial root token associated with the current sentence
     */
    public abstract Token getRootToken();

    /**
     * Given the name of a structure (e.g. 'stk' for 'stack') and an address, interpret
     * this as the location of a token in the parser state, and return that token.
     *
     * This exposes the data structures to the feature extraction process. Return null if there is no token
     * as the location present.
     *
     * Convention is to use a three letter word for structureType, so that the feature table specification file looks
     * vaguely clean and aligned.
     */
    public abstract Token getToken(String structureType, int address);


    /**
     * Make a complete clone of the current state of the parser.
     * This may be useful when doing Beam Search (in order to allow the parser to freely explore different
     * possibilities without having to somehow undo transitions, a complete copy of the current parser
     * state is probably necessary. Then this new copy can be used to explore one set of transitions).
     *
     * Bear in mind that the state of the parser includes what arcs have already been assigned to the tree.
     * These arcs are stored on the input tokens themselves. So in order to provide a complete proper clone
     * of the parser state, the input sentence itself must be cloned.
     *
     * A parser state only starts with access to the full sentence (when it's loaded into the buffer), but
     * eventually it pops tokens off the stack and forgets about them when it's done with them.
     *
     * So in order to make a proper clone, the current input sentence being parsed needs to be passed to this method.
     *
     * The method will return an instance of "ClonedState", whose fields contain both a cloned sentence, and the
     * cloned parser state.
     *
     * Currently, it is impossible to modify the attributes of Token objects once they've been constructed. So when
     * Token objects are cloned, the clone maintains a reference to the attribute map of the original, instead of
     * having an entire new map for the attributes. If you ever modify the Token class such that it is now possible
     * to modify these attributes, you will need to create a unparsedDeepCopy() method on the Token, and ensure that during
     * this method call, that that method is called instead of unparsedShallowCopy(), and that the deep copy makes an
     * entirely new map.
     *
     * If you're implementing your own copy function in your own ParserState, then be sure to check out the
     * Sentence.parsedCopy() method, which can make a copy of a list of Tokens properly. This is half your work
     * done (see example copy() function in the ParserStateOneStack class).
     */
    public abstract ClonedState copy(List<Token> currentSentence);


    /**
     * A full clone of a state includes a clone of the full input sentence. This is represented with this
     * object. See copy().
     */
    public static class ClonedState {

        private List<Token> sentence;
        private ParserState parserState;

        public ClonedState(List<Token> sentence, ParserState parserState){
            this.sentence = sentence;
            this.parserState = parserState;
        }

        public ParserState getParserState() {
            return parserState;
        }

        public List<Token> getSentence() {
            return sentence;
        }
    }
}
