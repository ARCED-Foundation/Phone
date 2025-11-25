# Actionable Tasks: ODK Call Data Field Auto-Return

**Branch**: `001-odk-field-return`
**Generated**: 2025-11-25 from [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

## Dependencies (User Story Order)
- **Phase 3 (US1)** → Phase 4 (US2)  *(US1 auto-return builds session data for US2)*
- **Phase 5 (US3)** → Independent *(leverages US2 auto-save)*
- **Phase 6 (US4)** → Independent *(low-prio, ODK-side)*

**Parallel Opportunities**:
- All model/extension tasks [P] (separate files)
- US1/US2 implementation [P] in different classes (DialpadActivity vs CallManager)

## Implementation Strategy
**MVP Scope**: Phase 1-4 (US1+US2: core auto-return/save). Deliver testable increment: ODK launch → connect → return; disconnect → data save.
**Incremental**: US3 (manual), US4 (block). Tests in Polish.
**Execution**: /speckit.implement processes phases sequentially.

## Phase 1: Setup
**Goal**: Initialize development environment.
**Independent Test**: `./gradlew test` passes baseline; on correct branch.

- [ ] T001 Switch to feature branch `001-odk-field-return`
- [ ] T002 [P] Run `./gradlew clean test` to verify baseline

## Phase 2: Foundational
**Goal**: Extend models and capture launch data (blocks all stories).
**Independent Test**: ODKSession serializes with new fields; intent extras parsed in DialpadActivity.

- [ ] T003 Extend ODKSession data class in `app/src/main/kotlin/org/fossify/phone/models/Call.kt` with `fieldId: String?` and `callingPackage: String?`
- [ ] T004 [P] Update `saveOdkSession`/`loadOdkSession` in `app/src/main/kotlin/org/fossify/phone/extensions/Context.kt` to persist new fields
- [ ] T005 Update `DialpadActivity.kt` (~line 550) to capture `intent.getStringExtra(\"odk_field_id\")` and `packageManager.getCallingPackage()` then pass to `CallManager.initializeOdkSession(...)`

## Phase 3: User Story 1 (P1) - Auto-return on Connect
**Goal**: Auto-return to ODK form on first call connect.
**Independent Test**: Mock ODK intent → simulate Telecom ACTIVE → verify `setResult(RESULT_OK, partial=true)` called <1s.

- [ ] T006 Register CallManager listener in `DialpadActivity.kt` for `notifyCallActiveEvents`
- [ ] T007 [P] In `DialpadActivity.kt`, on first `notifyCallActiveEvents`: call `returnToOdk(true)` with partial data
- [ ] T008 [P] Update `CallManager.kt` `notifyCallActiveEvents` to support partial payload (connected calls only)

## Phase 4: User Story 2 (P1) - Auto-save on Disconnect
**Goal**: Auto-populate data to original field on any disconnect.
**Independent Test**: Simulate disconnect (any cause) → verify `setResult` extras (`value`, `total_duration`, `successful_calls`, `records` JSON, `field_id`).

- [ ] T009 In `CallManager.kt` `notifyCallEndedEvents`: if no active calls and autoReturnDisconnect, trigger `returnToOdk(false)` via broadcast/PendingIntent to DialpadActivity
- [ ] T010 [P] Extend `DialpadActivity.kt` `returnToOdk(false)`: build full CallDataPayload extras (concat value, totals, JSON records, fieldId echo)
- [ ] T011 [P] Handle background disconnects: if no Activity context, store pendingIntent/callingPackage in session for later dispatch

## Phase 5: User Story 3 (P2) - Manual App Access
**Goal**: Support switch back to app during call → manual end → auto-save.
**Independent Test**: Background app during call → foreground → end → data saves correctly.

- [ ] T012 Ensure existing foreground handling in `DialpadActivity.kt` triggers US2 auto-save on manual end

## Phase 6: User Story 4 (P3) - Form Submit Block
**Goal**: Block ODK submit while active (app-side signal).
**Independent Test**: During call, setResult extra signals invalid; post-disconnect valid.

- [ ] T013 [P] Add `form_valid: Boolean` extra to CallDataPayload in `DialpadActivity.kt` returnToOdk (false during active, true post-end)
- [ ] T014 Document ODK-side handling in `quickstart.md` (out-of-scope for app)

## Polish & Cross-Cutting
**Goal**: Tests, validation, cleanup.
**Independent Test**: `./gradlew test`; manual adb ODK mock verifies full flows.

- [ ] T015 Add unit tests for CallManager Telecom mocks in `app/src/test/kotlin/.../CallManagerTest.kt`
- [ ] T016 [P] Add integration tests: mock ODK intent → verify setResult extras in instrumentation tests
- [ ] T017 Run `./gradlew test` and fix failures
- [ ] T018 Manual validation: `adb shell am start -a org.fossify.phone -e phoneNumber +123 --eu odk_field_id testfield`
- [ ] T019 Update README/CLAUDE.md with new ODK features