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
public interface ParserState {

    /**
     * Initialise the parser with a new sentence.
     */
    public void initialise(List<Token> sentence);

    /**
     * @return True if parser is in terminal state.
     */
    public boolean isTerminal();

    /**
     * Given the name of a structure (e.g. 'stk' for 'stack') and an address, interpret
     * this as the location of a token in the parser state, and return that token.
     *
     * This exposes the data structures to the feature extraction process. Return null if there is no token
     * as the location present.
     */
    public Token getToken(String structureType, int address);

}
