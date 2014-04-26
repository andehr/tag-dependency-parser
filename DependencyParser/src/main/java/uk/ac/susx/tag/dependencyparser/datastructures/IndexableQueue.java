package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * A queue which allows efficient additions to either end, and can still be indexed in constant time.
 *
 * The queue is implemented with an array that is twice (expansionFactor) as big as the collection with which it was initialised
 * (or size 20 if no initialised collection).
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

    private Object[] elements;
    private int startIndex;
    private int endIndex;
    private int expansionFactor;

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


    public E peek() {
        return get(0);
    }

    public E get(int index) {
        return (E)elements[startIndex+index];
    }

    public void push(E element) {
        if (endIndex >= elements.length){
            newElementArray(elements, startIndex, endIndex);
        }
        elements[endIndex] = element;
        endIndex++;
    }

    public E pop() {
        if (size() > 0) {
            E element = (E)elements[startIndex];
            elements[startIndex] = null;
            startIndex++;
            return element;
        } else throw new NoSuchElementException();
    }

    public void addToFront(E element) {
        if (startIndex == 0) {
            newElementArray(elements, startIndex, endIndex);
        }
        elements[--startIndex] = element;
    }


    public int size() {
        return endIndex - startIndex;
    }

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
