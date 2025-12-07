# Quickstart: CATI Call Logger Testing

## Prerequisites
1. ODK Central instance with "call_logs" dataset
2. Web user "call_logger" with write access
3. Fossify Phone app built with feature branch
4. ODK Collect configured with intent extras

## Test 1: Manual Admin Setup (P2)
```
1. Launch app → Settings → Admin Setup
2. Enter PIN → Central credentials → Validate → Save
Expected: Credentials saved, validation success toast
```

## Test 2: Outgoing Call Logging + Sync (P1)
```
1. Launch via ODK intent: centralBaseUrl/projectId/call_logs + phoneNumber
2. Make answered call → Hang up
3. Check local DB: CallLog created, outcome=answered
4. Wait background sync → Check ODK Central: Entity exists
Expected: Log synced with timestamps/outcome/extras
```

## Test 3: Incoming Survey Call (P2)
```
1. Receive incoming call → End call
2. Post-call dialog: Enter surveyId/notes → Save
3. Verify DB: PartialSurveyData + CallLog linked
Expected: Data preserved, syncs on completion
```

## Test 4: Failure Scenarios (P1)
```
1. Network off → Make call → Toggle network → Wait sync
2. Invalid credentials → Admin setup → Validation fails
Expected: Exponential retry, error flagged, no data loss
```

## Debug Commands
```
adb shell am start -a org.fossify.phone.ODK_LAUNCH \
  --es centralBaseUrl "https://your-central.com" \
  --es centralProjectId "1" \
  --es centralDatasetName "call_logs" \
  --es phoneNumber "+1234567890"
```

**Quickstart Status: READY**