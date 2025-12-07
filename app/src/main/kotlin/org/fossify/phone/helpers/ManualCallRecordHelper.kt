package org.fossify.phone.helpers

import android.content.Context
import org.fossify.phone.models.CallOutcome
import java.util.UUID

data class ManualCallRecordData(
    val phoneNumber: String,
    val isOutgoing: Boolean,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Double,
    val outcome: CallOutcome,
    val outcomeDetail: String? = null
)

sealed class ManualCallRecordResult {
    data class Success(val callLogId: UUID) : ManualCallRecordResult()
    object Duplicate : ManualCallRecordResult()
    data class Failure(val error: String) : ManualCallRecordResult()
}

class ManualCallRecordHelper(private val callLogger: CallLogger) {
    constructor(context: Context) : this(CallLogger(context))

    suspend fun createManualRecord(data: ManualCallRecordData): ManualCallRecordResult {
        return try {
            val callLog = callLogger.createCallLog(
                phoneNumber = data.phoneNumber,
                isOutgoing = data.isOutgoing,
                startTime = data.startTime,
                endTime = data.endTime,
                durationSeconds = data.durationSeconds,
                outcome = data.outcome,
                outcomeDetail = data.outcomeDetail,
                skipDuplicateCheck = true,
                isOdkCall = false
            )
            if (callLog == null) {
                ManualCallRecordResult.Duplicate
            } else {
                ManualCallRecordResult.Success(callLog.callLogId)
            }
        } catch (e: Exception) {
            ManualCallRecordResult.Failure(e.message ?: "manual_record_error")
        }
    }
}
