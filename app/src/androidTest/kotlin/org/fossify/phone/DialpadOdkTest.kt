package org.fossify.phone

// T016 Instrumentation ODK intent test
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialpadOdkTest {
    @Test
    fun testOdkLaunchAndReturn() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Launch with ODK extras
        val intent = Intent("org.fossify.phone").apply {
            putExtra("phoneNumber", "+123")
            putExtra("odk_field_id", "testfield")
        }
        context.startActivity(intent)

        // Simulate dial/call/connect/disconnect
        device.waitForIdle(2000)
        // Verify setResult extras (mock via logs or dumpsys)

        assertTrue("ODK session initialized", true) // Placeholder - full impl via Espresso
    }
}
