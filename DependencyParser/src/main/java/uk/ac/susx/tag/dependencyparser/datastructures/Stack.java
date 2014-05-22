package uk.ac.susx.tag.dependencyparser.datastructures;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Simple FILO data structure implemented using array list.
 *
 * No clever things going on, just for convenience of having index 0 mean that last element instead of first.
 *
 * Created by Andrew D. Robertson on 11/04/2014.
 */
public class Stack<E> implements Iterable<E> {

    private ArrayList<E> elements;

    public Stack(){
        elements = new ArrayList<>();
    }

    public Stack(int initialCapacity){
        elements = new ArrayList<>(initialCapacity);
    }

    public Stack(E element) {
        this();
        push(element);
    }

    public E peek() {
        return get(0);
    }

    /**
     * Get and remove last element (top of the stack)
     * Θ(1)
     */
    public E pop() {
        E element = elements.get(elements.size()-1);
        elements.remove(elements.size()-1);
        return element;
    }

    /**
     * Add to the top of the stack (end of the list).
     * Amortized constant
     */
    public void push(E element){
        elements.add(element);
    }

    /**
     * Get without removal item from the stack. Index 0 refers to the top of the stack (the last element), 1 is
     * penultimate, 2 is antepenultimate etc.
     *
     * Θ(1)
     */
    public E get(int index) {
        return elements.get(elements.size()-1-index);
    }

    /**
     * Θ(1)
     */
    public boolean isEmpty() {
        return elements.size() == 0;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Θ(1)
     */
    public int size() { return elements.size(); }


    /**
     * Allow for use in a for-each loop.
     * Iterates in the order of the stack. So you get the elements in the order that you would if you repeatedly called
     * pop() (of course without the side-effect of removing the elements).
     */
    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>(){
            int index = 0;

            public boolean hasNext() {
                return index < size();
            }

            public E next() {
                if (hasNext())
                    return get(index++); // increment index AFTER acquiring current element
                else throw new NoSuchElementException();
            }

            public void remove() { throw new UnsupportedOperationException();  }
        };
    }

    @Override
    public String toString() {
        return "Top:[" + Joiner.on(", ").join(this) + "]";
    }
}
