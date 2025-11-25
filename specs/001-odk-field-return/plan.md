# Implementation Plan: ODK Call Data Field Auto-Return

**Branch**: `001-odk-field-return` | **Date**: 2025-11-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-odk-field-return/spec.md`

## Summary

Extend existing ODK Collect integration in DialpadActivity.kt and CallManager.kt to: (1) auto-return to ODK form on first call connect via setResult, (2) auto-populate call data to original launching field on any disconnect (using captured calling package/field ID), supporting navigation away, manual app access, and low-prio form submit block. Leverage current session persistence, concat logic, and Telecom state tracking.

## Technical Context

**Language/Version**: Kotlin 2.2.21
**Primary Dependencies**: Android Telecom framework, Fossify Commons (UI/themes), kotlinx.serialization.json (ODK persistence), EventBus (state updates), libphonenumber (formatting)
**Storage**: SharedPreferences (Base64+JSON for ODKSession persistence across restarts)
**Testing**: ./gradlew testGplayDebugUnitTest, connectedAndroidTest (add integration tests for ODK flows)
**Target Platform**: Android 8.0+ (minSDK=26, targetSDK=36), all variants (core/foss/gplay)
**Project Type**: Android mobile app
**Performance Goals**: Call state detection and result return <1s; no UI blocking
**Constraints**: Offline-capable, privacy-first (no extra data collection), multi-SIM support, concurrent calls independent
**Scale/Scope**: Single feature extension (~5-10 files), 1k LOC impact

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Privacy-First Design Compliance
- [x] Integration features respect user privacy principles
- [x] No unnecessary data collection beyond what's required
- [x] Proper permission handling and user consent mechanisms

### Seamless External Integration Compliance
- [x] Uses standard Android intent protocols
- [x] Well-defined data return mechanisms implemented
- [x] Graceful error handling for integration workflows

### Metadata Accuracy Compliance
- [x] Call data tracking is precise and reliable
- [x] Connection-based timing (not dial-based) implemented
- [x] Concurrent call independence maintained

### Build Variant Compatibility Compliance
- [x] Functions across all build variants (core, foss, gplay)
- [x] Debug and release intent filters both supported
- [x] No hardcoded variant-specific limitations

### User Experience Clarity Compliance
- [x] Clear visual indicators for integration sessions
- [x] Intuitive session exit mechanisms provided
- [x] Proper state persistence and user confirmation

### Integration Standards Compliance
- [x] Follows standard Android intent patterns
- [x] Integration points maintainable and documented
- [x] Session lifecycle clearly defined

### Testing Requirements Compliance
- [x] Integration workflows have end-to-end tests
- [x] Call metadata accuracy validation implemented
- [x] Build variant compatibility verified
- [x] Error scenarios comprehensively tested

## Project Structure

### Documentation (this feature)

```
specs/001-odk-field-return/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```
app/src/main/kotlin/org/fossify/phone/
├── activities/
│   ├── DialpadActivity.kt     # ODK entry, add auto-return logic
│   └── DialerActivity.kt      # Fallback outgoing calls
├── helpers/
│   └── CallManager.kt         # Core: extend notifyCallActiveEvents, notifyCallEndedEvents for auto-result
├── models/
│   └── Call.kt                # ODKSession, OdkCallRecord, add fieldId to session
├── extensions/
│   ├── Context.kt             # save/loadOdkSession, add callingPackage/fieldId
│   └── Call.kt                # Formatting
├── services/
│   └── CallService.kt         # Telecom events → CallManager
└── res/                      # ODK strings, layouts (indicators)

tests/                        # Unit: CallManager, Integration: ODK mock intents
```

**Structure Decision**: Extend existing activities/helpers/models per Fossify Phone architecture (layered: activities→services→helpers).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
