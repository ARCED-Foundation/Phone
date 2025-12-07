package org.fossify.phone

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.phone.helpers.AdminSettingsHelper
import org.fossify.phone.helpers.IntentExtrasHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReservedKeysIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val adminSettingsHelper = AdminSettingsHelper(context)

    @After
    fun tearDown() {
        adminSettingsHelper.clearCustomReservedKeys()
    }

    @Test
    fun reservedKeysFilteringReflectsStoredConfiguration() {
        adminSettingsHelper.clearCustomReservedKeys()
        adminSettingsHelper.addReservedKey("integration_reserved")

        val intent = Intent().apply {
            putExtra("centralBaseUrl", "https://central.test")
            putExtra("centralProjectId", "42")
            putExtra("centralDatasetName", "dataset")
            putExtra("system_sessionId", "system")
            putExtra("integration_reserved", "secret")
            putExtra("visible_field", "value")
        }

        val filtered = IntentExtrasHelper.getFilteredExtras(context, intent)

        assertFalse(filtered.containsKey("centralBaseUrl"))
        assertFalse(filtered.containsKey("centralProjectId"))
        assertFalse(filtered.containsKey("centralDatasetName"))
        assertFalse(filtered.containsKey("system_sessionId"))
        assertFalse(filtered.containsKey("integration_reserved"))
        assertEquals("value", filtered["visible_field"])

        assertTrue(adminSettingsHelper.hasReservedKeys())
        assertTrue(adminSettingsHelper.getReservedKeys().contains("integration_reserved"))

        val json = IntentExtrasHelper.extrasToJson(context, intent)
        assertFalse(json.contains("integration_reserved"))
        assertFalse(json.contains("system_sessionId"))
        assertTrue(json.contains("visible_field"))
    }
}
