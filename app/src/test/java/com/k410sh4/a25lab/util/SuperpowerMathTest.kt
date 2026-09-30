package com.k410sh4.a25lab.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperpowerMathTest {
    @Test
    fun magnitude3_calculatesEuclideanMagnitude() {
        assertEquals(13f, SuperpowerMath.magnitude3(3f, 4f, 12f), 0.0001f)
    }

    @Test
    fun dynamicAccelerationFallback_removesOneGAtRest() {
        assertEquals(
            0f,
            SuperpowerMath.dynamicAccelerationFallback(0f, 0f, 9.80665f),
            0.0001f,
        )
    }

    @Test
    fun sensorAccuracyName_mapsAndroidAccuracyLevels() {
        assertEquals("não confiável", SuperpowerMath.sensorAccuracyName(0))
        assertEquals("baixa", SuperpowerMath.sensorAccuracyName(1))
        assertEquals("média", SuperpowerMath.sensorAccuracyName(2))
        assertEquals("alta", SuperpowerMath.sensorAccuracyName(3))
    }

    @Test
    fun motionLevel_classifiesStrongMotion() {
        assertEquals("movimento forte", SuperpowerMath.motionLevel(3f, 0.2f))
    }

    @Test
    fun rssiBand_classifiesSignalStrength() {
        assertEquals("muito forte", SuperpowerMath.rssiBand(-45))
        assertEquals("forte", SuperpowerMath.rssiBand(-60))
        assertEquals("médio", SuperpowerMath.rssiBand(-72))
        assertEquals("fraco", SuperpowerMath.rssiBand(-90))
    }
}
