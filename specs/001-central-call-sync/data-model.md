# Data Model: CATI Call Logger Entities

## Entities

### CallLog (Primary)
**Fields:**
- callLogId: UUID (PK)
- direction: String (incoming/outgoing)
- callStartUtc: Instant
- callEndUtc: Instant
- durationSeconds: Double
- outcome: String (answered/no_answer/busy/failed/rejected)
- outcomeDetail: String?
- deviceId: String
- instanceId: String? (from intent)
- phoneNumber: String?
- attemptNumber: Int?
- enumeratorId: String?
- extrasJson: String (JSON of filtered intent extras)
- surveyId: String? (incoming survey calls)
- additionalNotes: String? (incoming survey calls)
- synced: Boolean (default false)
- syncAttempts: Int (default 0)
- lastSyncError: String?

**Validation Rules:**
- durationSeconds >= 0
- outcome in enum values
- callStartUtc < callEndUtc

### CentralCredentials
**Fields:**
- id: Int (PK, autoincrement)
- username: String
- passwordHash: String (encrypted)
- validated: Boolean (default false)
- lastValidation: Instant?

**Validation Rules:**
- username/password non-empty
- validated true before use

### PendingSync
**Fields:**
- id: UUID (PK)
- callLogId: UUID (FK to CallLog)
- syncPayloadJson: String
- retryCount: Int (default 0)
- nextRetry: Instant?
- errorType: String? (network/auth/server)

**Relationships:**
- ForeignKey(CallLog.callLogId)

### PartialSurveyData
**Fields:**
- id: UUID (PK)
- callLogId: UUID (FK to CallLog)
- surveyId: String?
- additionalNotes: String?
- isComplete: Boolean (default false)
- createdAt: Instant

**Relationships:**
- ForeignKey(CallLog.callLogId)

## Relationships
```
CallLog 1---* PendingSync (one log → multiple retries)
CallLog 1---1 PartialSurveyData (incoming survey partial data)
```

## State Transitions
**CallLog:**
```
NEW → LOGGED (detection complete)
LOGGED → SYNC_PENDING (queued)
SYNC_PENDING → SYNCED/SYNC_FAILED (WorkManager result)
SYNC_FAILED → SYNC_PENDING (retry)
```

## Indexes
- CallLog: idx_unsynced (synced=false)
- PendingSync: idx_next_retry (nextRetry <= now())

**Data Model Status: READY FOR IMPLEMENTATION**