package uk.ac.susx.tag.dependencyparser.datastructures;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for the Stack.
 *
 * User: Andrew D. Robertson
 * Date: 16/07/2014
 * Time: 15:43
 */
public class StackTest {

    /**
     * Check that the ordering of results from popping after pushing is in accordance with a FILO structure.
     */
    @Test
    public void pushingAndPopping(){

        Stack<Integer> s = new Stack<>();

        for (int i = 0; i < 100; i++){
            s.push(i);
        }

        for (int i = 99; i >= 0; i--){
            assertThat(s.pop(), is(i));
        }
    }

    /**
     * Check that the stack reports empty when it should
     */
    @Test
    public void emptiness(){

        Stack<Integer> s = new Stack<>();

        assertThat(s.isEmpty(), is(true));
        assertThat(s.isNotEmpty(), is(false));


        for (int i = 0; i < 100; i++){
            s.push(i);

            assertThat(s.isNotEmpty(), is(true));
            assertThat(s.isEmpty(), is(false));
        }


        for (int i = 0; i < 100; i++){
            assertThat(s.isNotEmpty(), is(true));
            assertThat(s.isEmpty(), is(false));

            s.pop();
        }

        assertThat(s.isEmpty(), is(true));
        assertThat(s.isNotEmpty(), is(false));
    }

    /**
     * Check that iteration occurs in accordance with the FILO structure
     */
    @Test
    public void iteration(){

        Stack<Integer> s = new Stack<>();

        for (int i = 0; i < 100; i++){
            s.push(i);
        }

        int i = 99;
        for (int element : s){
            assertThat(element, is(i));
            i--;
        }
    }
}
