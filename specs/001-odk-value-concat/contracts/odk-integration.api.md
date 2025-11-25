# ODK Integration API Contracts

**Branch**: `001-odk-value-concat` | **Date**: 2025-11-22

## Overview

This document defines the API contracts for the enhanced ODK integration with value concatenation. The contracts specify the intent-based communication protocol between ODK Collect and Fossify Phone.

## Intent Contracts

### 1. Launch Intent (ODK → Phone)

**Purpose**: Launch Fossify Phone from ODK Collect for call integration.

**Intent Action**:
- Debug: `org.fossify.phone.debug`
- Release: `org.fossify.phone`

**Intent Structure**:
```xml
<intent-filter android:priority="1000">
    <action android:name="org.fossify.phone.debug" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>

<intent-filter android:priority="1000">
    <action android:name="org.fossify.phone" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

**Intent Extras**:
| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `phone` | String | Optional | Phone number to pre-fill in dialpad |
| `value` | String | Optional | Existing call data to be concatenated |

**Intent Example**:
```kotlin
// From ODK Collect to Fossify Phone
val intent = Intent().apply {
    action = "org.fossify.phone.debug"  // or "org.fossify.phone"
    putExtra("phone", "+1234567890")
    putExtra("value", "Previous call data")
    addCategory(Intent.CATEGORY_DEFAULT)
}
```

### 2. Result Intent (Phone → ODK)

**Purpose**: Return call data to ODK Collect when session completes.

**Result Code**: `Activity.RESULT_OK`

**Intent Structure**:
```kotlin
val returnIntent = Intent().apply {
    putExtra("value", concatenatedCallData)
}
setResult(RESULT_OK, returnIntent)
finish()
```

**Result Extra**:
| Key | Type | Description |
|-----|------|-------------|
| `value` | String | Concatenated call data with timestamps |

**Result Example**:
```kotlin
// Existing value + new calls with timestamps
"Previous call data | Out: +1234567890; Duration: 15.25s; Started: 2025-11-22T14:30:15Z | In: +0987654321; Duration: 8.50s; Started: 2025-11-22T14:35:22Z"

// Empty session
""

// Single call with existing value
"Existing data | Out: +1234567890; Duration: 15.25s; Started: 2025-11-22T14:30:15Z"
```

## Data Format Contracts

### 1. Call Data Format

**Purpose**: Standard format for individual call records in concatenated results.

**Format Structure**:
```
"{direction}: {phoneNumber}; Duration: {duration}s; Started: {timestamp}"
```

**Format Components**:
- `direction`: "Out" for outgoing, "In" for incoming
- `phoneNumber`: E.164 formatted phone number
- `duration`: Decimal seconds with 2 precision
- `timestamp`: ISO 8601 formatted timestamp

**Example Formats**:
```
// Outgoing call
"Out: +1234567890; Duration: 15.25s; Started: 2025-11-22T14:30:15Z"

// Incoming call
"In: +0987654321; Duration: 8.50s; Started: 2025-11-22T14:35:22Z"

// Failed call (duration = 0)
"Out: +1234567890; Duration: 0.00s; Started: 2025-11-22T14:30:15Z"
```

### 2. Concatenation Format

**Purpose**: Standard format for combining multiple call records and existing values.

**Separators**:
- Record separator: " | " (pipe with spaces)
- Field separator: "; " (semicolon with space)

**Concatenation Rules**:
1. If both existing value and new calls are empty → return empty string
2. If only one component exists → return that component
3. If both components exist → concatenate with " | " separator

**Concatenation Examples**:
```kotlin
// Multiple calls with existing value
"Previous data | Out: +1234567890; Duration: 15.25s; Started: 2025-11-22T14:30:15Z | In: +0987654321; Duration: 8.50s; Started: 2025-11-22T14:35:22Z"

// Single call, no existing value
"Out: +1234567890; Duration: 15.25s; Started: 2025-11-22T14:30:15Z"

// Existing value only, no new calls
"Previous call data"

// Empty session
""
```

## State Machine Contracts

### 1. ODK Session States

**State Transitions**:
```
INACTIVE → ACTIVE (onCreate with ODK intent)
ACTIVE → COMPLETED (user taps "Finish & Return")
ACTIVE → INACTIVE (session timeout or error)
ACTIVE → ERROR (unexpected state corruption)
COMPLETED → INACTIVE (after result returned)
```

**State Definitions**:
- `INACTIVE`: No active ODK session
- `ACTIVE`: Session in progress, tracking calls
- `COMPLETED`: Session finished, data returned
- `ERROR`: Session encountered unrecoverable error

### 2. Call Tracking States

**Call State Transitions**:
```
IDLE → DIALING (call initiation)
DIALING → CONNECTING (call establishment)
CONNECTING → ACTIVE (call connected)
ACTIVE → DISCONNECTING (call ending)
DISCONNECTING → DISCONNECTED (call ended)
DIALING → DISCONNECTED (failed call)
CONNECTING → DISCONNECTED (failed call)
```

**Tracking Events**:
- `onCallStarted`: When call enters DIALING state
- `onCallActive`: When call enters ACTIVE state
- `onCallEnded`: When call enters DISCONNECTED state

## Error Handling Contracts

### 1. Error Types

**Intent Processing Errors**:
- `INVALID_INTENT`: Intent missing required action or extras
- `DUPLICATE_SESSION`: Attempt to start session when already active
- `VARIANT_MISMATCH`: Intent action doesn't match build variant

**Session Management Errors**:
- `SESSION_TIMEOUT`: Session inactive for extended period
- `STATE_CORRUPTION`: Session state inconsistent
- `DATA_LOSS`: Call data lost during transition

**Call Tracking Errors**:
- `CALL_NOT_FOUND`: Call object not found in tracking
- `INVALID_STATE`: Impossible state transition detected
- `TIMESTAMP_ERROR`: Invalid timestamp values

### 2. Error Recovery

**Error Recovery Strategies**:
1. **Graceful Degradation**: Switch to normal dialer mode
2. **Best-Effort Preservation**: Save available data
3. **User Notification**: Inform user of errors
4. **Session Cleanup**: Reset session state on unrecoverable errors

**Error Response Format**:
```kotlin
// For critical errors, return empty string to ODK
val returnIntent = Intent().apply {
    putExtra("value", "")
}
setResult(RESULT_OK, returnIntent)
finish()
```

## Timing Contracts

### 1. Timestamp Recording

**Recording Points**:
- **Dial Time**: When call enters DIALING state (`onCallStarted`)
- **Connect Time**: When call enters ACTIVE state (`onCallActive`)
- **End Time**: When call enters DISCONNECTED state (`onCallEnded`)

**Timestamp Format**: ISO 8601 UTC
```
2025-11-22T14:30:15Z
```

### 2. Timing Accuracy Requirements

**Accuracy Targets**:
- Dial time recording: < 100ms after state change
- Connect time recording: < 100ms after state change
- Duration calculation: Precise to 0.01s

## Performance Contracts

### 1. Response Time Targets

**Performance Requirements**:
- Intent processing: < 50ms
- Call state tracking: < 10ms per update
- Data concatenation: < 100ms for 100 calls
- Session persistence: < 20ms for save/restore

### 2. Memory Usage Targets

**Memory Limits**:
- Active session tracking: < 1MB
- Call history per session: < 100 calls
- State persistence: < 500KB

## Security Contracts

### 1. Data Privacy

**Privacy Requirements**:
- No call content storage (only metadata)
- Temporary data cleared after session completion
- No unnecessary permissions beyond call functionality

### 2. Intent Security

**Security Measures**:
- Intent action validation
- Extra value sanitization
- Build variant verification

## Build Variant Contracts

### 1. Variant-Specific Behaviors

**Debug Variant (`org.fossify.phone.debug`)**:
- Used during development and testing
- Enhanced logging and error reporting
- May include additional debugging features

**Release Variant (`org.fossify.phone`)**:
- Used in production builds
- Optimized performance and logging
- Minimal debugging overhead

### 2. Intent Filter Priority

**Priority Requirements**:
- ODK intent filters: priority = 1000
- Other intent filters: priority ≤ 999

## Testing Contracts

### 1. Test Scenarios

**Required Test Cases**:
1. **Value Concatenation**: Existing value + new calls
2. **Empty Session**: No calls made during session
3. **Failed Calls**: Calls that don't connect
4. **Session Persistence**: Configuration changes and restarts
5. **Error Handling**: Invalid intents and state corruption

### 2. Expected Results

**Test Validation Criteria**:
- Correct formatting of call data with timestamps
- Proper concatenation with existing values
- Empty string returned for empty sessions
- All call attempts tracked (successful and failed)
- Session state preserved across lifecycle events

This API contract provides a comprehensive specification for the enhanced ODK integration, ensuring reliable communication between ODK Collect and Fossify Phone while maintaining compatibility with existing functionality.