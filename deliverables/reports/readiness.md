# Planavora 0.1.0 release readiness

Assessment date: 13 November 2026. Overall status: **BLOCKED**.

| Area | Status | Evidence and remaining work |
| --- | --- | --- |
| Debug and release compilation | PASS | Gradle builds a debug APK, an unsigned release APK, and an instrumentation APK. |
| JVM tests | PASS | Five tests for decimal parsing, vendor reconciliation, readiness, timeline overlaps and duplicated record links. |
| Fire HD 10, 11th generation, KFTRWI, Fire OS 7, API 28 | PASS | Debug installation, on-device Room persistence and relationship test, PDF and CSV generation, sensitive report exclusion, and private attachment path validation. |
| Fire UI journey and screenshot capture | BLOCKED | Tablet remained at its PIN screen after waking. The UI attempt could not observe a Compose hierarchy. |
| Other Fire tablets and Android phones | NOT TESTED | Physical Fire OS 8 and a compatible phone remain to be tested. |
| Voice | NOT TESTED | No recognition service was registered on the tested tablet. Ivona and Pico TTS were installed. Spoken playback, permission denial and locale-specific recognition require device testing. Typed command support is implemented. |
| Localisation structure | PASS | 19 locale resources passed coverage and placeholder validation. |
| Localisation quality and RTL review | NOT TESTED | Machine-translated locale resources and storefront text require native-language review, layout review and text-to-speech testing. Several regional variants share a base translation. |
| Amazon purchasing | BLOCKED | SDK, pricing, restore and receipt verification code are present. Deployment of the verifier, SKU registration, purchase tests and a signed test build are pending. |
| Planning assistant | NOT TESTED | The app generates editable offline checklist drafts from event type and date, detects duplicate task names, and shows summaries and an invitation draft. Optional remote AI remains unimplemented. |
| Privacy design | NOT TESTED | Android app-private storage, explicit report sharing, restricted file grants and no account requirement. The privacy draft requires owner approval and retention details. Airtight export and attachment lifecycle auditing remains pending. |
| Original assets | PASS | Adaptive and monochrome icon sources, store icons, invitation-suite artwork and a promotional image are present. Additional category artwork remains pending. |
| Submission | BLOCKED | Authorised release signing, Amazon validation, approved translations, full screenshots, privacy disclosures and clearance of the product name remain pending. |

## Known functional gaps

The current code includes event and record editing, a dashboard, tracked RSVP and task history, seating assignment by list, expense and vendor payment records, time overlap detection, editable checklists and report exports. The full brief still calls for additional work on seating drag-and-drop, timeline reordering across dates, granular and user-scheduled reminders with snooze, encrypted backup and restore, optional app lock, broader assistant actions, and human-verified multilingual voice phrases. A production launch also requires microphone, notification, accessibility, import and offline UI testing.

## Toolchain and evidence

Run the build commands in the repository README. Local Gradle and instrumented output is in deliverables/android/build-logs. Android's release APK requires the owner's approved signing key. Amazon IAP SDK 3.0.9 emits D8 stack-map warnings during clean builds.

Original invitation-suite asset created using built-in ImageGen with an overhead still life of ivory linen, dusty rose ribbon, eucalyptus, blank cards, a champagne envelope and a plum silk bow. The PNG is in deliverables/brand-assets and the compressed runtime image is in app/src/main/res/drawable-nodpi.
