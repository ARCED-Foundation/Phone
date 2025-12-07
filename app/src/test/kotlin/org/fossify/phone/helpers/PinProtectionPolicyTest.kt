package org.fossify.phone.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicLong
import org.fossify.phone.helpers.Config
import org.fossify.phone.testutil.TestApplication
import org.fossify.phone.utils.SecurityUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [33], application = TestApplication::class)
class PinProtectionPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var config: Config
    private lateinit var policy: PinProtectionPolicy
    private val clockTime = AtomicLong(1_000_000L)

    @Before
    fun setUp() {
        config = Config.newInstance(context)
        resetPinState()
        policy = PinProtectionPolicy(config, clock = { clockTime.get() })
    }

    @After
    fun tearDown() {
        resetPinState()
    }

    @Test
    fun `success resets attempts and lockout`() {
        config.adminPinHash = SecurityUtils.hashSecret("4321")
        config.adminPinFailedAttempts = 2
        config.adminPinLockoutUntil = clockTime.get() + 10_000

        val result = policy.validatePin("4321")

        assertTrue(result.isValid)
        assertFalse(result.lockoutTriggered)
        assertEquals(0, config.adminPinFailedAttempts)
        assertEquals(0L, config.adminPinLockoutUntil)
    }

    @Test
    fun `failed attempts lock out after max attempts`() {
        config.adminPinHash = SecurityUtils.hashSecret("9876")

        repeat(policy.maxAttempts) { attempt ->
            val result = policy.validatePin("0000")
            if (attempt == policy.maxAttempts - 1) {
                assertFalse(result.isValid)
                assertTrue(result.lockoutTriggered)
            } else {
                assertFalse(result.isValid)
                assertFalse(result.lockoutTriggered)
            }
        }

        val expectedLockout =
            clockTime.get() + policy.lockoutMinutes * 60_000
        assertEquals(expectedLockout, config.adminPinLockoutUntil)
        assertTrue(policy.isLockedOut())
    }

    private fun resetPinState() {
        config.adminPinHash = null
        config.adminPinFailedAttempts = 0
        config.adminPinLockoutUntil = 0
    }
}
