package uk.ac.susx.tag.dependencyparser.parserstates;

import uk.ac.susx.tag.dependencyparser.datastructures.IndexableQueue;
import uk.ac.susx.tag.dependencyparser.datastructures.Stack;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;

import java.util.List;

/**
 * This is your bog-standard transition based datastructure:
 *
 *  1 stack
 *  1 queue for the buffer
 *
 * The buffer is loaded with a new sentence, and items to be processed are loaded onto the stack.
 *
 * Created by Andrew D. Robertson on 11/04/2014.
 */
public class ParserStateOneStack implements ParserState {

    private Token root = Token.newRootToken();
    private IndexableQueue<Token> buffer = new IndexableQueue<>(); // To be loaded with a sentence, and processed in order
    private Stack<Token> stack = new Stack<>(root);  // To be loaded with items from the buffer to be processed


    @Override
    public void initialise(List<Token> sentence) {
        root = Token.newRootToken();
        stack = new Stack<>(root);
        buffer = new IndexableQueue<>(sentence);
    }

    public Stack<Token> getStack() { return stack; }
    public IndexableQueue<Token> getBuffer() { return buffer; }

    /**
     * Check if the parser is in a terminal configuration.
     */
    @Override
    public boolean isTerminal() {
        return buffer.isEmpty();
    }

    @Override
    public Token getRootToken() {
        return root;
    }

    /**
     * Expose the stack and buffer to the feature extraction process, via the keywords "stk" and "buf" respectively.
     * Usage:
     *
     *  - buf[0] refers to the next token on the buffer queue
     *  - stk[0] refers to the top item on the stack.
     *
     * WARNING: returns null when index is out of bounds, instead of throwing an exception.
     */
    @Override
    public Token getToken(String structureType, int address) {
        try {
            switch (structureType) {
                case "stk":  // "stack"
                    return stack.get(address);
                case "buf":  // "buffer"
                    return buffer.get(address);
                default:
                    throw new RuntimeException("Invalid data structure.");
            }
        } catch (IndexOutOfBoundsException e) {
            return null; // Token is not present at requested address, so return null.
        }
    }

}
