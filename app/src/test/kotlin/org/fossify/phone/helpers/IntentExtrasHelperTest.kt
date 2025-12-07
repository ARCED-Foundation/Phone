package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.fossify.phone.testutil.TestApplication
import androidx.test.core.app.ApplicationProvider
import org.fossify.phone.helpers.AdminSettingsHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class IntentExtrasHelperTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test
    fun filtersReservedAndSystemExtras() {
        val intent = Intent().apply {
            putExtra("centralBaseUrl", "https://central.test")
            putExtra("centralProjectId", "12")
            putExtra("centralDatasetName", "dataset")
            putExtra("system_callId", "abc")
            putExtra("custom_reserved", "value")
            putExtra("field_id", "1234")
            putExtra("phoneNumber", "+123")
        }

        val extras = IntentExtrasHelper.getFilteredExtras(
            intent,
            IntentExtrasHelper.ReservedKeyConfig(customReservedKeys = setOf("custom_reserved"))
        )

        assertFalse("Reserved keys should be filtered out", extras.containsKey("centralBaseUrl"))
        assertFalse("System prefix should be filtered out", extras.containsKey("system_callId"))
        assertFalse("Custom reserved keys should be filtered out", extras.containsKey("custom_reserved"))
        assertEquals("1234", extras["field_id"])
        assertEquals("+123", extras["phoneNumber"])
    }

    @Test
    fun extrasToJsonEncodesPrimitives() {
        val intent = Intent().apply {
            putExtra("string", "value")
            putExtra("int", 5)
            putExtra("bool", true)
        }

        val json = IntentExtrasHelper.extrasToJson(intent)
        val parsed = JSONObject(json)

        assertEquals("value", parsed.getString("string"))
        assertEquals(5, parsed.getInt("int"))
        assertEquals(true, parsed.getBoolean("bool"))
    }

    @Test
    fun filtersExtrasUsingStoredReservedKeys() {
        val helper = AdminSettingsHelper(context)
        helper.clearCustomReservedKeys()
        helper.addReservedKey("custom_reserved")

        val intent = Intent().apply {
            putExtra("custom_reserved", "filtered")
            putExtra("field_id", "visible")
        }

        val extras = IntentExtrasHelper.getFilteredExtras(context, intent)

        assertFalse(extras.containsKey("custom_reserved"))
        assertEquals("visible", extras["field_id"])
    }

    @Test
    fun extractOdkConfigRequiresMandatoryFields() {
        val incomplete = Intent().apply { putExtra("centralBaseUrl", "https://central.test") }
        assertNull(IntentExtrasHelper.extractOdkConfig(incomplete))

        val complete = Intent().apply {
            putExtra("centralBaseUrl", "https://central.test")
            putExtra("centralProjectId", "proj")
            putExtra("centralDatasetName", "dataset")
            putExtra("odkCollectInstanceId", "inst-1")
        }

        val config = IntentExtrasHelper.extractOdkConfig(complete)
        assertNotNull(config)
        assertEquals("https://central.test", config!!.baseUrl)
        assertEquals("proj", config.projectId)
        assertEquals("dataset", config.datasetName)
        assertEquals("inst-1", config.instanceId)
    }

    @After
    fun tearDown() {
        AdminSettingsHelper(context).clearCustomReservedKeys()
    }
}
