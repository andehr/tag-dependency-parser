package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Simple FIFO data structure, implemented using doubly linked list.
 *
 * Created by Andrew D. Robertson on 11/04/2014.
 */
public class Queue<E>  {

    private LinkedList<E> elements;

    public Queue() {
        elements = new LinkedList<>();
    }

    public Queue(Collection<? extends E> elements) {
        this.elements = new LinkedList<>(elements);
    }

    /**
     * Add an item to the end of the queue.
     */
    public void push(E element) {
        elements.add(element);
    }

    /**
     * Add item to front of queue
     */
    public void addToFront(E element) {
        elements.addFirst(element);
    }

    /**
     * Get the first added item, and remove it.
     */
    public E pop() {
        return elements.removeFirst();
    }

    public boolean isEmpty() {
        return elements.size() == 0;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public E get(int index) {
        return elements.get(index);
    }

    public E peek() {
        return get(0);
    }

    public int size() { return elements.size(); }
}
