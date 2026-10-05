package com.example.autotap

import com.example.autotap.infrastructure.vision.SmartMaskEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMethodConvergenceTestSuite {

    @Test
    fun testAutoOptimization_ConvergesToMaximumThreshold() {
        val tw = 40
        val th = 40
        val templatePixels = IntArray(tw * th)
        
        for (y in 0 until th) {
            for (x in 0 until tw) {
                val idx = y * tw + x
                if (x == y || x == (tw - 1 - y) || (x in 18..21)) {
                    templatePixels[idx] = 0xFFFFFFFF.toInt()
                } else {
                    templatePixels[idx] = 0xFF1E1E1E.toInt()
                }
            }
        }

        val sw = 120
        val sh = 120
        val screenPixels = IntArray(sw * sh) { 0xFF1E1E1E.toInt() }
        
        for (y in 0 until th) {
            for (x in 0 until tw) {
                if (x == y || x == (tw - 1 - y) || (x in 18..21)) {
                    screenPixels[(40 + y) * sw + (40 + x)] = 0xFFFFFFFF.toInt()
                }
            }
        }

        val (bestSim, score) = SmartMaskEngine.autoOptimizeGlyphSegmentationFast(
            rawPixels = templatePixels,
            tw = tw,
            th = th,
            screenshotPixels = screenPixels,
            sw = sw,
            sh = sh,
            anchorX = 40,
            anchorY = 40
        )

        assertTrue(
            "Авто-метод обязан обеспечивать сходимость >= 80%, получено: $bestSim% (Score: $score)",
            bestSim >= 80
        )
    }
}
