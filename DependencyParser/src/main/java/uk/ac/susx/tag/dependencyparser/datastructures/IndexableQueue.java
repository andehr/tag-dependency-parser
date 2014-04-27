package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * A queue which allows efficient additions to either end, and can still be indexed in constant time.
 *
 * The queue is implemented with an array that is twice (or expansionFactor) as big as the collection with which it
 * was initialised (or size 20 with no initialised collection).
 *
 * The actual values are stored in the middle of the array (the actual values are centred on the middle of the array,
 * they don't start in the middle):
 *
 *   [ - , - , E, E, E, E, - , - ]
 *
 * If through adding elements you need a bigger array, then using the expansionFactor, it will create a bigger array
 * and copy over the values.
 *
 * User: Andrew D. Robertson
 * Date: 25/04/2014
 * Time: 11:30
 */
public class IndexableQueue<E> {

    private Object[] elements;   // The array of length size()*expansionFactor. It contains the queue elements in the middle.
    private int startIndex;      // The index in *elements* where the elements actually start
    private int endIndex;        // The index in *elements* where the elements actually start
    private int expansionFactor; // The factor which influences the size of *elements*. A factor of 2 implies that the size of *elements* will be twice the number of actual elements.

    public IndexableQueue() {
        this(2);
    }

    public IndexableQueue(int expansionFactor) {
        this.expansionFactor = expansionFactor;
        newElementArray();
    }

    public IndexableQueue(Collection<? extends E> elements) {
        this(elements, 2);
    }

    public IndexableQueue(Collection<? extends E> elements, int expansionFactor){
        this.expansionFactor = expansionFactor;
        newElementArray(elements.toArray());
    }

    /**
     * Return the first element without removing it.
     */
    public E peek() {
        return get(0);
    }

    /**
     * Return the element specified by *index* (0 is the start of the queue), without removal.
     */
    public E get(int index) {
        return (E)elements[startIndex+index];
    }

    /**
     * Add an element to the end of the queue.
     * Note: if there is no more room at the end of the array, then a bigger array is created, and the old values
     *       are copied across.
     */
    public void push(E element) {
        if (endIndex >= elements.length){
            newElementArray(elements, startIndex, endIndex);
        }
        elements[endIndex] = element;
        endIndex++;
    }

    /**
     * Remove and return the first element of the queue.
     * Note: this does not shrink the array, see trim().
     */
    public E pop() {
        if (size() > 0) {
            E element = (E)elements[startIndex];
            elements[startIndex] = null;
            startIndex++;
            return element;
        } else throw new NoSuchElementException();
    }

    /**
     * Add an element to the front of the queue.
     * Note: if there is no more room at the start of the array, then a bigger array is created, and the old values
     *       are copied across.
     */
    public void addToFront(E element) {
        if (startIndex == 0) {
            newElementArray(elements, startIndex, endIndex);
        }
        elements[--startIndex] = element;
    }

    /**
     * This returns the number of elements that have been added to the queue.
     */
    public int size() {
        return endIndex - startIndex;
    }

    /**
     * Return true if there are no elements in the queue.
     */
    public boolean isEmpty(){
        return startIndex == endIndex;
    }

    public boolean isNotEmpty(){
        return !isEmpty();
    }

    /**
     * Use this if you allowed the array to grow to massive proportions, and then removed a tonne of elements,
     * if you want the underlying array to be reduced back down to expansionFactor*size of the remaining elements.
     */
    public void trim() {
        newElementArray(elements, startIndex, endIndex);
    }

    private void newElementArray() {
        this.elements = new Object[20];
        startIndex = 5;
        endIndex = 5;
    }

    private void newElementArray(Object[] elements) {
        newElementArray(elements, 0, elements.length);
    }

    private void newElementArray(Object[] elements, int start, int end){
        if (start < 0 || end > elements.length)
            throw new RuntimeException("Invalid start and end arguments.");

        this.elements = new Object[(end-start)*expansionFactor];
        startIndex = (this.elements.length / 2) - ((end-start)/2);
        endIndex = startIndex + (end-start);

        System.arraycopy(elements, start, this.elements, startIndex, end-start);
    }

}
