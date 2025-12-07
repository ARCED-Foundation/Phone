package org.fossify.phone.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.fossify.phone.testutil.TestApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AdminSettingsHelperTest {

    private lateinit var helper: AdminSettingsHelper
    private lateinit var server: MockWebServer
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        helper = AdminSettingsHelper(context)
        helper.clearCustomReservedKeys()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        helper.clearCustomReservedKeys()
        server.shutdown()
    }

    @Test
    fun `validateCredentialsBeforeStorage rejects missing fields`() = runBlocking {
        val result = helper.validateCredentialsBeforeStorage("", "", "")
        assertFalse(result)
    }

    @Test
    fun `validateCredentialsBeforeStorage fails when central returns unauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val baseUrl = server.url("/").toString()
        val result = helper.validateCredentialsBeforeStorage(baseUrl, "user", "pass")

        assertFalse(result)
    }

    @Test
    fun `validateCredentialsBeforeStorage succeeds on valid response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val baseUrl = server.url("/").toString()
        val result = helper.validateCredentialsBeforeStorage(baseUrl, "user", "pass")

        assertTrue(result)

        val request = server.takeRequest()
        assertTrue(request.path?.contains("/v1/users/current") == true)
        assertTrue(request.method == "GET")
    }

    @Test
    fun `updateReservedKeys stores valid custom keys`() {
        val success = helper.updateReservedKeys(setOf("custom_reserved"))

        assertTrue(success)
        assertTrue(helper.getReservedKeys().contains("custom_reserved"))
    }

    @Test
    fun `updateReservedKeys rejects invalid custom keys`() {
        val success = helper.updateReservedKeys(setOf("invalid key!"))

        assertFalse(success)
        assertFalse(helper.getReservedKeys().contains("invalid key!"))
    }
}
