package com.barebonesdownloader.android
import com.barebonesdownloader.android.media.*
import org.junit.Test
import org.junit.Assert.*
class SizeBudgetTest {
    @Test fun decimalBoundaryIsStrictAndCannotOverflow() {
        assertEquals(16_384_000_000L, SizeBudget.bytes(16384))
        assertTrue(SizeBudget.fits(999999, SizeBudget.bytes(1)))
        assertFalse(SizeBudget.fits(1000000, SizeBudget.bytes(1)))
        assertTrue(SizeBudget.fits(Long.MAX_VALUE, 0))
    }
    @Test fun invalidDurationFailsAndReductionIsBoundedMonotonic() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertTrue(runCatching { SizeBudget.estimate(100, it, 16, 512) }.isFailure) }
        var rate = SizeBudget.estimate(1_000_000, 60.0, 16, 512)
        assertTrue(rate < 134)
        repeat(7) { val next = SizeBudget.lower(rate, 16); assertTrue(next in 16..rate); rate = next }
    }
}
