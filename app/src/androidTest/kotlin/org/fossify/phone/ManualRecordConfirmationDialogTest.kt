package org.fossify.phone

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.phone.dialogs.ManualRecordConfirmationDialog
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualRecordConfirmationDialogTest {
    @Test
    fun dialogShowsConfiguredText() {
        launchFragmentInContainer<ManualRecordConfirmationDialog>(
            themeResId = android.R.style.Theme_Material_Light_Dialog_Alert
        )

        onView(withText(R.string.manual_record_dialog_title)).check(matches(isDisplayed()))
        onView(withText(R.string.manual_record_dialog_message)).check(matches(isDisplayed()))
        onView(withText(R.string.manual_record_dialog_confirm)).check(matches(isDisplayed()))
        onView(withText(R.string.manual_record_dialog_cancel)).check(matches(isDisplayed()))
    }
}
