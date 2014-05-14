package uk.ac.susx.tag.dependencyparser.datastructures;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * A queue which allows efficient additions to either end (amortized constant), and can still be indexed in Θ(1).
 * This is done by taking a hit to memory. See below.
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
 * NOTE:
 *   If you allow the array to grow to massive proportions, and then remove a tonne of elements, and you want the
 *   underlying array to be reduced back down to expansionFactor*size of the remaining elements, then you must
 *   manually call the trim() method. This is unnecessary for my use-case (see rationale).
 *
 * RATIONALE:
 *
 *   - In this project, this queue will be used to hold at most a full sentence of token objects. And there will only
 *     ever be as many in memory as there are concurrent calls of the Parser.parseSentence() method. So RAM will most
 *     definitely not be a concern.
 *
 *   - As far as I can see, the current styles of parsing would actually never need to grow the
 *     array (because they will always take items from the queue before adding items back. So technically I could
 *     just use a fixed length array equal to the actual collection size. But for the sake of maintainability and
 *     extensibility, a growing array will be used (perhaps parse styles on an individual basis could select an expansion
 *     factor of 1 if they guarantee this behaviour TODO).
 *
 *   - At least one of the default ParseStyle implementations performs insertions at both ends of the queue. So you'd
 *     think that a linked list might suffice. However, for EVERY SINGLE parsing decision, a feature vector of the
 *     parser state must be constructed. And to build such a vector, MANY features such as "the PoS of the 3rd item
 *     on the buffer queue" will be used. So the queue will be indexed into A LOT. Which for a linked list would mean
 *     walking the nodes A LOT (so indexing in to linked list would be worst case Θ(n), instead of the Θ(1) of this
 *     structure). Testing has confirmed a non-trivial speed-up.
 *
 * User: Andrew D. Robertson
 * Date: 25/04/2014
 * Time: 11:30
 */
public class IndexableQueue<E> {

    private Object[] elements;   // The array of length size()*expansionFactor. It contains the queue elements in the middle.
    private int startIndex;      // The index in *elements* where the elements actually start
    private int endIndex;        // The index in *elements* where the elements actually start
    private double expansionFactor; // The factor which influences the size of *elements*. A factor of 2 implies that the size of *elements* will be twice the number of actual elements.

    public IndexableQueue() {
        this(2);
    }

    public IndexableQueue(double expansionFactor) {
        this.expansionFactor = expansionFactor;
        newElementArray();
    }

    public IndexableQueue(Collection<? extends E> elements) {
        this(elements, 2);
    }

    public IndexableQueue(Collection<? extends E> elements, double expansionFactor){
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
     * Θ(1)
     */
    public E get(int index) {
        return (E)elements[startIndex+index];
    }

    /**
     * Add an element to the end of the queue.
     * Note: if there is no more room at the end of the array, then a bigger array is created, and the old values
     *       are copied across.
     *
     * Amortized constant
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
     *
     * Θ(1)
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
     * Amortized constant
     */
    public void addToFront(E element) {
        if (startIndex == 0) {
            newElementArray(elements, startIndex, endIndex);
        }
        elements[--startIndex] = element;
    }

    /**
     * This returns the number of elements that have been added to the queue.
     * Θ(1)
     */
    public int size() {
        return endIndex - startIndex;
    }

    /**
     * Return true if there are no elements in the queue.
     * Θ(1)
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
     * Θ(n)
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

    /**
     * Create a new array for *elements* whose size is elements.size()*expansionFactor.
     * Copy all elements into new array.
     * Θ(n)
     */
    private void newElementArray(Object[] elements, int start, int end){
        if (start < 0 || end > elements.length)
            throw new RuntimeException("Invalid start and end arguments.");

        this.elements = new Object[(int)Math.ceil((end-start)*expansionFactor)];
        startIndex = (this.elements.length / 2) - ((end-start)/2);
        endIndex = startIndex + (end-start);

        System.arraycopy(elements, start, this.elements, startIndex, end-start);
    }
}
