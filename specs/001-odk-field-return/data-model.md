# Data Model: ODK Field Auto-Return

**Date**: 2025-11-25

## Entities

### ODKSession (Extended)
**Purpose**: Tracks complete ODK integration session across app restarts.

**Fields**:
- `phoneNumber: String?` - Dialed number from ODK intent
- `existingValue: String?` - Pre-existing field value for concatenation
- `startTime: Long` - Session start timestamp
- `variant: String?` - Build variant (debug/release)
- `fieldId: String?` *(NEW)* - Unique ODK field identifier (from intent extra)
- `callingPackage: String?` *(NEW)* - Launching package (ODK Collect) for result targeting
- `callRecords: List<OdkCallRecord>` - Accumulated call data
- `activeOdkCallTracking: Map<String, OdkCallTrackingInfo>` - Live calls

**Validation**:
- fieldId non-empty if auto-return enabled
- callRecords immutable post-session end

### OdkCallRecord
**Purpose**: Single call metadata for concatenation.

**Fields** (existing + extensions):
- `callId: String`
- `direction: CallDirection` (INCOMING/OUTGOING)
- `phoneNumber: String`
- `duration: Double` (seconds, 0 for failed)
- `startTime: Long`, `connectTime: Long?`, `endTime: Long`
- `wasSuccessful: Boolean`
- `formattedTimestamp: String` (ISO8601)

**Relationships**: List in ODKSession; used for concat string.

### CallDataPayload (Intent Extras)
**Purpose**: Data returned to ODK via setResult Intent.

**Fields**:
- `value: String` - Legacy concatenated string
- `totalDuration: Double` *(NEW)*
- `successfulCalls: Int` *(NEW)*
- `records: String` *(NEW)* - JSON-serialized List<OdkCallRecord>
- `fieldId: String?` *(NEW)* - Target field confirmation

## State Transitions

```
ODK Launch Intent → ODKSession init (pending)
↓ (Telecom DIALING)
Active Call Track → callRecords pending
↓ (ACTIVE)
Auto-Return Trigger → Partial Payload to ODK
↓ (DISCONNECTED, all active=0)
Full Payload to ODK → Session End
```

**Edge States**: Failed connect (duration=0), App kill (restore from SharedPrefs → end on load if stale).
