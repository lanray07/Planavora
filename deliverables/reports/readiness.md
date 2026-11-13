# Planavora 0.1.0 release readiness

Assessment date: 13 November 2026. Overall status: **BLOCKED**.

| Area | Status | Evidence and remaining work |
| --- | --- | --- |
| Debug and release compilation | PASS | Gradle builds a debug APK, an unsigned release APK, and an instrumentation APK. |
| JVM tests | PASS | Five tests for decimal parsing, vendor reconciliation, readiness, timeline overlaps and duplicated record links. Four additional instrumented tests passed on the Fire HD 10. |
| Fire HD 10, 11th generation, KFTRWI, Fire OS 7, API 28 | PASS | Debug installation, on-device Room persistence and relationship test, PDF and CSV generation, sensitive report exclusion, and private attachment path validation. |
| Fire tablet UI | PASS | Created an event and task, completed the task, closed the activity and verified both records after relaunch on Fire OS 7. |
| Amazon Fire tablet screenshot files | PASS | 120 real 1920 × 1200 captures and 120 verified 2560 × 1600 listing compositions across 15 storefront variants. Eight additional Arabic captures are for RTL QA. Phone variants remain untested. |
| Other Fire tablets and Android phones | NOT TESTED | Physical Fire OS 8 and a compatible phone remain to be tested. |
| Typed commands and on-device en-GB TTS | PASS | The capture journey executes a typed budget command and reads the live balance. Android TTS reported completion for a British English test utterance. |
| Speech recognition | BLOCKED | The tested Fire OS 7 image exposes no Android recognition service. See the Fire voice report for locale availability. |
| Localisation structure | PASS | 19 locale resources passed coverage and placeholder validation. |
| Arabic dashboard deadline layout | PASS | Fire HD 10 regression test confirmed that the English task title appears above the intact localised date and the deadline opens the task list. |
| Broader localisation quality | NOT TESTED | Some visible translations need editorial correction. Native-language review and broader layout and accessibility testing remain pending. |
| Amazon App Tester sandbox | PASS | Fire HD 10 loaded the test price, unlocked Premium after receipt verification and restored the entitlement with an empty local Premium cache. Dismissing the purchase left Premium locked and displayed the generic purchase error. |
| Amazon production purchase testing | BLOCKED | Developer Console entitlement setup, the Appstore public key, production receipt verification and Live App Testing remain pending. |
| Planning assistant | NOT TESTED | The app generates editable offline checklist drafts from event type and date, detects duplicate task names, and shows summaries and an invitation draft. Optional remote AI remains unimplemented. |
| Privacy design | NOT TESTED | Android app-private storage, explicit report sharing, restricted file grants and no account requirement. The privacy draft requires owner approval and retention details. Airtight export and attachment lifecycle auditing remains pending. |
| Original assets | PASS | Adaptive and monochrome icon sources, store icons, invitation-suite artwork and a promotional image are present. Additional category artwork remains pending. |
| Submission | BLOCKED | Amazon validation with standard Amazon signing, approved translations and screenshot copy, phone variants, privacy disclosures and clearance of the product name remain pending. |

## Known functional gaps

The current code includes event and record editing, a dashboard, tracked RSVP and task history, seating assignment by list, expense and vendor payment records, time overlap detection, editable checklists and report exports. The full brief still calls for additional work on seating drag-and-drop, timeline reordering across dates, granular and user-scheduled reminders with snooze, encrypted backup and restore, optional app lock, broader assistant actions, and human-verified multilingual voice phrases. A production launch also requires microphone, notification, accessibility, import and offline UI testing.

## Toolchain and evidence

Run the build commands in the repository README. Local Gradle and instrumented output is in deliverables/android/build-logs. The release APK is unsigned. See the submission guide for Amazon signing and developer-signed test builds. Amazon IAP SDK 3.0.9 emits D8 stack-map warnings during clean builds.

Original invitation-suite asset created using built-in ImageGen with an overhead still life of ivory linen, dusty rose ribbon, eucalyptus, blank cards, a champagne envelope and a plum silk bow. The PNG is in deliverables/brand-assets and the compressed runtime image is in app/src/main/res/drawable-nodpi.
