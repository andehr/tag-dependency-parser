package uk.ac.susx.tag.dependencyparser.parsestyles;

import uk.ac.susx.tag.dependencyparser.datastructures.Queue;
import uk.ac.susx.tag.dependencyparser.datastructures.Stack;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserStateOneStack;

/**
 * Implementation of Joakim Nivre's arc eager transition based dependency parsing.
 *
 * See ParseStyle class.
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class ParseStyleArcEager extends ParseStyle {

    public static final String leftArc = "leftArc";
    public static final String rightArc = "rightArc";
    public static final String shift = "shift";
    public static final String reduce = "reduce";

    @Override
    public ParserState getNewParserState() {
        return new ParserStateOneStack();
    }

    @Override
    public boolean transition(ParserState s, Transition t, boolean perform) {
        ParserStateOneStack state = (ParserStateOneStack)s;
        switch (t.transitionName) {
            case leftArc:
                return leftArc(state, t.arcLabel, perform);
            case rightArc:
                return rightArc(state, t.arcLabel, perform);
            case shift:
                return shift(state, perform);
            case reduce:
                return reduce(state, perform);
            default:
                throw new RuntimeException("Unrecognised transition.");
        }
    }

    @Override
    public Transition optimumTrainingTransition(ParserState s, TrainingData data) {
        if (s.isTerminal()) return null;
        ParserStateOneStack state = (ParserStateOneStack)s;
        Stack<Token> stack = state.getStack();
        Queue<Token> buffer = state.getBuffer();
        if (stack.isNotEmpty()) {  // If there is nothing on the stack, then all we can do is shift.
            Token stackTop = stack.peek();    // Get the next item on the stack
            Token bufferTop = buffer.peek();  // Get the next item on the buffer

            // If relation buffer-->stack, then arc is leftward, and stack item holds the deprel
            if (data.hasRelation(bufferTop, stackTop)) {
                leftArc(state, stackTop.getGoldDeprel(), true);
                return new Transition(leftArc, stackTop.getGoldDeprel());

            // If relation stack-->buffer, then arc is rightward, and buffer item holds the deprel
            } else if (data.hasRelation(stackTop, bufferTop)) {
                rightArc(state, bufferTop.getGoldDeprel(), true);
                return new Transition(rightArc, bufferTop.getGoldDeprel());

            // If there's no relation, and all dependants have been assigned to top stack item, and it has a head assigned; reduce
            } else if (data.hasAllDependantsAssigned(stackTop) && stackTop.getHead()!=null) {
                reduce(state, true);
                return new Transition(reduce);

            // Otherwise, shift next buffer item onto stack
            } else {
                shift(state, true);
                return new Transition(shift);
            }
        } else {
            shift(state, true);
            return new Transition(shift);
        }
    }

    @Override
    public String key() {
        return "arc-eager";
    }

    private boolean shift(ParserStateOneStack state, boolean perform) {
        if (state.getBuffer().isNotEmpty()) {
            if (perform) {
                state.getStack().push(state.getBuffer().pop());
            } return true;
        } return false;
    }

    private boolean reduce(ParserStateOneStack state, boolean perform){
        Stack<Token> stack = state.getStack();
        if(stack.isNotEmpty() && stack.peek().getHead() != null){
            if(perform) {
                stack.pop();
            } return true;
        } return false;
    }

    private boolean rightArc(ParserStateOneStack state, String label, boolean perform) {
        Queue<Token> buffer = state.getBuffer();
        Stack<Token> stack = state.getStack();
        if (stack.isNotEmpty() && buffer.isNotEmpty()) {
            if (perform) {
                Token head = stack.peek();
                Token dependant = buffer.pop();
                stack.push(dependant);
                dependant.setHead(head, label);
            } return true;
        } return false;
    }

    private boolean leftArc(ParserStateOneStack state, String label, boolean perform){
        Queue<Token> buffer = state.getBuffer();
        Stack<Token> stack = state.getStack();
        if (stack.isNotEmpty() && buffer.isNotEmpty() && !stack.peek().isRoot() && stack.peek().getHead()!=null){
            if (perform) {
                Token dependant = stack.pop();
                Token head = buffer.peek();
                dependant.setHead(head, label);
            } return true;
        } return false;
    }
}
