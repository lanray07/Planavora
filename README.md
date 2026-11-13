# Planavora

Planavora is a native Android event planner for Fire tablets and compatible Android devices. It keeps events, budgets, guests, RSVPs, tasks and event-day schedules on the device.

## Build and install

Use JDK 17, Android SDK platform 36 and Android build tools 35.0.0. Configure the Android SDK path with ANDROID_HOME or local.properties, then run the Gradle wrapper.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The release task produces an unsigned APK. Sign it with the app's authorised release keystore and verify it with apksigner before any store testing. Keep the keystore and its credentials outside the repository. See [submission preparation](deliverables/reports/submission.md).

## Architecture

Kotlin and Compose render the adaptive UI. A lifecycle-aware ViewModel observes Room event and record streams, and Room transactions enforce relationship and entitlement limits. Room stores event ownership with an indexed foreign key and a cascade for dependent records and history. The version 1 schema is checked in. Future schema changes require explicit migration and upgrade tests. Preferences use DataStore and deadline scans run through WorkManager. Amounts use integer minor units in one currency per event. SpeechRecognizer and TextToSpeech use installed services when available.

The core workflow has no account requirement. The microphone is optional. Document imports use the Android picker, then copy into private storage for offline access. Reports require an explicit share or export action and separate consent for contact and dietary details.

## Localisation and QA

Run `python scripts/localize.py` to verify language coverage and placeholders. The translation script only sends public source strings when explicitly run with `--translate`. Review translated copy and capture each approved storefront locale on a QA device before submitting.

Run `python scripts/capture.py --adb <path-to-adb> --locale en-GB` after installing the debug and instrumentation APKs. The test uses fictional records and clears Planavora's data on the selected test device. Do not run it against a production installation.

Compose the store cards with `python scripts/compose_store.py` and verify them with `python scripts/validate_store.py`. Editable layout settings are in scripts/store_layout.json. The localized headlines and captions are in deliverables/store-assets/metadata.

[Privacy](PRIVACY.md) · [Support](SUPPORT.md)
