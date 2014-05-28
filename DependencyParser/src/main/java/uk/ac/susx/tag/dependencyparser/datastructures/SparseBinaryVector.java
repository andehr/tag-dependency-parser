package uk.ac.susx.tag.dependencyparser.datastructures;

import com.google.common.base.Joiner;
import com.google.common.collect.Ordering;
import it.unimi.dsi.fastutil.doubles.DoubleCollection;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sparse vector with no fixed number of dimensions.
 *
 * - All values are assumed to be 0 at every index until the user expressly uses the put() method to assign a value
 *   to an index. So requesting an index that the vector has never seen (using get()) will still result in 0.
 *
 * - When an index is added, its value is set immediately to 1 (hence the name binary vector).
 *
 * - However it is possible to normalise the length of the vector (which necessarily means that the vector isn't binary
 *   between 0 and 1. Though the values will be either zero or X where X is a constant:
 *
 *     X = 1 - (1 / (number of non-zero values))
 *
 * - Backed by a hashmap of primitives
 *
 * Created by Andrew D. Robertson on 13/04/2014.
 */
public class SparseBinaryVector {

    private Int2DoubleOpenHashMap elements = new Int2DoubleOpenHashMap();

    /**
     * Set an index to 1.
     */
    public void put(int index){
       elements.put(index, 1);
    }

    /**
     * Check if the value corresponding to an index is nonzero.
     */
    public boolean contains(int index) {
        return elements.get(index) != 0;
    }

    public double get(int index) {
        return elements.get(index);
    }

    public IntSet indices() { return elements.keySet(); }
    public DoubleCollection values() { return elements.values(); }

    /**
     * Normalise vector by its length.
     */
    public void normalise() {
        double length = Math.sqrt(elements.size());
        for (Int2DoubleMap.Entry entry : elements.int2DoubleEntrySet()){
            entry.setValue(1 / length);
        }
    }

    public int numNonZero() {
        return elements.size();
    }

    /**
     * String representation where the indices are in sorted order.
     */
    public String toString() {
        List<String> indexValuePairs = new ArrayList<>();
        for (Int2DoubleMap.Entry entry : new IndexOrdering().sortedCopy(elements.int2DoubleEntrySet())){
            indexValuePairs.add(entry.getIntKey() + ":" + entry.getDoubleValue());
        }
        return Joiner.on(" ").join(indexValuePairs);
    }

    private static class IndexOrdering extends Ordering<Int2DoubleMap.Entry> {
        @Override
        public int compare(Int2DoubleMap.Entry entry, Int2DoubleMap.Entry entry2) {
            return Integer.compare(entry.getIntKey(), entry2.getIntKey());
        }
    }
}
