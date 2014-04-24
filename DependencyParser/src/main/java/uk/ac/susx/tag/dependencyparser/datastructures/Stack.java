package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Simple FILO data structure implemented using array list.
 *
 * Created by Andrew D. Robertson on 11/04/2014.
 */
public class Stack<E>  {

    private ArrayList<E> elements;

    public Stack(){
        elements = new ArrayList<>();
    }

    public Stack(E element) {
        this();
        push(element);
    }

    public Stack(Collection<? extends E> elements){
        this.elements = new ArrayList<>(elements);
    }

    public E peek() {
        return get(0);
    }

    /**
     * Get and remove last element (top of the stack)
     */
    public E pop() {
        E element = elements.get(elements.size()-1);
        elements.remove(elements.size()-1);
        return element;
    }

    /**
     * Add to the top of the stack (end of the list).
     */
    public void push(E element){
        elements.add(element);
    }

    /**
     * Get without removal item from the stack. Index 0 refers to the top of the stack (the last element), 1 is
     * penultimate, 2 is antepenultimate etc.
     */
    public E get(int index) {
        return elements.get(elements.size()-1-index);
    }

    public boolean isEmpty() {
        return elements.size() == 0;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public int size() { return elements.size(); }
}
