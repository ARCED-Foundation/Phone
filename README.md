# Fossify Phone
<img alt="Logo" src="graphics/icon.webp" width="120" />

<a href='https://play.google.com/store/apps/details?id=org.fossify.phone'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=80/></a> <a href="https://f-droid.org/packages/org.fossify.phone/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" height=80/></a> <a href="https://apt.izzysoft.de/fdroid/index/apk/org.fossify.phone"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height=80/></a>

Empower your calls, and safeguard your data. Fossify Phone redefines the mobile app experience with unmatched privacy and efficiency. Free from ads and intrusive permissions, it's designed for seamless and secure everyday communication.

📱 **YOUR PRIVACY, OUR PRIORITY:**  
Welcome to the Fossify Phone App, where your digital privacy is paramount. Switch to a mobile experience that respects your data, ensuring your personal information remains secure and private.

🚀 **SEAMLESS PERFORMANCE:**  
The Fossify Phone App offers a fluid and responsive mobile interface, enhancing your phone's performance while safeguarding your privacy. Experience a lag-free, smooth user experience, optimized for efficiency and speed.

🌐 **OPEN-SOURCE ASSURANCE:**  
With the Fossify Phone App, transparency is at your fingertips. Built on an open-source foundation, our app allows you to review our code on GitHub, fostering trust and a community committed to privacy.

🖼️ **TAILOR-MADE CUSTOMIZATION:**  
Customize your mobile experience with the Fossify Phone App. Adjust your app settings for a personalized interface, from thematic designs to functional preferences. Enjoy a user interface that's intuitive and uniquely yours.

🔋 **EFFICIENT RESOURCE MANAGEMENT:**  
The Fossify Phone App is designed for optimal resource usage, contributing to extended battery life. It's light on your phone's resources, ensuring your device runs efficiently with minimized battery drain.

Download the Fossify Phone App now and step into a mobile world where privacy seamlessly blends with functionality. Your journey towards a safer, personalized mobile experience starts here.

## Call Logging Configuration

1. **Admin setup** – Launch the admin flow, protect it with a PIN, and save your ODK Central credentials plus a configurable list of reserved keys so sensitive extras never leave the device.
2. **Automatic call logging** – Every call attempt generates a call log record, deduplicated via `CallLogger`, stored in Room, and queued for battery-aware WorkManager uploads when constraints allow.
3. **Manual fallback** – If automatic detection misses a call, the “End call & record” action surfaces a confirmation dialog, reuses the call logger pipeline, and still pushes the same pending sync entry.
4. **Monitoring** – PerformanceMonitor keeps call detection under 100 ms and syncs under 5 min, while CrashTracker, CallDetectionAnalytics, and JaCoCo coverage reports guard reliability.
5. **Validation quickstart** – Run the quickstart integration suite under `app/src/androidTest/kotlin/org/fossify/phone/quickstart/QuickstartIntegrationTest.kt` to ensure admin setup, manual fallback dialogs, reserved-key filtering, and failure handling behave before syncing to Central.

➡

## Central entity schema

Call logs are uploaded to Central via POST /v1/projects/{projectId}/datasets/{datasetName}/entities/creators. Each request builds an entity whose data map comes from CallSyncManager.createEntityPayload. The following properties are always set:

- central_base_url, project_id, dataset - the intent-provided Central configuration.
- call_log_id, direction, call_start_utc, call_end_utc, duration_seconds, outcome, phone_number - the core call fields mirrored in the Room record.
- Optional metadata when present: outcome_detail, instance_id, enumerator_id, survey_id, dditional_notes.
- Every non-reserved intent extra (reserved keys are centralBaseUrl, centralProjectId, centralDatasetName, system_*, plus any custom reserved keys configured in Admin Settings).

All values are submitted as strings because Central entities store string-valued properties. Bulk uploads reuse the same field names inside an entities array plus an optional source descriptor.

Explore more Fossify apps: https://www.fossify.org<br>
➡️ Open-Source Code: https://www.github.com/FossifyOrg<br>
➡️ Join the community on Reddit: https://www.reddit.com/r/Fossify<br>
➡️ Connect on Telegram: https://t.me/Fossify

<div align="center">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%">
</div>
