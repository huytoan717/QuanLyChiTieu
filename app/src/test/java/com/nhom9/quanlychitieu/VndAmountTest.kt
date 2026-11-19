package com.nhom9.quanlychitieu

import com.nhom9.quanlychitieu.model.VndAmount
import org.junit.Assert.*
import org.junit.Test

class VndAmountTest {
    @Test fun parsesPlainAndVietnameseGroupedAmounts() {
        assertEquals(150000L, VndAmount.parse("150000"))
        assertEquals(150000L, VndAmount.parse(" 150.000 "))
        assertEquals(3_000_000_000L, VndAmount.parse("3.000.000.000"))
    }

    @Test fun rejectsEmptyZeroNegativeAndMalformedAmounts() {
        listOf("", "  ", "0", "000", "-100", "1.5", "12.34.567", "1,500", "abc", "1 000").forEach {
            assertNull(it, VndAmount.parse(it))
        }
    }

    @Test fun rejectsOverflow() {
        assertNull(VndAmount.parse("9223372036854775808"))
        assertNull(VndAmount.add(Long.MAX_VALUE.toString(), 20000))
    }

    @Test fun shortcutsAddWithoutResettingInvalidInput() {
        assertEquals(20000L, VndAmount.add("", 20000))
        assertEquals(20000L, VndAmount.add("0", 20000))
        assertEquals(3_000_020_000L, VndAmount.add("3.000.000.000", 20000))
        assertNull(VndAmount.add("oops", 20000))
    }
}
