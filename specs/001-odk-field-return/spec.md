# Feature Specification: ODK Call Data Field Auto-Return

**Feature Branch**: `001-odk-field-return`
**Created**: 2025-11-25
**Status**: Draft
**Input**: User description for phone survey ODK integration: auto-return to form on call connect, auto-save call data to launching field on disconnect (any cause), support manual app access for disconnect, ensure return to original field despite navigation, low-prio form submit block.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Auto-return to ODK form on call connection (Priority: P1)

Enumerator launches dialer app from specific ODK Collect form field to initiate survey call. Once the call successfully connects, the app automatically returns control to the ODK Collect form so the enumerator can fill survey responses during the call.

**Why this priority**: Core to survey workflow efficiency; enables hands-free form filling post-connection, preventing manual navigation delays.

**Independent Test**: Launch app from ODK field, connect mock call, verify automatic return to ODK form; delivers seamless workflow transition.

**Acceptance Scenarios**:

1. **Given** app launched from ODK field via intent, **When** outgoing call connects successfully, **Then** app immediately returns to ODK form (no user action needed).
2. **Given** app launched from ODK field, **When** call connects after ringing, **Then** enumerator sees ODK form resumed at launching point.

---

### User Story 2 - Auto-save call data to original ODK field on disconnect (Priority: P1)

Regardless of disconnect cause (enumerator ends call, remote disconnects, network failure), when call ends, app automatically populates call data (status, duration, timestamps, number) back into the exact ODK field from which it was launched, even if enumerator navigated to other fields.

**Why this priority**: Critical data integrity for surveys; ensures no data loss despite workflow interruptions or navigation.

**Independent Test**: Launch from ODK field, simulate call connect/disconnect, verify data populates correct field; preserves survey data reliably.

**Acceptance Scenarios**:

1. **Given** call from ODK-launched app ends (any reason), **When** disconnect detected, **Then** call data auto-saves to launching ODK field within 2 seconds.
2. **Given** enumerator navigates to different ODK fields post-launch, **When** call disconnects, **Then** data still returns to original launching field.

---

### User Story 3 - Manual access to app for call control from ODK (Priority: P2)

During active call, enumerator can switch back to dialer app from ODK form to manually end call; upon end, data auto-saves to original field.

**Why this priority**: Provides flexibility for urgent control needs while maintaining data flow.

**Independent Test**: Launch call, switch to ODK, return to app and end call, verify data save; supports flexible workflows.

**Acceptance Scenarios**:

1. **Given** active call from ODK launch, **When** enumerator switches to app and ends call, **Then** data auto-saves to original field.
2. **Given** app in background during call, **When** brought to foreground and disconnected, **Then** return data flow works.

---

### User Story 4 - Block ODK form submission until call disconnected (Priority: P3)

Prevent enumerator from submitting ODK form while call is active.

**Why this priority**: Low priority per user; prevents incomplete data submission but secondary to core flows.

**Independent Test**: Attempt form submit during call, verify blocked; ensures data completeness.

**Acceptance Scenarios**:

1. **Given** active call from ODK field, **When** enumerator tries to submit form, **Then** submission blocked with clear message.
2. **Given** call disconnected, **When** submit attempted, **Then** submission allowed.

---

### Edge Cases

- What happens when app launched but call fails to connect? Data saved as 'failed' with timestamp.
- How does system handle multiple concurrent ODK launches? Assume single active instance; latest overrides.
- Network loss mid-call? Save partial data (attempted connect, no duration).
- App killed during call? Save available data on restart/service end.
- ODK field type mismatch? Fallback to string representation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST capture unique identifier of launching ODK field on app start.
- **FR-002**: System MUST detect call connection status and auto-return to ODK form immediately upon connection.
- **FR-003**: System MUST populate call data (phone number, status: connected/disconnected/failed, start/end timestamps, duration) to original ODK field on any disconnect event.
- **FR-004**: System MUST preserve original field targeting despite ODK internal navigation post-launch.
- **FR-005**: System MUST allow app access during call for manual disconnect with auto-data return.
- **FR-006**: System MUST block ODK form submission while call active (low priority).
- **FR-007**: System MUST handle edge disconnects (network, app switch, kill) with partial data save.

### Key Entities *(include if feature involves data)*

- **ODK Launch Context**: Unique identifier of originating form field, passed via launch intent; used for targeted data return.
- **Call Data Payload**: Structured data including number, status, timestamps, duration; formatted for ODK field consumption.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of connected calls auto-return enumerator to ODK form within 1 second.
- **SC-002**: 100% of disconnected calls populate data in correct original field within 2 seconds.
- **SC-003**: Enumerators complete survey form filling during 95% of calls without manual navigation.
- **SC-004**: Zero data loss from navigation away in ODK (tested across 50 simulated navigations).
- **SC-005**: Manual disconnect from app returns data correctly in 100% of tests.
