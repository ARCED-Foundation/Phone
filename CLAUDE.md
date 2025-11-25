# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Fossify Phone is a privacy-focused, open-source Android dialer/caller application. The app emphasizes data privacy, efficiency, and customization without ads or intrusive permissions. It's part of the Fossify organization suite of apps.

**Key Features:**
- Private call management with no ads or tracking
- Speed dial functionality
- Call history grouping
- Conference call support
- Multiple customization options
- Multi-SIM support
- Call blocking capabilities
- Full Material Design 3 theming support

## Common Development Commands

### Building the App
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build specific flavor
./gradlew assembleGplayDebug
./gradlew assembleFossRelease
./gradlew assembleCoreDebug

# Build and install to connected device
./gradlew installDebug
```

### Testing
```bash
# Run all unit tests
./gradlew test

# Run tests for specific variant
./gradlew testGplayDebugUnitTest

# Run tests with coverage (if configured)
./gradlew testGplayDebugUnitTestCoverage

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Code Quality
```bash
# Run detekt static analysis
./gradlew detekt

# Run detekt with auto-correct
./gradlew detektAutoCorrect

# Check lint
./gradlew lint
```

### Development Tasks
```bash
# Clean build artifacts
./gradlew clean

# Build with verbose output
./gradlew assembleDebug --info

# Run dependency analysis
./gradlew dependencies
```

## Project Architecture

### Package Structure
The application follows a layered architecture with clear separation of concerns:

```
org.fossify.phone/
├── activities/          # Android Activities (UI entry points)
│   ├── MainActivity.kt      # Primary app interface (3 tabs: Recents, Contacts, Favorites)
│   ├── CallActivity.kt      # In-call UI screen
│   ├── DialerActivity.kt    # Outgoing call handler
│   ├── DialpadActivity.kt   # On-screen dialer
│   ├── ConferenceActivity.kt # Conference call management
│   └── SettingsActivity.kt  # App settings
├── services/            # Android Services (background operations)
│   ├── CallService.kt           # Telecom InCallService implementation
│   └── SimpleCallScreeningService.kt # Call screening/filtering
├── helpers/            # Business logic and managers
│   ├── CallManager.kt         # Central call state management
│   ├── CallNotificationManager.kt # Call notification handling
│   └── RecentsHelper.kt       # Call history management
├── adapters/           # RecyclerView adapters
│   ├── ContactsAdapter.kt     # Contact list display
│   ├── RecentsAdapter.kt      # Call history list
│   ├── ConferenceCallsAdapter.kt # Conference participants
│   └── SpeedDialAdapter.kt    # Speed dial interface
├── dialogs/            # Custom dialogs
│   ├── CallActionDialog.kt    # In-call actions
│   ├── ExportCallHistoryDialog.kt
│   └── SelectSIMDialog.kt     # SIM card selection
├── fragments/          # UI fragments (pager tabs)
│   ├── RecentsFragment.kt     # Call history tab
│   ├── ContactsFragment.kt    # Contacts tab
│   └── FavoritesFragment.kt   # Favorites tab
├── extensions/         # Kotlin extension functions
│   ├── Activity.kt            # Activity-specific extensions
│   ├── Call.kt                # Call-related extensions
│   └── Context.kt             # Context utilities
├── models/             # Data models
│   └── Call.kt                # Call data structure
├── receivers/          # BroadcastReceivers
│   └── CallActionReceiver.kt  # Handles call action intents
└── interfaces/         # Interface definitions
```

### Core Architecture Components

**1. CallManager (helpers/CallManager.kt)**
- Singleton pattern managing all call states
- Coordinates between Telecom API and UI
- Handles audio routing, call operations (hold, resume, merge, split)
- Uses EventBus to notify UI components of call state changes

**2. CallService (services/CallService.kt)**
- Implements Android's `InCallService` interface
- Receives call events from TelecomManager
- Bridges system call events to CallManager
- Runs in foreground to maintain reliability

**3. MainActivity (activities/MainActivity.kt)**
- Primary UI with ViewPager containing 3 tabs: Recents, Contacts, Favorites
- Handles app lifecycle and permission management
- Integrates with Fossify Commons for common UI patterns
- Manages notification permissions and default phone app setup

**4. CallActivity (activities/CallActivity.kt)**
- Full-screen in-call interface
- Displays caller information, call duration
- Provides controls: end, hold/resume, mute, speaker, dialpad
- Handles conference calls and call merging

### Dependencies and Integration

**Core Dependencies:**
- `fossify-commons` - Shared UI components, themes, utilities across Fossify apps
- `EventBus` - Decoupled component communication
- `libphonenumber` - Phone number parsing and formatting
- `kotlinx.serialization.json` - JSON serialization
- `IndicatorFastScroll` - Fast scroll RecyclerView library
- `AutofitTextView` - Automatic text size adjustment

**Build Flavors:**
- `core` - Base configuration
- `foss` - F-Droid build (no Google Play services)
- `gplay` - Google Play build (may include Play-specific features)

**Android Components:**
- Uses Android Telecom framework for call management
- System UI for in-call experience
- Notification channels for call status
- Foreground service for call reliability
- ContentResolver for call logs and contacts

## Build Configuration

### Version Configuration (gradle.properties)
- `VERSION_NAME=1.8.0` - Semantic version
- `VERSION_CODE=17` - Internal version for updates
- `APP_ID=org.fossify.phone` - Application ID

### SDK Targets (gradle/libs.versions.toml)
- `minimumSDK=26` - Android 8.0
- `targetSDK=36` - Android 16
- `compileSDK=36`
- Kotlin: `2.2.21`
- Gradle Plugin: `8.11.1`

### Signing Configuration
- Release builds require signing via `keystore.properties` or environment variables:
  - `SIGNING_KEY_ALIAS`
  - `SIGNING_KEY_PASSWORD`
  - `SIGNING_STORE_FILE`
  - `SIGNING_STORE_PASSWORD`

## Important Implementation Notes

### Permissions and System Integration
The app requires multiple critical permissions declared in `AndroidManifest.xml`:
- `CALL_PHONE`, `CALL_PRIVILEGED` - For making calls
- `READ_CONTACTS`, `WRITE_CONTACTS` - For contact integration
- `READ_CALL_LOG`, `WRITE_CALL_LOG` - For call history
- `ANSWER_PHONE_CALLS` - For auto-answering (Android 8+)
- `FOREGROUND_SERVICE` - For reliable call service
- `POST_NOTIFICATIONS` - For call notifications (Android 13+)
- `USE_FULL_SCREEN_INTENT` - For full-screen incoming call display

### Call Flow Architecture
1. **Incoming Call** → System → CallService → CallManager → CallActivity
2. **Outgoing Call** → DialerActivity/DialpadActivity → Intent → System Call UI
3. **Call Management** → CallManager → Telecom API → System

### State Management
- Call state tracked in `CallManager` singleton
- UI updates via EventBus from background to foreground components
- Persistent settings via Android SharedPreferences (through Commons)
- Call history stored in Android call log provider

### UI Framework
- Standard Android Views (no Jetpack Compose yet)
- Fossify Commons provides:
  - Material Design 3 theming
  - Custom dialogs and bottom sheets
  - Permission handling
  - Color customization system
  - FAQ system

## Code Quality Standards

**Detekt Rules (detekt.yml):**
- Max line length: 120 characters
- Max function length: 120 lines
- Max parameters: 10 (functions), 8 (constructors)
- Magic numbers (except: -1, 0, 1, 2, 42, 1000)
- Return count: max 4 per function
- All issues must be resolved (maxIssues: 0)

**Code Style:**
- Kotlin official code style
- Extension functions heavily used for clean API
- Follows Android/Kotlin best practices
- Dependency injection via constructor (manual, no DI framework)

## Development Setup

1. **Clone and Setup**
   ```bash
   git clone <repo>
   cd Phone
   ```

2. **Open in Android Studio**
   - Arctic Fox or later recommended
   - Sync project with Gradle files

3. **Configure Signing (for release builds)**
   - Copy `keystore.properties_sample` to `keystore.properties`
   - Add your signing credentials

4. **Build Variants**
   - Default: `gplayDebug` for development
   - For F-Droid: `fossDebug`
   - For testing: `coreDebug`

## Key Resources

- **App Icon**: Multiple themed launchers defined in `AndroidManifest.xml` (activity-aliases)
- **String Resources**: App-specific strings in `app/src/main/res/values/strings.xml` (Fossify Commons provides shared strings)
- **Layouts**: ViewBinding enabled in build.gradle.kts
- **Themes**: Material Design 3 with app theming

## Important References

- Fossify Commons: Shared library for common functionality
- Android Telecom API: Core call management
- Material Design 3: UI/UX guidelines
- Contributors guide: https://github.com/FossifyOrg/General-Discussion#contribution-rules-for-developers
