package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.data.ShakeSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Умный Экран", appName)
    }

    @Test
    fun `test preferences manager defaults and updates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager.getInstance(context)

        assertNotNull(prefs.settingsFlow.value)
        prefs.setShakeSensitivity(ShakeSensitivity.HIGH)
        assertEquals(ShakeSensitivity.HIGH, prefs.settingsFlow.value.shakeSensitivity)

        prefs.setHapticsEnabled(true)
        assertTrue(prefs.settingsFlow.value.isHapticsEnabled)
    }
}
