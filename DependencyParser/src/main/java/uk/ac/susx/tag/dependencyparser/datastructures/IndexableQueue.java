package uk.ac.susx.tag.dependencyparser.datastructures;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A queue which allows efficient additions to either end (amortized constant), and can still be indexed in Θ(1).
 * This is done by taking a hit to memory. See below.
 *
 * The queue is implemented with an array that is twice (or expansionFactor) as big as the collection with which it
 * was initialised. Or if nothing is specifies, then size is 10*expansionFactor. Or you can specify an initial capacity,
 * so that the array is size initialCapacity*expansionFactor.
 *
 * The actual values are stored in the middle of the array (the actual values are centred on the middle of the array,
 * they don't start in the middle):
 *
 *   [ - , - , E, E, E, E, - , - ]
 *
 * This shows an initial capacity of 4 with expansion factor of 2.
 *
 * If through adding elements you need a bigger array, then using the expansionFactor, it will create a bigger array
 * and copy over the values (like an ArrayList would for example).
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
 *     structure). Testing has confirmed a non-trivial speed-up from linked lists. An ArrayList is very inefficient
 *     for insertions at the front, so it wasn't considered.
 *
 * User: Andrew D. Robertson
 * Date: 25/04/2014
 * Time: 11:30
 */
public class IndexableQueue<E> implements Iterable<E>{

    private Object[] elements;   // The array of length size()*expansionFactor. It contains the queue elements in the middle.
    private int startIndex;      // The index in *elements* where the elements actually start (inclusive)
    private int endIndex;        // The index in *elements* where the elements actually end (exclusive)

    /*
     * Initialisation without elements
     */

    /**
     * Initialise to be big enough to make at least 10 calls to push() or addToFront() before having to reallocate the
     * array.
     */
    public IndexableQueue() {
        this(10);
    }

    /**
     * By setting the initialCapacity to X, you are asking for enough space to make at least X calls to push() or
     * addToFront() before having to reallocate memory. This amounts to making an array of size 3X+2
     */
    public IndexableQueue(int initialCapacity) {
        newElementArray(initialCapacity);
    }

    /**
     * Create an empty queue big enough to support *addToFrontCapacity* number of addToFront() calls, and *pushCapacity*
     * number of calls to push().
     *
     * Size of array = addToFrontCapacity+pushCapacity
     */
    public IndexableQueue(int addToFrontCapacity, int pushCapacity){
        newElementArray(addToFrontCapacity, pushCapacity);
    }

    /*
     * Initialisation with elements. Create a queue just big enough to hold the data.
     *
     * Any subsequent addition of elements would require reallocation of data array.
     *
     * Except when:
     *
     *  - Calling addToFront() if the user has made a previous call to pop() (thereby making space at the front)
     *  - Calling push() if the user has made a previous call to removeFromEnd() (thereby making space at the end)
     */
    public IndexableQueue(Collection<? extends E> elements){
        this.elements = elements.toArray();
        startIndex = 0;
        endIndex = elements.size();
    }

    /**
     * Initialisation with elements. Create a queue big enough to hold the data, plus enough space to make
     * *additionalCapacity* number of calls to push().
     */
    public IndexableQueue(Collection<? extends E> elements, int additionalCapacity){
        Object[] toBeAdded = elements.toArray();
        this.elements = new Object[toBeAdded.length + (2*additionalCapacity)];
        startIndex = (this.elements.length / 2) - ((toBeAdded.length)/2);
        endIndex = startIndex + toBeAdded.length;
        System.arraycopy(toBeAdded, 0, this.elements, startIndex, toBeAdded.length);
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
        if (startIndex == 0) {  // If our start index has gone to 0, then the array has no more space to prepend items
            newElementArray(elements, startIndex, endIndex);  // So grow the array
        }
        elements[--startIndex] = element;
    }

    /**
     * Get and remove the last element on the queue.
     *
     * Θ(1)
     */
    public E removeFromEnd() {
        if (size() > 0) {
            E element = (E)elements[endIndex-1];
            elements[endIndex-1] = null;
            endIndex--;
            return element;
        } else throw new NoSuchElementException();
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
     * if you want the underlying array to be reduced back down to 1.5*size+2 of the remaining elements.
     * Θ(n)
     */
    public void trim() {
        newElementArray(elements, startIndex, endIndex);
    }

    /**
     * Bear in mind that the actual size of the underlying array will be initialCapacity*2 (in order to allow for
     * *initialCapacity* number of calls to push() and addToFront()).
     */
    private void newElementArray(int initialCapacity) {
        this.elements = new Object[2 * initialCapacity];
        startIndex = (this.elements.length / 2);
        endIndex = startIndex;
    }

    private void newElementArray(int addToFrontCapacity, int pushCapacity){
        this.elements = new Object[addToFrontCapacity+pushCapacity];
        startIndex = addToFrontCapacity;
        endIndex = startIndex;
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

        this.elements = new Object[calculateNewSize(end-start)];
        startIndex = (this.elements.length / 2) - ((end-start)/2);
        endIndex = startIndex + (end-start);

        System.arraycopy(elements, start, this.elements, startIndex, end - start);
    }

    private int calculateNewSize(int numElements){
        return (3*numElements)/2 + 2;
    }

    /**
     * Allows for use in a for-each loop.
     * Iterates in the order of the queue. So you get the elements in the order that you'd get if you
     * repeatedly called pop() (of course without the side-effect of removing them from the queue)
     * Θ(n)
     */
    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>(){
            int index = 0;

            public boolean hasNext() {
                return index < size();
            }

            public E next() {
                if(hasNext())
                    return get(index++); // increment index AFTER acquiring element
                else throw new NoSuchElementException();
            }

            public void remove() { throw new UnsupportedOperationException(); }
        };
    }

    @Override
    /**
     * Θ(n)
     */
    public String toString() {
        return "[" + Joiner.on(", ").join(this) + "]";
    }
}
