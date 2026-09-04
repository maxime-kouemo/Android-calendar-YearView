package com.mamboa.yearview.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip contract for [DayConfig]'s pixel ↔ multiplier conversion.
 *
 * The multiplier is what makes day backgrounds resolution independent, and it is
 * converted back to pixels on every draw, so the two directions must agree.
 */
class DayConfigMetricsTest {

    @Test
    fun `pixel margin survives a round trip through the multiplier`() {
        val textSizePx = 32
        for (radiusPx in 0..40) {
            val multiplier = DayConfig.pixelsToMultiplier(radiusPx, textSizePx)
            val backToPixels = DayConfig.multiplierToPixelMargin(multiplier, textSizePx)
            assertEquals("radiusPx=$radiusPx", radiusPx, backToPixels)
        }
    }

    @Test
    fun `round trip holds across a range of text sizes`() {
        for (textSizePx in intArrayOf(8, 10, 12, 16, 24, 32, 48, 64)) {
            for (radiusPx in intArrayOf(0, 1, 2, 4, 8, 16)) {
                val multiplier = DayConfig.pixelsToMultiplier(radiusPx, textSizePx)
                val backToPixels = DayConfig.multiplierToPixelMargin(multiplier, textSizePx)
                assertEquals(
                    "textSize=$textSizePx radius=$radiusPx",
                    radiusPx,
                    backToPixels
                )
            }
        }
    }

    @Test
    fun `a snug background is the identity multiplier`() {
        assertEquals(1f, DayConfig.pixelsToMultiplier(0, 32), EPSILON)
        assertEquals(0, DayConfig.multiplierToPixelMargin(1f, 32))
    }

    @Test
    fun `larger radii produce larger multipliers`() {
        val small = DayConfig.pixelsToMultiplier(4, 32)
        val large = DayConfig.pixelsToMultiplier(12, 32)
        assertTrue("expected $large > $small", large > small)
    }

    @Test
    fun `the multiplier is resolution independent`() {
        // The same physical ratio (radius = a quarter of the text size) must yield
        // the same multiplier regardless of the density the pixels were measured at.
        val atMdpi = DayConfig.pixelsToMultiplier(radiusPx = 4, textSizePx = 16)
        val atXhdpi = DayConfig.pixelsToMultiplier(radiusPx = 8, textSizePx = 32)
        assertEquals(atMdpi, atXhdpi, EPSILON)
    }

    @Test
    fun `a zero text size degrades gracefully instead of dividing by zero`() {
        assertEquals(1f, DayConfig.pixelsToMultiplier(8, 0), EPSILON)
        assertEquals(0, DayConfig.multiplierToPixelMargin(2f, 0))
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
