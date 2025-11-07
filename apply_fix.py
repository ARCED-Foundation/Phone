#!/usr/bin/env python3
import re

# Read the original file
with open(r'/c/Users/Mehrab Ali/Documents/GitHub/Phone/app/src/main/kotlin/org/fossify/phone/helpers/CallManager.kt', 'r') as f:
    lines = f.readlines()

# Find the line number where to insert callPreviousStates (after line 22)
insert_line = 22
lines.insert(insert_line, '        private val callPreviousStates = mutableMapOf<Call, Int>()\n')

# Update checkCallStateTransitions call
for i, line in enumerate(lines):
    if 'checkCallStateTransitions(previousState)' in line:
        lines[i] = '            checkCallStateTransitions()\n'
        break

# Update checkCallStateTransitions method
# Find start and end of method
start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if 'private fun checkCallStateTransitions(previousState: Int?)' in line:
        start_idx = i
    if start_idx != -1 and line.strip() == '}' and 'callPreviousStates' not in line:
        end_idx = i
        break

# Replace the method
if start_idx != -1 and end_idx != -1:
    new_method = [
        '        private fun checkCallStateTransitions() {\n',
        '            calls.forEach { call ->\n',
        '                val currentState = call.getStateCompat()\n',
        '                val previousState = callPreviousStates[call]\n',
        '                \n',
        '                // Only notify if state actually changed\n',
        '                if (previousState != currentState) {\n',
        '                    when (currentState) {\n',
        '                        Call.STATE_ACTIVE -> {\n',
        '                            // Call became active (connected)\n',
        '                            if (previousState != Call.STATE_ACTIVE) {\n',
        '                                notifyCallStartedEvents(call)\n',
        '                            }\n',
        '                        }\n',
        '                        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {\n',
        '                            // Call ended\n',
        '                            notifyCallEndedEvents()\n',
        '                        }\n',
        '                    }\n',
        '                }\n',
        '                \n',
        '                // Update the stored state\n',
        '                callPreviousStates[call] = currentState\n',
        '            }\n',
        '            \n',
        '            // Clean up states for removed calls\n',
        '            val activeCallIds = calls.map { it.details.handle }.toSet()\n',
        '            callPreviousStates.keys.removeAll { it.details.handle !in activeCallIds }\n',
        '        }\n'
    ]
    lines = lines[:start_idx] + new_method + lines[end_idx+1:]

# Add callPreviousStates.clear() when no calls remain
for i, line in enumerate(lines):
    if 'if (previousState != null)' in line and 'notifyCallEndedEvents()' in line:
        # Find the next closing brace
        for j in range(i+1, len(lines)):
            if lines[j].strip() == '}':
                # Insert clear before this brace
                lines.insert(j, '                // Clear the state map when no calls remain\n')
                lines.insert(j+1, '                callPreviousStates.clear()\n')
                break
        break

# Write the modified file
with open(r'/c/Users/Mehrab Ali/Documents/GitHub/Phone/app/src/main/kotlin/org/fossify/phone/helpers/CallManager.kt', 'w') as f:
    f.writelines(lines)

print("Successfully applied the fix!")
