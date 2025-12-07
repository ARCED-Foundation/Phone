Remaining tasks:
1. Admin setup pin creation must match 2 entries while creation. If does not match start creating pin again.
2. The end call form should have only 1 button - save. The form fields must have values.
3. The ODK Base URL in the app admin page should be auto-removed if the "Remember this base URL for future sessions" is not toggled on. If toggled off the ODK intent will be used, otherwise, this base URL will be used. So, this button should be called: "Always use this base URL".

Context:
The Phone app already implements offline-first call logging with local Room storage and background syncing to ODK Central Entity API. Many reliability mechanisms are in place (unique call_log_id, WorkManager retries, basic auth, dataset mapping, admin settings). We now want to verify system readiness for no-data-loss guarantee under real field conditions and identify any missing safeguards or edge cases.

Task:
Analyze the current implementation across these dimensions and produce a checklist with one of:
✔ Already implemented | 🛠 Needs improvement | ➕ Optional enhancement

Focus areas to evaluate:

1️⃣ Local durability

PendingSync table: confirm guaranteed persistence before any network attempt

Confirm records are never deleted or overwritten until confirmed 2xx from server

True idempotency via call_log_id handling

2️⃣ Sync logic & error handling

HTTP status-specific logic: 2xx, 400, 401/403, 404, 409, 429, timeout, 5xx

Exponential backoff + jitter (check retry schedule)

Single sync worker per device to avoid bursts

Batching behavior (e.g., sending N logs per run)

3️⃣ Configuration verification

Test request when admin saves server URL / dataset / credentials

Pause sync on 401/403 and surface clear UI recovery path

Reserved‐keys filtering for intent extras (no schema violation risk)

4️⃣ Schema stability

Confirm payload JSON matches Central dataset properties exactly

No stray keys from Intent or defaults

Label formatting confirmed safe (string only, no nulls)

5️⃣ Observability

Log for each sync attempt: timestamp + call_log_id + HTTP code + body extract

UI indicator: pending count + last sync success time

Export error log or show last N errors to admin

6️⃣ Extreme cases

Large offline backlog (100s or 1000s items)

Continuous bad credentials or unreachable server

App reinstalled or device changed — recovery behavior

7️⃣ Capacity protection

Per-request throttling or small inter-request delay (to avoid server overload)

Handling of 429 Retry-After header if ever enabled server-side

Deliverable:
A single table:

| Category | Item | Status (✔/🛠/➕) | Evidence or Suggested Fix |

Then provide short prioritized recommendations for what to improve first, if anything is missing.