package uk.ac.susx.tag.dependencyparser.datastructures;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created with IntelliJ IDEA.
 * User: Andrew D. Robertson
 * Date: 16/07/2014
 * Time: 14:34
 */
public class SparseBinaryVectorTest {

    /**
     * Check that values stores in the vector can be extracted
     */
    @Test
    public void retainsValues(){
        SparseBinaryVector v = getTestVector();

        assertThat(v.contains(3), is(true));
        assertThat(v.contains(18), is(true));
        assertThat(v.contains(24342345), is(true));
        assertThat(v.contains(75), is(true));
    }

    /**
     * Check that normalisation is done correctly.
     */
    @Test
    public void normalisation(){
        SparseBinaryVector v = getTestVector();
        v.normalise();

        double normalised = 1 / Math.sqrt(4);

        for (double value : v.values()){
            assertThat(value, is(normalised));
        }
    }

    /**
     * Check that vector is binary before normalisation
     */
    @Test
    public void binary(){
        SparseBinaryVector v = getTestVector();

        for (double value : v.values()){
            assertThat(value, is(1.0));
        }
    }

    /**
     * Check that the string representation of the vector is correct
     */
    @Test
    public void stringRepresentation(){
        assertThat(getTestVector().toString(), is("3:1.0 18:1.0 75:1.0 24342345:1.0"));
    }


    private static SparseBinaryVector getTestVector(){
        SparseBinaryVector v = new SparseBinaryVector();

        v.put(3);
        v.put(18);
        v.put(24342345);
        v.put(75);

        return v;
    }
}
