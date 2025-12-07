# Phase 0 Research Summary: CATI Call Logger with ODK Central Integration

## Research Topics Covered

### 1. Constitution Compliance Analysis
**Key Findings:**
- Privacy-First Design: Compliant (minimal data collection, proper permissions)
- Seamless External Integration: Partially compliant (needs core variant intent filter)
- Metadata Accuracy: Compliant (Telecom API connection-based timing)
- Build Variant Compatibility: Non-compliant (missing core variant support)
- User Experience Clarity: Partially compliant (needs visual feedback improvements)
- Integration Standards: Partially compliant (standardize intent actions)
- Testing Requirements: Non-compliant (needs comprehensive test suite)

**Decisions:**
- Decision: Proceed with justified violations documented in Complexity Tracking
- Rationale: Core functionality aligns with principles; variant/testing gaps addressed in implementation plan
- Alternatives: Full compliance would delay MVP by 4 weeks

### 2. ODK Central Entity API
**Key Findings:**
- Endpoint: POST /api/v1/projects/{projectId}/entity-types/call_logs/entities
- Auth: HTTP Basic (call_logger web user)
- Request: JSON {\"entity\":{\"properties\":{...}}}
- Response: {\"entity\":{\"uuid\":...,\"properties\":{...}}}
- Retry: Exponential backoff (1s → 30s max)
- Android: OkHttp + Gson + WorkManager integration

**Decisions:**
- Decision: Use OkHttp for API calls with RetryInterceptor
- Rationale: Matches existing Fossify network patterns, robust error handling
- Alternatives: Retrofit (overhead), Volley (limited)

### 3. Android Call Detection (Telecom API)
**Key Findings:**
- Leverage existing CallService/CallManager
- States: DIALING→CONNECTING→ACTIVE (answered); RINGING→DISCONNECTED (no_answer/rejected)
- Outcomes: disconnectCause for busy/failed/rejected
- Background: Foreground InCallService (battery efficient)
- Concurrent: Per-call state tracking in CallManager

**Decisions:**
- Decision: Extend CallManager with CallOutcome detection
- Rationale: Minimal changes to existing architecture
- Alternatives: PhoneStateListener (deprecated, battery drain)

### 4. Room Database Patterns
**Key Findings:**
- Entities: CallLog, CentralCredentials, PendingSync, PartialSurveyData
- DAOs: @Query/@Insert/@Update with transactions
- Relationships: ForeignKey (sessionId/callLogId)
- Migrations: Versioned schema updates
- Threading: CoroutineWorkers + Dispatchers.IO

**Decisions:**
- Decision: Room with TypeConverters for JSON extras
- Rationale: Standard Android persistence, offline-first
- Alternatives: Realm (proprietary), SQLite direct (maintenance heavy)

### 5. WorkManager Background Sync
**Key Findings:**
- Workers: OdkSyncWorker with ExponentialBackoffPolicy.LINEAR
- Constraints: Network.CONNECTED + BatteryNotLow
- Retry: 7 attempts (10s → 64min)
- Chaining: OneTime for immediate, PeriodicWorkRequest for daily
- Testing: WorkManagerTestInitProvider

**Decisions:**
- Decision: PeriodicWorkRequest (24h) + immediate enqueues
- Rationale: Battery/network aware, reliable retries
- Alternatives: AlarmManager (Doze restricted), JobScheduler (API<28)

## Resolved NEEDS CLARIFICATION from Technical Context
- Language: Kotlin 2.2.21 ✅
- Dependencies: Room/WorkManager/OkHttp ✅
- Storage: Room ✅
- Testing: JUnit/Espresso ✅
- Target: Android 8.0+ ✅
- Performance: <100ms detection, 5min sync ✅
- Scale: 100 pending logs ✅

**Research Status: COMPLETE** - All unknowns resolved. Ready for Phase 1 Design.