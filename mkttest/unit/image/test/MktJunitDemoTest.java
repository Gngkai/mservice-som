package mktTest.unit.image.test;

import mkt.image.test.MktJunitDemo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MktJunitDemoTest {
    private mkt.image.test.MktJunitDemo MktJunitDemo;

    @BeforeEach
    public void setUp() {
        this.MktJunitDemo = new MktJunitDemo();
    }

    @AfterEach
    public void tearDown() {
        this.MktJunitDemo = null;
    }

    @Test
    void testAdd() {
        assertEquals(100, this.MktJunitDemo.add(100));
        assertEquals(150, this.MktJunitDemo.add(50));
        assertEquals(130, this.MktJunitDemo.add(-20));
    }

    @Test
    void testSub() {
        assertEquals(-100, this.MktJunitDemo.sub(100));
        assertEquals(-150, this.MktJunitDemo.sub(50));
        assertEquals(-130, this.MktJunitDemo.sub(-20));
    }

    @Test
    void aos_junit() {
        assertEquals(1, mkt.image.test.MktJunitDemo.demo(1));
        assertEquals(1, mkt.image.test.MktJunitDemo.demo(1));
    }
}