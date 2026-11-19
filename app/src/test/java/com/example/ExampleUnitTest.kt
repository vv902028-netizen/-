package com.example

import com.example.data.AppSettings
import com.example.data.LockMethod
import com.example.data.ShakeSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `shake sensitivity thresholds are strictly ordered`() {
        assertTrue(ShakeSensitivity.LOW.threshold > ShakeSensitivity.MEDIUM.threshold)
        assertTrue(ShakeSensitivity.MEDIUM.threshold > ShakeSensitivity.HIGH.threshold)
    }

    @Test
    fun `default app settings prioritize safety and pocket guard`() {
        val defaults = AppSettings()
        assertFalse(defaults.isMasterServiceEnabled)
        assertTrue(defaults.isPocketGuardEnabled)
        assertTrue(defaults.isHapticsEnabled)
        assertEquals(LockMethod.AUTO, defaults.lockMethod)
        assertEquals(ShakeSensitivity.MEDIUM, defaults.shakeSensitivity)
    }
}
