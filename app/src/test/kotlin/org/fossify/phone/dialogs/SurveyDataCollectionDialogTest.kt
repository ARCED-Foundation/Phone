package org.fossify.phone.dialogs

import android.os.Looper
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import org.fossify.commons.views.MyTextInputLayout
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.helpers.SurveyDataCompletionHelper
import org.fossify.phone.testutil.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SurveyDataCollectionDialogTest {

    private val callLogId = UUID.randomUUID()

    @Test
    fun `save requires survey id`() = withSimpleActivity { activity ->
        var completedCalled = false

        val dialogInstance = SurveyDataCollectionDialog(
            activity = activity,
            draft = SurveyDataCompletionHelper.SurveyDraft(callLogId),
            autoSaveScope = activity.lifecycleScope,
            onSaveDraft = { _, _ -> },
            onComplete = { _, _ -> completedCalled = true },
            onSkip = { }
        )

        shadowOf(Looper.getMainLooper()).idle()
        val alert = dialogInstance.alertDialog
        assertNotNull(alert)

        val positive = alert!!.getButton(AlertDialog.BUTTON_POSITIVE)
        positive.performClick()

        assertFalse(completedCalled)
        assertTrue(alert.isShowing)

        val idLayout = alert!!.findViewById<MyTextInputLayout>(R.id.survey_data_id_hint)!!
        assertEquals(activity.getString(R.string.survey_id_required), idLayout.error)
    }

    @Test
    fun `complete passes trimmed values to completion callback`() = withSimpleActivity { activity ->
        var receivedSurveyId: String? = null
        var receivedNotes: String? = null

        val dialogInstance = SurveyDataCollectionDialog(
            activity = activity,
            draft = SurveyDataCompletionHelper.SurveyDraft(callLogId),
            autoSaveScope = activity.lifecycleScope,
            onSaveDraft = { _, _ -> },
            onComplete = { surveyId, notes ->
                receivedSurveyId = surveyId
                receivedNotes = notes
            },
            onSkip = { }
        )

        shadowOf(Looper.getMainLooper()).idle()
        val alert = dialogInstance.alertDialog
        assertNotNull(alert)

        alert!!.findViewById<TextInputEditText>(R.id.survey_data_id)!!.setText(" SURV-123 ")
        alert.findViewById<TextInputEditText>(R.id.survey_data_notes)!!.setText(" note ")

        val positive = alert.getButton(AlertDialog.BUTTON_POSITIVE)
        positive.performClick()

        assertEquals("SURV-123", receivedSurveyId)
        assertEquals("note", receivedNotes)
        assertFalse(alert.isShowing)
    }

    @Test
    fun `skip saves trimmed draft before skipping`() = withSimpleActivity { activity ->
        var savedSurveyId: String? = null
        var savedNotes: String? = null
        var skipCalled = false

        val dialogInstance = SurveyDataCollectionDialog(
            activity = activity,
            draft = SurveyDataCompletionHelper.SurveyDraft(callLogId),
            autoSaveScope = activity.lifecycleScope,
            onSaveDraft = { surveyId, notes ->
                savedSurveyId = surveyId
                savedNotes = notes
            },
            onComplete = { _, _ -> },
            onSkip = { skipCalled = true }
        )

        shadowOf(Looper.getMainLooper()).idle()
        val alert = dialogInstance.alertDialog
        assertNotNull(alert)

        alert!!.findViewById<TextInputEditText>(R.id.survey_data_id)!!.setText(" SURV-789 ")
        alert.findViewById<TextInputEditText>(R.id.survey_data_notes)!!.setText(" some notes ")

        val skipButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE)
        skipButton.performClick()

        assertTrue(skipCalled)
        assertEquals("SURV-789", savedSurveyId)
        assertEquals("some notes", savedNotes)
        assertFalse(alert.isShowing)
    }

    private fun withSimpleActivity(block: (SimpleActivity) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.runOnUiThread {
                block(activity)
            }
        } finally {
            controller.destroy()
        }
    }
}
