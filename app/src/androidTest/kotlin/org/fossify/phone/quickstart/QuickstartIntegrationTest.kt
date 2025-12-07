package org.fossify.phone.quickstart

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.phone.R
import org.fossify.phone.activities.AdminSetupActivity
import org.fossify.phone.work.WorkManagerHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickstartIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun adminSetupJourneyIsAccessible() {
        ActivityScenario.launch(AdminSetupActivity::class.java).use {
            onView(withId(R.id.textAdminHeader)).check(matches(isDisplayed()))
            onView(withId(R.id.btnConfigureCredentials)).check(matches(isDisplayed()))
            onView(withId(R.id.btnConfigureReservedKeys)).check(matches(isDisplayed()))
            onView(withId(R.id.btnAddPin)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun failureScenarioBackgroundWorkDoesNotCrash() {
        WorkManagerHelper.cancelOdkSyncWork(context)
        WorkManagerHelper.enqueueOdkSyncWork(context)
        WorkManagerHelper.schedulePeriodicSync(context)
        assertTrue("Background work enqueued without crashing", true)
    }
}
