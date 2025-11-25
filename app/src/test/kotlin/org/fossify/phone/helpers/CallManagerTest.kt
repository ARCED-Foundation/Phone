// T015 Basic CallManager ODK tests
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.models.*
import org.junit.Test
import org.junit.Assert.*

class CallManagerTest {
    @Test
    fun testOdkSessionInit() {
        val context = mockContext()
        CallManager.initializeOdkSession(context, "123", "existing", "test", "field1", "pkg.test")
        val session = CallManager.getOdkSession()
        assertTrue(session?.isActive ?: false)
        assertEquals("123", session?.phoneNumber)
        assertEquals("field1", session?.fieldId)
        assertTrue(session?.autoReturnDisconnect ?: false)
    }

    @Test
    fun testConcatValue() {
        val callData = "Out: 123; Duration: 10.00s; Started: 2025-01-01T12:00:00Z"
        val existing = "prev"
        val concat = CallManager.concatenateCallValues(existing, callData)
        assertEquals("prev | Out: 123; Duration: 10.00s; Started: 2025-01-01T12:00:00Z", concat)
    }

    @Test
    fun testSessionEndTotals() {
        // Mock session with records
        val record1 = OdkCallRecord("id1", CallDirection.OUTGOING, "123", 10.0, 1, 1, 11, true, null, "")
        val session = ODKSession(true, "123", "", 1, listOf(record1), emptyMap(), "test")
        assertEquals(10.0, session.totalDuration, 0.0)
        assertEquals(1, session.successfulCallCount)
    }
}