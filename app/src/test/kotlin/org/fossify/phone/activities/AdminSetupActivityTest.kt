package org.fossify.phone.activities

import android.content.Context
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.testutil.TestApplication
import org.fossify.phone.utils.SecurityUtils
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AdminSetupActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        resetPinState()
    }

    @Test
    fun `buttons reflect no pin state`() {
        resetPinState()
        ActivityScenario.launch(AdminSetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val addPin = activity.findViewById<Button>(R.id.btnAddPin)
                val removePin = activity.findViewById<Button>(R.id.btnRemovePin)

                assertTrue(addPin.isEnabled)
                assertFalse(removePin.isEnabled)
            }
        }
    }

    @Test
    fun `buttons reflect pin set state`() {
        context.config.adminPinHash = SecurityUtils.hashSecret("0000")
        ActivityScenario.launch(AdminSetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val addPin = activity.findViewById<Button>(R.id.btnAddPin)
                val removePin = activity.findViewById<Button>(R.id.btnRemovePin)

                assertFalse(addPin.isEnabled)
                assertTrue(removePin.isEnabled)
            }
        }
    }

    @Test
    fun `configure credentials button launches settings`() {
        resetPinState()
        ActivityScenario.launch(AdminSetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.btnConfigureCredentials)
                button.performClick()

                val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
                assertTrue(
                    nextIntent.component?.className == CredentialsSetupActivity::class.java.name
                )
            }
        }
    }

    private fun resetPinState() {
        context.config.adminPinHash = null
        context.config.adminPinFailedAttempts = 0
        context.config.adminPinLockoutUntil = 0L
    }
}
