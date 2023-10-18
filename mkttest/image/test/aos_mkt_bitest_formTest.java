package mkttest.image.test;

import mkt.image.test.aos_mkt_bitest_form;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class aos_mkt_bitest_formTest {

    @Test
    void aos_junit() {
        assertEquals(1, aos_mkt_bitest_form.aos_junit(1));
        assertEquals(1, aos_mkt_bitest_form.aos_junit(2));
    }
}