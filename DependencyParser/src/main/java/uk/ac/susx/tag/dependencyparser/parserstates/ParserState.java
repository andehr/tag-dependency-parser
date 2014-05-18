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
 * TODO:
 *    Make a new method "copy()" which should create a new state that is an exact copy of the current state.
 *    This will probably be necessary for beam search. Since a parser state doesn't necessarily store the entire sentence
 *    (since Tokens are pruned away) the sentence will need to be passed to the copy() method). The parser state should
 *    make a complete
 *
 *    - This needs to be the responsibility of the parser state, because it is the only object with access to the root
 *      token of the new state.
 *
 *    - The input list of Tokens is currently directly annotated with the arc decisions of the parser. This means that
 *      the partial parse which is part of the parser state actually includes the full sentence, even tho the state
 *      may not include the full set of tokens at any one time.
 *
 *    - So we kinda need to return a cloned version of the input sentence AND a cloned version of the parser state.
 *
 *    - The solution perhaps is to have a method:
 *
 *      public abstract ClonedState copy(List<Token> sentence);
 *
 *      Which is overridden by a ParserState subclass, which takes the input sentence, makes use of the
 *      Sentence.unparsedCopy() method to make a full copy without the decision arcs.
 *
 *      Then the ParserState creates a new empty version of itself, adding the relevant tokens to each data-structure
 *      (including making a new root token).
 *
 *      Then maps out what dependencies have already been assigned in the original version. Then copies those into the
 *      new version.
 *
 *      In the returned ClonedState object, there is a full parsed clone of the original sentence, and a new cloned
 *      parser state.
 *
 *    - Currently the tokens when cloned, do a shallow copy of their attributes. Since they'll
 *      never change mid-parse. Should probably make sure this is the case.
 *
 *    - If this gets too complicated, we may have to store arc decisions separate from the tokens themselves. This would
 *      involve massive re-writes...
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
     */
    public abstract Token getToken(String structureType, int address);

    public static class ClonedState {

        private List<Token> sentence;
        private ParserState parserState;

    }
}
