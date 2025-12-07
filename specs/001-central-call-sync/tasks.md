---

description: "Task list for CATI Call Logger with ODK Central Integration implementation"
---

# Tasks: CATI Call Logger with ODK Central Integration

**Input**: Design documents from `/specs/001-central-call-sync/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Mobile Android**: `app/src/main/java/org/fossify/phone/`
- **Android Tests**: `app/src/androidTest/java/org/fossify/phone/`
- **Android Unit Tests**: `app/src/test/java/org/fossify/phone/`
- Paths shown below assume Android project structure - adjust based on plan.md structure

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure for call logging feature

- [X] T001 Add Room dependency to gradle/libs.versions.toml and app/build.gradle.kts
- [X] T002 Add WorkManager dependency to gradle/libs.versions.toml and app/build.gradle.kts
- [X] T003 Add OkHttp and Gson dependencies to gradle/libs.versions.toml and app/build.gradle.kts
- [X] T004 Configure AndroidX Navigation component for admin setup flow
- [X] T005 [P] Update AndroidManifest.xml with required permissions for call detection and background services

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T006 Create Room database class AppDatabase with CallLog, CentralCredentials, PendingSync, PartialSurveyData entities
- [X] T007 Create TypeConverters for UUID, Instant, and JSON serialization in Room database
- [X] T008 [P] Implement DAO interfaces for all entities (CallLogDao, CentralCredentialsDao, PendingSyncDao, PartialSurveyDataDao)
- [X] T009 [P] Create migration scripts for database schema versions
- [X] T010 Setup WorkManager configuration with ExponentialBackoffPolicy.LINEAR
- [X] T011 Configure network constraints and periodic sync request setup
- [X] T012 Create ODK Central API client with OkHttp and authentication handling
- [X] T013 Implement retry interceptor for exponential backoff strategy
- [X] T014 [P] Create utility classes for UUID generation, timestamp handling, and phone number validation
- [X] T015 Setup logging framework for call detection and sync operations

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Automated Call Logging (Priority: P1) 🎯 MVP

**Goal**: System automatically detects call start and end for all call attempts (answered, unanswered, busy, failed, rejected) and generates structured call log records with mandatory fields

**Independent Test**: Can be tested by making various types of calls (answered, no-answer, busy, failed) and verifying that corresponding log records are created with correct timestamps, outcomes, and attempt information

### Implementation for User Story 1

- [X] T016 [P] [US1] Create CallLog Room entity in app/src/main/java/org/fossify/phone/models/CallLog.kt
- [X] T017 [P] [US1] Create CallOutcome enum with answered, no_answer, busy, failed, rejected values
- [X] T018 [US1] Extend CallManager with call outcome detection logic for all call types
- [X] T019 [US1] Add call state monitoring in CallService to detect call start/end events
- [X] T020 [US1] Implement call duration calculation and timestamp recording
- [X] T021 [US1] Add call direction detection (incoming/outgoing) based on call initiation
- [X] T022 [US1] Create CallLogger helper class to generate CallLog records with UUID and mandatory fields
- [X] T023 [US1] Add database insertion logic for CallLog entries in CallLogDao
- [X] T024 [US1] Implement pending sync creation for new call logs in PendingSyncDao
- [X] T025 [US1] Add error handling for call detection failures and duplicate prevention
- [X] T026 [US1] Create unit tests for CallLog entity validation and CallLogger functionality

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Dynamic ODK Central Sync (Priority: P1)

**Goal**: System reads all configuration from ODK Collect intent extras and syncs call logs to the configured ODK Central instance

**Independent Test**: Can be tested by launching the app with different intent extras and verifying that sync attempts use the correct Central URL, project ID, dataset name, and authentication credentials

### Implementation for User Story 2

- [X] T027 [P] [US2] Create CentralCredentials Room entity in app/src/main/java/org/fossify/phone/models/CentralCredentials.kt
- [X] T028 [P] [US2] Create PendingSync Room entity in app/src/main/java/org/fossify/phone/models/PendingSync.kt
- [X] T029 [US2] Create IntentExtrasHelper to read and filter intent extras excluding reserved keys
- [X] T030 [US2] Implement ODK Central API client with dynamic URL construction
- [X] T031 [US2] Create CallSyncManager to handle entity payload creation and API calls
- [X] T032 [US2] Add request/response handling for ODK Central POST /entities endpoint
- [X] T033 [US2] Implement HTTP Basic authentication using stored credentials
- [X] T034 [US2] Add reserved keys filtering logic with configurable prefix "system_"
- [X] T035 [US2] Create OdkSyncWorker WorkManager implementation with exponential backoff
- [X] T036 [US2] Add sync result handling to update CallLog.synced status and PendingSync retry count
- [X] T037 [US2] Create unit tests for intent extras filtering and API client functionality
- [X] T038 [US2] Create integration tests for dynamic configuration and sync workflow

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Secure Credential Management (Priority: P2)

**Goal**: Supervisors can securely configure ODK Central credentials through a PIN-protected admin interface

**Independent Test**: Can be tested by accessing the admin setup screen, entering credentials, performing validation, and verifying that credentials are only stored when validation succeeds

### Implementation for User Story 3

- [X] T039 [P] [US3] Create AdminSetupActivity in app/src/main/kotlin/org/fossify/phone/activities/AdminSetupActivity.kt
- [X] T040 [P] [US3] Create PIN protection dialog and authentication logic
- [X] T041 [P] [US3] Create AdminSettingsHelper for credentials and reserved keys management
- [X] T042 [US3] Implement ODK Central credential validation API call before storage
- [X] T043 [US3] Add PIN creation and PIN validation functionality
- [X] T044 [US3] Create secure password hashing for credential storage
- [X] T045 [US3] Add admin settings navigation entry in MainActivity
- [X] T046 [US3] Implement PIN attempt limiting and security measures
- [X] T047 [US3] Create unit tests for PIN protection and credential validation
- [X] T048 [US3] Create UI tests for admin setup workflow

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: User Story 4 - Background Sync with Reliability (Priority: P2)

**Goal**: System syncs pending call logs to ODK Central in background using WorkManager with proper retry logic

**Independent Test**: Can be tested by simulating network failures and verifying that sync attempts continue in background and retry appropriately

### Implementation for User Story 4

- [X] T049 [P] [US4] Create CallSyncService in app/src/main/java/org/fossify/phone/services/CallSyncService.kt
- [X] T050 [P] [US4] Enhance OdkSyncWorker with network constraints and battery optimization
- [X] T051 [US4] Implement exponential backoff retry logic (10s → 64min, 7 attempts)
- [X] T052 [US4] Add network failure detection and error categorization (network/auth/server)
- [X] T053 [US4] Create sync state management and progress tracking
- [X] T054 [US4] Add notification system for sync failures and authentication errors
- [X] T055 [US4] Implement periodic sync request (24h) with immediate sync triggers
- [X] T056 [US4] Add WorkManager chaining for retry attempts and cleanup
- [X] T057 [US4] Create unit tests for retry logic and sync error handling
- [X] T058 [US4] Create integration tests for background sync workflow

---

## Phase 7: User Story 6 - Incoming Survey Call Data Collection (Priority: P2)

**Goal**: For incoming calls that are survey calls, users can provide additional survey information after the call ends, including Survey ID and notes, with the ability to save partial data if interrupted

**Independent Test**: Can be tested by receiving incoming survey calls, completing post-call data collection, and verifying that survey information is properly stored and synced

### Implementation for User Story 6

- [X] T059 [P] [US6] Create PartialSurveyData Room entity in app/src/main/java/org/fossify/phone/models/PartialSurveyData.kt
- [X] T060 [P] [US6] Create SurveyDataCollectionDialog in app/src/main/java/org/fossify/phone/dialogs/SurveyDataCollectionDialog.kt
- [X] T061 [US6] Extend CallManager to detect incoming survey calls and trigger post-call dialog
- [X] T062 [US6] Implement SurveyDataCollectionDialog with Survey ID and notes fields
- [X] T063 [US6] Add partial data saving mechanism for interruption handling
- [X] T064 [US6] Create SurveyDataCompletionHelper for managing partial survey data
- [X] T065 [US6] Add dialog navigation and activity result handling
- [X] T066 [US6] Implement data persistence for PartialSurveyData with CallLog relationship
- [X] T067 [US6] Add survey data validation and error handling
- [X] T068 [US6] Create unit tests for PartialSurveyData entity and dialog functionality
- [X] T069 [US6] Create UI tests for survey data collection workflow

---

## Phase 8: User Story 7 - Reserved Keys Configuration (Priority: P3)

**Goal**: Supervisors can configure which intent extras are considered reserved keys and should be filtered out from call log data sent to ODK Central

**Independent Test**: Can be tested by adding/removing reserved keys and verifying that the correct extras are included/excluded from sync payloads

### Implementation for User Story 7

- [X] T070 [P] [US7] Add reserved keys configuration UI to AdminSetupActivity
- [X] T071 [P] [US7] Create ReservedKeysConfigDialog for managing custom reserved keys
- [X] T072 [US7] Extend AdminSettingsHelper with reserved keys CRUD operations
- [X] T073 [US7] Update IntentExtrasHelper to use configurable reserved keys list
- [X] T074 [US7] Add reserved keys persistence in SharedPreferences
- [X] T075 [US7] Implement reserved keys validation and default system keys
- [X] T076 [US7] Create unit tests for reserved keys configuration and filtering
- [X] T077 [US7] Create integration tests for reserved keys functionality (`app/src/androidTest/kotlin/org/fossify/phone/ReservedKeysIntegrationTest.kt`)

---

## Phase 9: User Story 5 - Manual Fallback Mechanism (Priority: P3)

**Goal**: Users can manually end calls and create call records if automatic detection fails

**Independent Test**: Can be tested by triggering the manual fallback button and verifying that a call log record is created regardless of call state

### Implementation for User Story 5

- [X] T078 [P] [US5] Add manual "End call & record" button to CallActivity; button anchors near `callEnd` (`app/src/main/res/layout/activity_call.xml`)
- [X] T079 [P] [US5] Create ManualCallRecordHelper for manual call record creation (`app/src/main/kotlin/org/fossify/phone/helpers/ManualCallRecordHelper.kt`)
- [X] T080 [US5] Implement manual call record logic with current call state (CallActivity builds `ManualCallRecordData` and triggers the helper)
- [X] T081 [US5] Add duplicate prevention for manual vs automatic call records (manual helper reuses `CallLogger`’s 2-minute duplicate guard before queueing `PendingSync`)
- [X] T082 [US5] Create manual record confirmation dialog (`app/src/main/kotlin/org/fossify/phone/dialogs/ManualRecordConfirmationDialog.kt`)
- [X] T083 [US5] Add manual record integration with existing sync workflow (`ManualCallRecordHelper` enqueues `PendingSync` via `CallLogger`)
- [X] T084 [US5] Create unit tests for manual record creation and duplicate prevention (`app/src/test/kotlin/org/fossify/phone/helpers/ManualCallRecordHelperTest.kt`)
- [X] T085 [US5] Create UI tests for manual fallback mechanism (`app/src/androidTest/kotlin/org/fossify/phone/ManualRecordConfirmationDialogTest.kt`)

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

 - [X] T086 [P] Update documentation in README with call logging configuration (`README.md`)
 - [X] T087 [P] Create comprehensive test suite covering all user stories (`app/src/test/kotlin/org/fossify/phone/suites/CallLoggingStoriesUnitTestSuite.kt`, `app/src/androidTest/kotlin/org/fossify/phone/suites/CallLoggingStoriesInstrumentedTestSuite.kt`)
 - [X] T088 [P] Add performance monitoring for call detection (<100ms) and sync (<5min) (`app/src/main/kotlin/org/fossify/phone/utils/PerformanceMonitor.kt`, `CallManager.kt`, `app/src/main/kotlin/org/fossify/phone/work/OdkSyncWorker.kt`)
 - [X] T089 [P] Implement battery optimization for background sync (`app/src/main/kotlin/org/fossify/phone/utils/BatteryOptimizer.kt`, `app/src/main/kotlin/org/fossify/phone/work/WorkManagerHelper.kt`)
 - [X] T090 [P] Add error logging and analytics for call detection failures (`app/src/main/kotlin/org/fossify/phone/helpers/CallLogger.kt`, `app/src/main/kotlin/org/fossify/phone/utils/CallDetectionAnalytics.kt`)
 - [X] T091 [P] Create crash handling for database operations and sync failures (`app/src/main/kotlin/org/fossify/phone/utils/CrashTracker.kt`, `app/src/main/kotlin/org/fossify/phone/PhoneApplication.kt`, `app/src/main/java/org/fossify/phone/helpers/CallSyncManager.kt`)
 - [X] T092 [P] Add integration tests following quickstart.md scenarios (`app/src/androidTest/kotlin/org/fossify/phone/quickstart/QuickstartIntegrationTest.kt`)
 - [X] T093 [P] Implement code coverage reports for all unit tests (`app/build.gradle.kts`)
 - [X] T094 [P] Add lint and detekt checks for new code (`app/build.gradle.kts` validateCallSync task)
 - [X] T095 [P] Run quickstart.md validation scenarios/st (`app/src/androidTest/kotlin/org/fossify/phone/quickstart/QuickstartIntegrationTest.kt`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Depends on US1 entities
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - Independent of other stories
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - Depends on US2 API client
- **User Story 6 (P2)**: Can start after Foundational (Phase 2) - Depends on US1 call detection
- **User Story 7 (P3)**: Can start after Foundational (Phase 2) - Depends on US2 extras filtering
- **User Story 5 (P3)**: Can start after Foundational (Phase 2) - Depends on US1 call logging

### Within Each User Story

- Models before services
- Services before UI components
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all models for User Story 1 together:
Task: "Create CallLog Room entity in app/src/main/java/org/fossify/phone/models/CallLog.kt"
Task: "Create CallOutcome enum with answered, no_answer, busy, failed, rejected values"

# Launch all unit tests for User Story 1 together:
Task: "Create unit tests for CallLog entity validation and CallLogger functionality"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Add User Story 4 → Test independently → Deploy/Demo
6. Add User Story 6 → Test independently → Deploy/Demo
7. Add User Story 7 → Test independently → Deploy/Demo
8. Add User Story 5 → Test independently → Deploy/Demo
9. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (P1) - Core call logging
   - Developer B: User Story 2 (P1) - Dynamic sync
   - Developer C: User Story 3 (P2) - Admin interface
3. Once P1 stories complete:
   - Developer A: User Story 4 (P2) - Background sync
   - Developer B: User Story 6 (P2) - Survey data collection
   - Developer C: User Story 7 (P3) - Reserved keys config
4. Final developer: User Story 5 (P3) - Manual fallback
5. Stories complete and integrate independently

---

## Task Summary

- **Total Task Count**: 95 tasks
- **Task Count per User Story**:
  - User Story 1 (P1): 11 tasks
  - User Story 2 (P1): 12 tasks
  - User Story 3 (P2): 10 tasks
  - User Story 4 (P2): 10 tasks
  - User Story 6 (P2): 11 tasks
  - User Story 7 (P3): 8 tasks
  - User Story 5 (P3): 8 tasks
- **Parallel Opportunities**: 38 tasks marked [P] for parallel execution
- **Independent Test Criteria**: Each user story has clear independent test verification steps
- **Suggested MVP Scope**: User Story 1 only (automated call logging with local storage)

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing (if tests included)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- Key dependencies: Foundational phase must complete before any user story work can begin

---

**Generated with [Claude Code](https://claude.com/claude-code)**

Co-Authored-By: Claude <noreply@anthropic.com>
