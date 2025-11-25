---

description: "Task list for Enhanced ODK Integration with Value Concatenation feature"
---

# Tasks: Enhanced ODK Integration with Value Concatenation

**Input**: Design documents from `/specs/001-odk-value-concat/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Android project**: `org/fossify.phone/` at repository root
- **Activities**: `activities/`
- **Services**: `services/`
- **Helpers**: `helpers/`
- **Models**: `models/`
- **Extensions**: `extensions/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure for ODK integration enhancement

- [ ] T001 Create enhanced session management constants in org/fossify/phone/extensions/Call.kt
- [ ] T002 [P] Setup ODK session SharedPreferences utilities in org/fossify/phone/extensions/Context.kt
- [ ] T003 [P] Create timestamp formatting utilities in org/fossify/phone/extensions/Call.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 [P] Create enhanced OdkCallTrackingInfo data class in org/fossify/phone/models/Call.kt
- [ ] T005 [P] Create OdkCallRecord data class in org/fossify/phone/models/Call.kt
- [ ] T006 [P] Create ODKSession data class in org/fossify/phone/models/Call.kt
- [ ] T007 [P] Create SessionState data class with enums in org/fossify/phone/models/Call.kt
- [ ] T008 [P] Create ConcatenatedValue data class in org/fossify/phone/models/Call.kt
- [ ] T009 [P] Setup JSON serialization utilities for ODK session data in org/fossify/phone/extensions/Context.kt
- [ ] T010 Update CallManager to include enhanced call tracking methods in org/fossify/phone/helpers/CallManager.kt
- [ ] T011 [P] Create ODK intent validation utilities in org/fossify/phone/extensions/Intent.kt

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Value Concatenation During ODK Session (Priority: P1) 🎯 MVP

**Goal**: Enable ODK Collect to accumulate multiple calls during a single session with proper value concatenation using pipe separators

**Independent Test**: Launch the phone app from ODK, make multiple calls, and verify the concatenated result with timestamps is returned correctly

### Tests for User Story 1 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T012 [P] [US1] Unit test for value concatenation logic in org/fossify/phone/tests/CallManagerTest.kt
- [ ] T013 [P] [US1] Integration test for ODK session lifecycle in org/fossify/phone/tests/DialpadActivityTest.kt

### Implementation for User Story 1

- [x] T014 [P] [US1] Implement enhanced onCallStarted method with dial timestamp recording in org/fossify/phone/helpers/CallManager.kt
- [x] T015 [P] [US1] Implement call data formatting utility with timestamps in org/fossify/phone/extensions/Call.kt
- [x] T016 [P] [US1] Implement value concatenation logic in org/fossify/phone/helpers/CallManager.kt
- [x] T017 [P] [US1] Update DialpadActivity to handle ODK intent extras and session management in org/fossify/phone/activities/DialpadActivity.kt
- [x] T018 [P] [US1] Implement session state persistence with SavedStateHandle in org/fossify/phone/activities/DialpadActivity.kt
- [x] T019 [US1] Implement "Finish & Return" button functionality with value concatenation in org/fossify/phone/activities/DialpadActivity.kt
- [x] T020 [US1] Update setResult logic to return concatenated values with timestamps in org/fossify/phone/activities/DialpadActivity.kt

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Empty Session Handling (Priority: P2)

**Goal**: Properly handle sessions where no calls are made, returning empty values instead of "No calls made" and preserving original ODK values

**Independent Test**: Launch the phone app from ODK without making any calls and verify an empty result is returned instead of "No calls made"

### Tests for User Story 2 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T021 [P] [US2] Unit test for empty session logic in org/fossify/phone/tests/CallManagerTest.kt
- [ ] T022 [P] [US2] Integration test for session preservation scenarios in org/fossify/phone/tests/DialpadActivityTest.kt

### Implementation for User Story 2

- [ ] T023 [P] [US2] Implement empty session detection logic in org/fossify/phone/helpers/CallManager.kt
- [ ] T024 [P] [US2] Implement empty value return logic in org/fossify/phone/helpers/CallManager.kt
- [ ] T025 [US2] Update DialpadActivity to handle empty session scenarios in org/fossify/phone/activities/DialpadActivity.kt
- [ ] T026 [US2] Implement original value preservation logic in org/fossify/phone/activities/DialpadActivity.kt
- [ ] T027 [US2] Update UI indicators to handle empty sessions appropriately in org/fossify/phone/activities/DialpadActivity.kt

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Timestamp Accuracy (Priority: P2)

**Goal**: Ensure all calls initiated from ODK Collect include accurate timestamps reflecting call start time (dial time), not connection time

**Independent Test**: Make calls and verify the timestamps accurately reflect when calls were initiated, not when they connected or ended

### Tests for User Story 3 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T028 [P] [US3] Unit test for timestamp recording accuracy in org/fossify/phone/tests/CallManagerTest.kt
- [ ] T029 [P] [US3] Integration test for rapid successive call timestamps in org/fossify/phone/tests/DialpadActivityTest.kt

### Implementation for User Story 3

- [ ] T030 [P] [US3] Implement dual timing approach in org/fossify/phone/helpers/CallManager.kt
- [ ] T031 [P] [US3] Implement dial-based timestamp recording in org/fossify/phone/helpers/CallManager.kt
- [ ] T032 [P] [US3] Implement connection-based duration calculation in org/fossify/phone/helpers/CallManager.kt
- [ ] T033 [P] [US3] Update call state transition tracking for accurate timing in org/fossify/phone/helpers/CallManager.kt
- [ ] T034 [US3] Implement timestamp formatting utilities in org/fossify/phone/extensions/Call.kt
- [ ] T035 [US3] Update failed call timestamp handling in org/fossify/phone/helpers/CallManager.kt
- [ ] T036 [US3] Add timestamp accuracy validation in org/fossify/phone/helpers/CallManager.kt

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T037 [P] Update ODK integration documentation in CLAUDE.md
- [ ] T038 [P] Add comprehensive error handling for ODK session edge cases in org/fossify/phone/activities/DialpadActivity.kt
- [ ] T039 [P] Add robust state management for app lifecycle events in org/fossify/phone/activities/DialpadActivity.kt
- [ ] T040 [P] Add performance optimization for large call sessions in org/fossify/phone/helpers/CallManager.kt
- [ ] T041 [P] Add configuration change resilience in org/fossify/phone/activities/DialpadActivity.kt
- [ ] T042 [P] Add memory leak prevention for long sessions in org/fossify/phone/activities/DialpadActivity.kt
- [ ] T043 Run comprehensive integration testing of all ODK workflows
- [ ] T044 Validate compatibility across all build variants (core, foss, gplay)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
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
# Launch all tests for User Story 1 together (if tests requested):
Task: "Unit test for value concatenation logic in org/fossify/phone/tests/CallManagerTest.kt"
Task: "Integration test for ODK session lifecycle in org/fossify/phone/tests/DialpadActivityTest.kt"

# Launch all foundational models for User Story 1 together:
Task: "Create enhanced OdkCallTrackingInfo data class in org/fossify/phone/models/Call.kt"
Task: "Create OdkCallRecord data class in org/fossify/phone/models/Call.kt"
Task: "Create ODKSession data class in org/fossify/phone/models/Call.kt"
Task: "Create SessionState data class with enums in org/fossify/phone/models/Call.kt"
Task: "Create ConcatenatedValue data class in org/fossify/phone/models/Call.kt"

# Launch all implementation tasks for User Story 1 together:
Task: "Implement enhanced onCallStarted method with dial timestamp recording in org/fossify/phone/helpers/CallManager.kt"
Task: "Implement call data formatting utility with timestamps in org/fossify/phone/extensions/Call.kt"
Task: "Implement value concatenation logic in org/fossify/phone/helpers/CallManager.kt"
Task: "Update DialpadActivity to handle ODK intent extras and session management in org/fossify/phone/activities/DialpadActivity.kt"
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
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Summary

- **Total Task Count**: 44 tasks
- **Task Count per User Story**:
  - User Story 1 (P1): 9 tasks
  - User Story 2 (P2): 5 tasks
  - User Story 3 (P2): 7 tasks
- **Parallel Opportunities Identified**: 22 tasks marked [P]
- **Independent Test Criteria for Each Story**:
  - US1: Launch from ODK, make multiple calls, verify concatenated result
  - US2: Launch from ODK without calls, verify empty result
  - US3: Make calls, verify accurate dial timestamps
- **Suggested MVP Scope**: User Story 1 only (T001-T020)
- **Format Validation**: ALL tasks follow the checklist format (checkbox, ID, labels, file paths)