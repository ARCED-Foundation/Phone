# Feature Specification: Enhanced ODK Integration with Value Concatenation

**Feature Branch**: `001-odk-value-concat`
**Created**: 2025-11-22
**Status**: Draft
**Input**: User description: "I want the app to not replace the existing value coming from the ODK Collect, instead wheverever a call is initiated from ODK collect, it should take the existing value and the new value should concat with a pipe. "No calls made" is not needed anymore, instead if there is no call the value can be empty, otherwise existing values can be concaniated. Additionally, the make sure the values include timestamp the call started. The purpose of this feature is that no calls that is initiated from ODK Collect, successfully connected or not, data with timestamp is logged. Make sure the other functionalities are intact."

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

### User Story 1 - Value Concatenation During ODK Session (Priority: P1)

ODK Collect initiates multiple calls during a single session and expects all call data to be accumulated with proper timestamps.

**Why this priority**: This is the core functionality that enables ODK to track all call attempts made during data collection workflows, providing comprehensive call history for analysis.

**Independent Test**: Can be fully tested by launching the phone app from ODK, making multiple calls, and verifying the concatenated result with timestamps is returned correctly.

**Acceptance Scenarios**:

1. **Given** ODK Collect launches Fossify Phone with an existing value, **When** user makes one call during the session, **Then** the returned value contains both existing and new call data separated by pipe with timestamps

2. **Given** ODK Collect launches Fossify Phone with an existing value, **When** user makes multiple calls during the session, **Then** all call data is concatenated with pipe separators and individual timestamps

---

### User Story 2 - Empty Session Handling (Priority: P2)

ODK Collect launches Fossify Phone but no calls are made during the session.

**Why this priority**: Ensures proper handling of edge cases where the session is initiated but no calls occur, maintaining data integrity.

**Independent Test**: Can be tested by launching the phone app from ODK without making any calls and verifying an empty result is returned instead of "No calls made".

**Acceptance Scenarios**:

1. **Given** ODK Collect launches Fossify Phone, **When** no calls are made during the session, **Then** the returned value is empty (not "No calls made")

2. **Given** ODK Collect launches Fossify Phone with existing value, **When** no calls are made during the session, **Then** the original value is preserved unchanged

---

### User Story 3 - Timestamp Accuracy (Priority: P2)

All calls initiated from ODK Collect include accurate timestamps for when calls started.

**Why this priority**: Timestamps are critical for ODK data analysis, providing temporal context for call attempts and enabling proper workflow tracking.

**Independent Test**: Can be tested by making calls and verifying the timestamps accurately reflect when calls were initiated, not when they connected or ended.

**Acceptance Scenarios**:

1. **Given** ODK Collect launches Fossify Phone, **When** a call is initiated, **Then** the timestamp reflects the call start time (dial time) not connection time

2. **Given** multiple calls are made in quick succession, **When** timestamps are recorded, **Then** each call has a unique and chronologically accurate timestamp

---

### Edge Cases

- What happens when the app is force closed during an ODK session?
- How does the system handle calls that are initiated but never connect (failed dial attempts)?
- What happens when the ODK session times out due to inactivity?
- How does the system handle rapid successive call initiations?
- What occurs when the phone loses connectivity during an ODK session?
- How are call attempts from normal usage (non-ODK) handled during an ODK session?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST preserve existing values from ODK Collect when initiating a new session instead of replacing them
- **FR-002**: System MUST concatenate new call data with existing values using pipe separators (`|`)
- **FR-003**: System MUST include timestamp for each call that reflects the call start time (dial time), not connection time
- **FR-004**: System MUST return empty value when no calls are made during ODK session (no "No calls made" message)
- **FR-005**: System MUST preserve original ODK value when no calls are made during session
- **FR-006**: System MUST track all calls initiated from ODK Collect regardless of connection success or failure
- **FR-007**: System MUST maintain all existing ODK integration functionality (session management, visual indicators, etc.)
- **FR-008**: System MUST format call data consistently with direction, phone number, duration, and timestamp
- **FR-009**: System MUST handle concurrent calls during ODK sessions independently
- **FR-010**: System MUST maintain data accuracy across app lifecycle events (rotation, background, etc.)

### Key Entities

- **ODKSession**: Represents an active integration session with ODK Collect, containing session state, existing values, and call tracking data
- **CallRecord**: Represents individual call attempts made during ODK session, including direction, phone number, duration, and timestamp
- **ConcatenatedValue**: The final formatted string returned to ODK Collect, combining existing values and new call records separated by pipes
- **SessionState**: Manages the lifecycle and state of ODK integration sessions

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: ODK Collect can initiate multiple calls during a single session and receive all call data concatenated with pipe separators within 5 seconds of session completion
- **SC-002**: System accurately records call timestamps within 1 second of call initiation time (dial time)
- **SC-003**: Empty sessions return empty values instead of "No calls made" message 100% of the time
- **SC-004**: Original ODK values are preserved unchanged when no calls are made during session 100% of the time
- **SC-005**: All call attempts (successful and failed) are tracked and logged during ODK sessions 100% of the time
- **SC-006**: Existing ODK integration functionality (visual indicators, session management, error handling) remains fully operational with no regressions
- **SC-007**: Call data format consistency maintained across all session scenarios with accurate direction, number, duration, and timestamp information
- **SC-008**: System handles concurrent call tracking independently without data corruption or loss
- **SC-009**: App lifecycle events (rotation, background/foreground transitions) maintain data integrity 100% of the time
