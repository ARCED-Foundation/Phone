# Research Summary: ODK Field Auto-Return Extension

**Date**: 2025-11-25

## Key Findings from Codebase Exploration

**Existing ODK Integration (Robust Baseline)**:
- DialpadActivity.kt: Handles ODK intents (`org.fossify.phone`/`debug`), validates, inits CallManager session, shows UI, manual return via setResult(RESULT_OK, Intent(\"value\"=concat)).
- CallManager.kt: Tracks ODKSession (phoneNumber, existingValue, callRecords: List<OdkCallRecord>), activeOdkCallTracking Map. Listens Telecom states: DIALING→start, ACTIVE→connectTime/success, DISCONNECTED→end/duration/record. Persists JSON to SharedPrefs. getConcatenatedOdkValue() formats \"existing | Out: num; Duration: Xs; Started: ISO | ...\".
- Models: OdkCallRecord (id, direction, num, duration, timestamps, success).
- Persistence survives restarts.

**Android Result Return Mechanisms**:
- Decision: Use Activity.setResult(RESULT_OK, Intent extras) from DialpadActivity (standard for launched activities). For background disconnects: Store callingPackage/fieldId in session; on end, if no Activity, use PendingIntent or broadcast (but setResult requires context).
- Rationale: Matches current manual flow; ODK expects \"value\" extra. Extend to auto-trigger.
- Alternatives: BroadcastReceiver (less reliable); ContentProvider (overkill).

**Auto-Return on Connect**:
- Trigger: CallManager.notifyCallActiveEvents() (first ACTIVE). But needs Activity context → Register listener in DialpadActivity relaying to CallManager.
- Partial data: Include connected calls only (ongoing duration=0 or live timer?).

**Field-Specific Return**:
- Capture: intent.getStringExtra(\"odk_field_id\") or callingPackage+formInstanceId.
- Despite navigation: ODK handles field targeting internally if same RESULT_OK instance; store field_id in session.
- Multi-field: Assume single field per launch; extras can have multiple (value, duration, records_json).

**Best Practices**:
- Intents: getCallingPackage(), referringIntent.
- State: ForegroundService for reliability (already CallService).
- Testing: Robolectric for unit (mock Telecom), Instrumentation for e2e ODK mocks.

**Resolved Clarifications**: None needed (existing patterns perfect fit). No unknowns.

**Sources**: DialpadActivity.kt, CallManager.kt, CLAUDE.md, Android docs (Telecom, Activity results).
