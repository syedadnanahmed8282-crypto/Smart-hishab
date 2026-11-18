package com.smarthisab.app

import com.smarthisab.app.domain.CalcEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcEngineTest {
    private fun ev(s: String) = CalcEngine.eval(s)

    @Test fun `multiplication before addition`() {
        assertEquals(1010.0, ev("120×8+50")!!, 0.0001)
        assertEquals(14.0, ev("2+3×4")!!, 0.0001)
        assertEquals(2.5, ev("10÷4")!!, 0.0001)
    }

    @Test fun `bengali digits and symbols are normalised`() {
        assertEquals(1010.0, ev("১২০×৮+৫০")!!, 0.0001)
        assertEquals(6.0, ev("2*3")!!, 0.0001)
        assertEquals(5.0, ev("10-5")!!, 0.0001)
    }

    @Test fun `percent rules`() {
        assertEquals(220.0, ev("200+10%")!!, 0.0001)    // ২০০ এর ১০% যোগ
        assertEquals(180.0, ev("200−10%")!!, 0.0001)
        assertEquals(0.5, ev("50%")!!, 0.0001)
        assertEquals(20.0, ev("200×10%")!!, 0.0001)
    }

    @Test fun `dangling operator is ignored and invalid gives null`() {
        assertEquals(5.0, ev("5+")!!, 0.0001)
        assertNull(ev("10÷0"))
        assertNull(ev(""))
        assertNull(ev("−"))
    }

    @Test fun `negative start and decimals`() {
        assertEquals(-3.0, ev("−5+2")!!, 0.0001)
        assertEquals(0.3, ev("0.1+0.2")!!, 0.0000001)
    }

    @Test fun `format uses bengali digits grouping and trims zeros`() {
        assertEquals("১,০১০", CalcEngine.format(1010.0))
        assertEquals("০.৩", CalcEngine.format(0.1 + 0.2))
        assertEquals("−১২,৫০০", CalcEngine.format(-12500.0))
        assertEquals("১,২৩৪.৫", CalcEngine.format(1234.5))
        assertEquals("1010", CalcEngine.plain(1010.0))
    }
}
