# Feature Specification: CATI Call Logger with ODK Central Integration

**Feature Branch**: `001-central-call-sync`
**Created**: 2025-11-30
**Status**: Draft
**Input**: User description: "Let's now plan to implement the remaining work in the Phone app at c:/Users/Mehrab Ali/Documents/GitHub/Phone. Create a new branch from the master branch called cati-call-logger in the Phone repo. Modify the existing phone app so that after every call attempt (connected, not answered, busy, failed, rejected), the app automatically generates a structured call log record and syncs it to ODK Central by creating a new Entity in a call_logs Dataset using the Central Entity API..."

## Clarifications

### Session 2025-11-30

- Q: Call Direction Handling → A: Support both outgoing and incoming calls, with distinct direction fields and post-call data collection for incoming survey calls (UUID not available for incoming)
- Q: Reserved Intent Extras Filtering → C: Use naming convention prefix (e.g., "system_") to identify reserved keys dynamically
- Q: Sync Retry Strategy → B: Exponential backoff retry strategy with increasing delays between attempts

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Automated Call Logging (Priority: P1)

Enumerator makes phone calls through the app, and every completed call attempt is automatically logged with detailed timing and outcome information.

**Why this priority**: This is the core functionality - without automatic call logging, there's no value proposition for the system. It must work reliably for every call attempt.

**Independent Test**: Can be tested by making various types of calls (answered, no-answer, busy, failed) and verifying that corresponding log records are created with correct timestamps, outcomes, and attempt information.

**Acceptance Scenarios**:

1. **Given** a call is answered and connected, **When** the call ends naturally, **Then** a call log record is created with outcome "answered", correct start/end timestamps, and duration
2. **Given** a call rings but isn't answered, **When** the call times out, **Then** a call log record is created with outcome "no_answer" and appropriate duration
3. **Given** a call encounters a busy signal, **When** the call fails, **Then** a call log record is created with outcome "busy" and correct timestamps

---

### User Story 2 - Dynamic ODK Central Sync (Priority: P1)

System reads all configuration from ODK Collect intent extras and syncs call logs to the configured ODK Central instance.

**Why this priority**: Dynamic configuration makes the system flexible and usable across different projects and deployments without app reconfiguration.

**Independent Test**: Can be tested by launching the app with different intent extras and verifying that sync attempts use the correct Central URL, project ID, dataset name, and authentication credentials.

**Acceptance Scenarios**:

1. **Given** the app is launched with intent extras including centralBaseUrl, centralProjectId, and centralDatasetName, **When** a call log is ready to sync, **Then** the system makes API calls to the specified ODK Central instance
2. **Given** custom enumerators data in intent extras (instanceId, phoneNumber, attemptNumber, enumeratorId), **When** creating entities, **Then** this data is included in the entity payload sent to Central
3. **Given** reserved keys are present in intent extras, **When** processing extras, **Then** reserved keys are filtered out and only dynamic data is sent to Central

---

### User Story 3 - Secure Credential Management (Priority: P2)

Supervisors can securely configure ODK Central credentials through a PIN-protected admin interface.

**Why this priority**: Credentials must be protected, and supervisors need a reliable way to manage them without compromising security.

**Independent Test**: Can be tested by accessing the admin setup screen, entering credentials, performing validation, and verifying that credentials are only stored when validation succeeds.

**Acceptance Scenarios**:

1. **Given** no PIN has been set, **When** accessing admin settings, **Then** user is prompted to create a PIN
2. **Given** a PIN is set, **When** accessing admin settings, **Then** user must enter the correct PIN to access credential management
3. **Given** incorrect credentials are entered in setup, **When** validation is performed, **Then** error is shown and credentials are not saved
4. **Given** correct credentials are entered, **When** validation is performed, **Then** credentials are securely stored and success message is shown

---

### User Story 7 - Reserved Keys Configuration (Priority: P3)

Supervisors can configure which intent extras are considered reserved keys and should be filtered out from call log data sent to ODK Central.

**Why this priority**: Provides flexibility for different deployment scenarios and allows customization of system behavior without code changes.

**Independent Test**: Can be tested by adding/removing reserved keys and verifying that the correct extras are included/excluded from sync payloads.

**Acceptance Scenarios**:

1. **Given** no custom reserved keys are configured, **When** processing intent extras, **Then** default system keys are filtered out
2. **Given** custom reserved keys are added in admin settings, **When** processing intent extras, **Then** both default and custom reserved keys are filtered out
3. **Given** reserved keys are updated, **When** processing new call logs, **Then** the updated set of reserved keys is applied

---

### User Story 4 - Background Sync with Reliability (Priority: P2)

System syncs pending call logs to ODK Central in background using WorkManager with proper retry logic.

**Why this priority**: Background sync ensures reliability and doesn't block user experience. Retry logic prevents data loss even with intermittent network issues.

**Independent Test**: Can be tested by simulating network failures and verifying that sync attempts continue in background and retry appropriately.

**Acceptance Scenarios**:

1. **Given** a call log is pending sync, **When** network is available, **Then** sync attempt is made in background
2. **Given** network call fails, **When** WorkManager retries, **Then** retry occurs after appropriate backoff period
3. **Given** authentication fails, **When** sync attempt fails, **Then** appropriate error is flagged and admin is notified
4. **Given** sync succeeds, **When** Central confirms entity creation, **Then** call log is marked as synced

---

### User Story 5 - Manual Fallback Mechanism (Priority: P3)

Users can manually end calls and create call records if automatic detection fails.

**Why this priority**: Provides a safety net when technology fails, ensuring no call data is lost even if automated detection doesn't work.

**Independent Test**: Can be tested by triggering the manual fallback button and verifying that a call log record is created regardless of call state.

**Acceptance Scenarios**:

1. **Given** automatic call detection fails, **When** user taps "End call & record" button, **Then** a call log record is created with current call information
2. **Given** manual record is created, **When** system detects actual call end, **Then** only one record is created (no duplication)

---

### User Story 6 - Incoming Survey Call Data Collection (Priority: P2)

For incoming calls that are survey calls, users can provide additional survey information after the call ends, including Survey ID and notes, with the ability to save partial data if interrupted.

**Why this priority**: Essential for research scenarios where incoming calls need to be tracked and associated with survey data, but cannot be initiated from ODK Collect.

**Independent Test**: Can be tested by receiving incoming survey calls, completing post-call data collection, and verifying that survey information is properly stored and synced.

**Acceptance Scenarios**:

1. **Given** an incoming call ends and is identified as a survey call, **When** user completes the post-call data collection, **Then** Survey ID, additional notes, and call information are saved together
2. **Given** post-call data collection is interrupted (app crash, user leaves), **When** system resumes, **Then** partial data is preserved and user can complete data entry
3. **Given** partial survey data exists, **When** user accesses the survey completion screen, **Then** previously entered data is displayed and user can complete missing fields

---

### Edge Cases

- What happens when the phone app is force-stopped during call logging? - Call logs should persist in local database and sync when app resumes
- How does system handle invalid timestamps from device? - System should use system clock and handle time zone differences appropriately
- What happens if ODK Central API returns an error after creating entity? - Should retry sync and maintain local copy until successful
- How does system handle device rotation during call? - Should preserve call state and log data across configuration changes
- What happens when intent extras contain extremely long values? - Should handle reasonable limits and truncate or skip problematic values
- What happens for incoming calls? Or a call started not from ODK? - System must support both outgoing and incoming calls, with distinct direction fields. For incoming calls that are survey calls, since no UUID can be sent during call initiation, the system must collect additional data after call ends including Survey ID (team-defined string for merging decisions), additional notes, and handle interruptions by saving partial data for later completion

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST automatically detect call start and end for all call attempts (answered, unanswered, busy, failed, rejected)
- **FR-002**: System MUST generate structured call log records for every completed call with mandatory fields: callLogId (UUID), direction, callStartUtc, callEndUtc, durationSeconds, outcome, outcomeDetail, deviceId
- **FR-003**: System MUST read all intent extras from ODK Collect launch intent, excluding reserved keys, and include them in Central entity payloads
- **FR-004**: System MUST use dynamic configuration from intent extras: centralBaseUrl, centralProjectId, centralDatasetName, plus call-specific values
- **FR-005**: System MUST implement a PIN-protected admin setup screen for configuring ODK Central credentials
- **FR-006**: System MUST perform API validation before storing credentials and only save if validation succeeds
- **FR-007**: System MUST use HTTP Basic authentication for all ODK Central API calls using stored credentials
- **FR-008**: System MUST store call logs locally using Room database until successfully synced
- **FR-009**: System MUST implement background sync using WorkManager with exponential backoff retry strategy for network and authentication failures, with increasing delays between attempts
- **FR-010**: System MUST mark call logs as synced only when ODK Central confirms entity creation
- **FR-011**: System MUST provide a manual "End call & record" button as fallback for automatic detection failures
- **FR-012**: System MUST handle and store all intent extras except a configurable set of reserved keys defined in admin settings, with default system keys for platform-specific functionality
- **FR-013**: System MUST generate appropriate call outcomes: answered, no_answer, busy, failed, rejected based on call results
- **FR-014**: System MUST support both outgoing and incoming calls with distinct direction fields, and for incoming survey calls, must provide post-call data collection interface for Survey ID, additional notes, and handle interruptions by saving partial data for later completion
- **FR-015**: System MUST provide admin interface for configuring reserved keys that filter intent extras from sync payloads, with default system keys and ability to add custom reserved keys

### Key Entities *(include if feature involves data)*

- **CallLog**: Represents a single call attempt and its outcome, includes generated fields (UUID, timestamps, outcome), direction field, forwarded intent extras, and for incoming survey calls includes Survey ID and additional notes
- **CentralCredentials**: Securely stored ODK Central username and password for Web User authentication
- **PendingSync**: Tracks call logs that need to be synced to Central, includes retry attempts and error information
- **PartialSurveyData**: Stores incomplete survey call information when post-call data collection is interrupted, allowing later completion

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of completed call attempts result in call log records being created locally
- **SC-002**: System successfully syncs 99.5% of eligible call logs to ODK Central on first attempt
- **SC-003**: Background sync completes within 5 minutes of network availability for up to 100 pending logs
- **SC-004**: System handles authentication failures by flagging issues clearly within 1 minute of detection
- **SC-005**: PIN protection effectively prevents unauthorized access to credential management (tested with multiple invalid attempts)
- **SC-006**: Manual fallback mechanism works when automatic detection fails (verified through testing scenarios)
- **SC-007**: No data loss occurs even with app crashes, device restarts, or network interruptions (verified across various failure scenarios)
- **SC-008**: Post-call data collection for incoming survey calls completes successfully within 2 minutes of call end, with 95% completion rate for survey information
