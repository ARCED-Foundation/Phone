# Implementation Plan: Enhanced ODK Integration with Value Concatenation

**Branch**: `001-odk-value-concat` | **Date**: 2025-11-22 | **Spec**: [link]
**Input**: Feature specification from `/specs/001-odk-value-concat/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Enhanced ODK integration that preserves existing values from ODK Collect, concatenates new call data with pipe separators, includes accurate call start timestamps, and returns empty values instead of "No calls made" for empty sessions. The implementation builds on the existing robust call tracking system in Fossify Phone to provide seamless external integration with improved data handling capabilities.

## Technical Context

**Language/Version**: Kotlin 2.2.21 for Android SDK 26+
**Primary Dependencies**: fossify-commons, Android Telecom API, libphonenumber, EventBus
**Storage**: Android SharedPreferences, Android CallLog content provider
**Testing**: Android JUnit tests, Robolectric UI tests, Espresso integration tests
**Target Platform**: Android 8.0+ (SDK 26-36) with Material Design 3 theming
**Project Type**: Mobile application with session-based external integration
**Performance Goals**: Real-time call tracking (<100ms latency), efficient session management, minimal memory footprint
**Constraints**: Privacy-first design, offline capability, build variant compatibility (core/foss/gplay)
**Scale/Scope**: Single-user call management with external integration support

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Privacy-First Design Compliance
- [✓] Integration features respect user privacy principles (no call content storage, only metadata tracking)
- [✓] No unnecessary data collection beyond what's required (only essential call metadata)
- [✓] Proper permission handling and user consent mechanisms (uses Android runtime permissions)

### Seamless External Integration Compliance
- [✓] Uses standard Android intent protocols (existing intent filters for ODK launch)
- [✓] Well-defined data return mechanisms implemented (String extra with formatted call data)
- [✓] Graceful error handling for integration workflows (existing exception handling)

### Metadata Accuracy Compliance
- [ ] Call data tracking is precise and reliable (NEEDS CLARIFICATION: currently connection-based, need dial-based for timestamps)
- [ ] Connection-based timing (not dial-based) implemented (VIOLATION: needs dial-based timestamps for ODK)
- [ ] Concurrent call independence maintained (✓ existing implementation handles this)

### Build Variant Compatibility Compliance
- [✓] Functions across all build variants (core, foss, gplay)
- [✓] Debug and release intent filters both supported
- [✓] No hardcoded variant-specific limitations

### User Experience Clarity Compliance
- [✓] Clear visual indicators for integration sessions (existing ODK banner)
- [✓] Intuitive session exit mechanisms provided ("Finish & Return" button)
- [✓] Proper state persistence and user confirmation (existing dialog confirmation)

### Integration Standards Compliance
- [✓] Follows standard Android intent patterns
- [✓] Integration points maintainable and documented
- [✓] Session lifecycle clearly defined

### Testing Requirements Compliance
- [✓] Integration workflows have end-to-end tests (existing test coverage)
- [✓] Call metadata accuracy validation implemented (existing validation)
- [✓] Build variant compatibility verified (existing implementation)
- [✓] Error scenarios comprehensively tested (existing error handling)

## Project Structure

### Documentation (this feature)

```text
specs/001-odk-value-concat/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
org/fossify.phone/
├── activities/
│   ├── DialpadActivity.kt        # Enhanced ODK session management
│   ├── CallActivity.kt           # In-call UI (minimal ODK changes)
│   └── MainActivity.kt           # Main app container
├── services/
│   ├── CallService.kt            # Telecom service (no changes)
│   └── SimpleCallScreeningService.kt # Call screening
├── helpers/
│   ├── CallManager.kt            # Enhanced call tracking with timestamps
│   └── RecentsHelper.kt          # Call history management
├── adapters/
│   ├── ContactsAdapter.kt       # Contact lists
│   ├── RecentsAdapter.kt        # Recent calls
│   └── SpeedDialAdapter.kt      # Speed dial interface
├── dialogs/
│   └── CallActionDialog.kt      # In-call actions
├── fragments/
│   ├── RecentsFragment.kt       # Call history tab
│   ├── ContactsFragment.kt      # Contacts tab
│   └── FavoritesFragment.kt     # Favorites tab
├── extensions/
│   ├── Activity.kt              # Activity extensions
│   ├── Call.kt                  # Call-related extensions
│   └── Context.kt               # Context utilities
├── models/
│   └── Call.kt                  # Call data model
├── receivers/
│   └── CallActionReceiver.kt    # Call action handling
└── AndroidManifest.xml           # Intent filters (existing)
```

**Structure Decision**: Single Android mobile application using existing project structure. Changes will be focused on `DialpadActivity.kt` and `CallManager.kt` to implement enhanced ODK integration with value concatenation and timestamp features.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Connection-based timing vs dial-based timestamps | ODK requirements explicitly need dial-based timestamps for accurate call logging | Connection-based timing maintains accurate duration calculation for UI while dial-based timestamps satisfy ODK data requirements for temporal analysis |

## Phase 0: Research Outline

**Unknowns Identified from Constitution Check**:
- **NEEDS CLARIFICATION**: How to implement dial-based timestamp recording while maintaining connection-based duration calculation
- **NEEDS RESEARCH**: Best practices for preserving existing ODK values during session initialization
- **NEEDS RESEARCH**: Android intent handling for preserving incoming extras throughout session lifecycle

**Research Tasks**:
1. **Dial-based Timestamp Implementation**: Research Android Telecom API call events to identify dial vs connect timing points
2. **ODK Value Preservation**: Analyze Android intent extra handling mechanisms for maintaining state across session lifecycle
3. **Failed Call Detection**: Research Android call state transitions for identifying failed dial attempts
4. **Data Format Consistency**: Validate current call data format against ODK requirements for timestamp inclusion
