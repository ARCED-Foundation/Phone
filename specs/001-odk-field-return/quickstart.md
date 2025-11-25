# Implementation Quickstart: ODK Field Auto-Return

## Prerequisites
- Branch: `001-odk-field-return`
- Read: research.md, data-model.md, contracts/

## Step-by-Step

1. **Extend Models** (`models/Call.kt`):
   ```kotlin
   @Serializable data class ODKSession(
       // ... existing
       val fieldId: String? = null,
       val callingPackage: String? = null
   )
   ```

2. **Capture Launch Data** (`DialpadActivity.kt` ~line 550):
   ```kotlin
   val fieldId = intent.getStringExtra("odk_field_id")
   CallManager.initializeOdkSession(..., fieldId, callingPackage = packageManager.getCallingPackage())
   ```

3. **Auto-Return on Connect** (`DialpadActivity.kt`):
   - Register CallManager listener
   - On first `notifyCallActiveEvents`: `returnToOdk(true)` (partial=true)

4. **Auto-Return on Disconnect** (`CallManager.kt notifyCallEndedEvents`):
   - If no active calls && autoReturnDisconnect: Broadcast to DialpadActivity or store pendingIntent → trigger returnToOdk(false)

5. **Low-Prio Form Block**: ODK-side (not in scope?); app can set ODK form validity via extra.

6. **Tests**:
   - Unit: Mock Telecom states in CallManager
   - Integration: Mock ODK intent → verify setResult extras

## Validation
- `./gradlew test`
- Manual: adb shell am start -a org.fossify.phone -e phoneNumber +123 --eu odk_field_id testfield
