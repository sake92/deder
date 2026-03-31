package junit5test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JUnit5Test {

    @Test
    public void test1() {
        assertEquals(1 + 1, 2);
    }

    @Test
    public void test2() {
        assertEquals("HeLLo".toLowerCase(), "hello");
    }
}



