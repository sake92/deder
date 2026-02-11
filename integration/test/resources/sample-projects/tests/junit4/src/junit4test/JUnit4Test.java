package junit4test;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class JUnit4Test {

    @Test
    public void test1() {
        assertEquals(1 + 1, 2);
    }

    @Test
    public void test2() {
        assertEquals("HeLLo".toLowerCase(), "hello");
    }
}



