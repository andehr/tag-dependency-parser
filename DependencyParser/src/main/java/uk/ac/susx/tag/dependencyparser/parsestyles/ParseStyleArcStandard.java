package uk.ac.susx.tag.dependencyparser.parsestyles;

import uk.ac.susx.tag.dependencyparser.datastructures.Queue;
import uk.ac.susx.tag.dependencyparser.datastructures.Stack;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserStateOneStack;

/**
 * Implementation of Joakim Nivre's arc standard transition based dependency parsing.
 *
 * See ParseStyle class.
 *
 * Created by Andrew D. Robertson on 18/04/2014.
 */
public class ParseStyleArcStandard extends ParseStyle {

    public static final String leftArc = "leftArc";
    public static final String rightArc = "rightArc";
    public static final String shift = "shift";

    @Override
    public ParserState getNewParserState() {
        return new ParserStateOneStack();
    }

    @Override
    public boolean transition(ParserState s, Transition t, boolean perform) {
        ParserStateOneStack state = (ParserStateOneStack) s;
        switch (t.transitionName) {
            case leftArc:
                return leftArc(state, t.arcLabel, perform);
            case rightArc:
                return rightArc(state, t.arcLabel, perform);
            case shift:
                return shift(state, perform);
            default:
                throw new RuntimeException("Unrecognised transition.");
        }
    }

    @Override
    public Transition optimumTrainingTransition(ParserState s, TrainingData data) {
        if(s.isTerminal()) return null;
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

            // If relation stack-->buffer, then arc is rightward, and buffer item holds the deprel. But If we haven't finished the buffer item's deprels, then we have to shift for now
            } else if (data.hasRelation(stackTop, bufferTop) && data.hasAllDependantsAssigned(bufferTop)) {
                rightArc(state, bufferTop.getGoldDeprel(), true);
                return new Transition(rightArc, bufferTop.getGoldDeprel());

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
        return "arc-standard";
    }

    private boolean shift(ParserStateOneStack state, boolean perform) {
        if (state.getBuffer().isNotEmpty()) {
            if (perform) {
                state.getStack().push(state.getBuffer().pop());
            } return true;
        } return false;
    }

    private boolean leftArc(ParserStateOneStack state, String label, boolean perform){
        Queue<Token> buffer = state.getBuffer();
        Stack<Token> stack = state.getStack();
        if (stack.isNotEmpty() && buffer.isNotEmpty() && !stack.peek().isRoot()){
            if (perform){
                Token dependant = stack.pop();
                Token head = buffer.peek();
                dependant.setHead(head, label);
            } return true;
        } return false;
    }

    private boolean rightArc(ParserStateOneStack state, String label, boolean perform){
        Queue<Token> buffer = state.getBuffer();
        Stack<Token> stack = state.getStack();
        if (stack.isNotEmpty() && buffer.isNotEmpty()) {
            if (perform) {
                Token head = stack.pop();
                Token dependant = buffer.pop();
                buffer.addToFront(head);
                dependant.setHead(head, label);
            } return true;
        } return false;
    }
}
