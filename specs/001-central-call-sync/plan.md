# Implementation Plan: CATI Call Logger with ODK Central Integration

**Branch**: `001-central-call-sync` | **Date**: 2025-11-30 | **Spec**: specs/001-central-call-sync/spec.md
**Input**: Feature specification from `/specs/001-central-call-sync/spec.md`

## Summary

Implement automated call logging for the Fossify Phone app that detects all call attempts (answered, unanswered, busy, failed, rejected), generates structured call log records with timestamps and outcomes, and syncs them to ODK Central via Entity API. The system uses dynamic configuration from ODK Collect intent extras, stores data locally with Room database, and performs background sync with WorkManager using exponential backoff retry strategy. A PIN-protected admin interface allows configuration of ODK Central credentials and reserved keys filtering.

## Technical Context

**Language/Version**: Kotlin 2.2.21 | **Primary Dependencies**: Room, WorkManager, OkHttp, AndroidX | **Storage**: Room Database | **Testing**: JUnit, Espresso, Android Instrumentation Tests | **Target Platform**: Android 8.0+ (API 26-36) | **Project Type**: Android mobile app | **Performance Goals**: <100ms call detection, 5min background sync completion | **Constraints**: Offline capable, minimal battery impact, background-friendly | **Scale/Scope**: Single call logger module, up to 100 pending syncs, 5min sync time

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Privacy-First Design Compliance
- [ ] Integration features respect user privacy principles
- [ ] No unnecessary data collection beyond what's required
- [ ] Proper permission handling and user consent mechanisms

### Seamless External Integration Compliance
- [ ] Uses standard Android intent protocols
- [ ] Well-defined data return mechanisms implemented
- [ ] Graceful error handling for integration workflows

### Metadata Accuracy Compliance
- [ ] Call data tracking is precise and reliable
- [ ] Connection-based timing (not dial-based) implemented
- [ ] Concurrent call independence maintained

### Build Variant Compatibility Compliance
- [ ] Functions across all build variants (core, foss, gplay)
- [ ] Debug and release intent filters both supported
- [ ] No hardcoded variant-specific limitations

### User Experience Clarity Compliance
- [ ] Clear visual indicators for integration sessions
- [ ] Intuitive session exit mechanisms provided
- [ ] Proper state persistence and user confirmation

### Integration Standards Compliance
- [ ] Follows standard Android intent patterns
- [ ] Integration points maintainable and documented
- [ ] Session lifecycle clearly defined

### Testing Requirements Compliance
- [ ] Integration workflows have end-to-end tests
- [ ] Call metadata accuracy validation implemented
- [ ] Build variant compatibility verified
- [ ] Error scenarios comprehensively tested

## Project Structure

### Documentation (this feature)

```text
specs/001-central-call-sync/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
app/src/main/java/org/fossify/phone/
├── activities/          # Existing activities
├── services/            # Background services and sync
├── helpers/             # Call manager and business logic
├── adapters/            # UI adapters
├── dialogs/             # Custom dialogs
├── fragments/           # UI fragments
├── models/              # Data models (Room entities)
├── receivers/           # Broadcast receivers
├── extensions/          # Kotlin extensions
└── interfaces/          # Interface definitions

# New files for feature implementation:
├── activities/AdminSetupActivity.kt     # PIN-protected admin interface
├── services/CallSyncService.kt          # WorkManager sync service
├── models/CallLog.kt                    # Room entity for call logs
├── models/CentralCredentials.kt         # Room entity for ODK Central credentials
├── models/PendingSync.kt                # Room entity for sync tracking
├── models/PartialSurveyData.kt         # Room entity for incoming survey data
├── helpers/CallSyncManager.kt           # Core sync logic and retry handling
├── helpers/AdminSettingsHelper.kt       # Reserved keys and credentials management
└── dialogs/SurveyDataCollectionDialog.kt # Post-call survey data collection
```

### Completed Phase 10 polish (polished after Task run)
```
├── utils/PerformanceMonitor.kt          # Tracks call detection + sync latency thresholds
├── utils/BatteryOptimizer.kt            # Defers sync work when the battery is low
├── utils/CallDetectionAnalytics.kt     # Stores detection failure summaries
├── utils/CrashTracker.kt                # Captures crash metadata and hooks uncaught handlers
├── suites/CallLoggingStoriesUnitTestSuite.kt # Aggregated unit suite
├── suites/CallLoggingStoriesInstrumentedTestSuite.kt # Instrumented suite with quickstart flows
```

**Structure Decision**: Mobile single project structure within existing Android Phone app. Feature uses modular architecture with clear separation between call detection, data persistence, sync logic, and administration components.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| | | |
