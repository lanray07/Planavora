# Fire HD 10 voice verification

Device: Fire HD 10 (11th generation), KFTRWI, Fire OS 7 build PS7331.4463N, Android API 28.

| Check | Status | Result |
| --- | --- | --- |
| English spoken output | PASS | Android TextToSpeech initialised on the hardware, accepted an en-GB utterance and reported playback complete. |
| Typed budget command | PASS | The Fire tablet screenshot test uses the localised example, runs the command and waits for the saved event's calculated balance to appear. |
| Microphone input | BLOCKED | Android reported no installed SpeechRecognizer service on this hardware configuration. |
| Microphone denial recovery | NOT TESTED | Requires a device configuration with an installed recognition service. |
| Other hardware | NOT TESTED | Fire OS 8 tablets and Android phones were outside this device run. |

The default installed speech engine returned country-level TTS support for en-GB. It returned language-level support for en-US and en-AU, so those accents and playback were not verified. The engine returned LANG_NOT_SUPPORTED for es-ES, es-419, fr-FR, fr-CA, de-DE, it-IT, pt-BR, pt-PT, nl-NL, pl-PL, sv-SE, ja-JP, hi-IN, ar, tr-TR and id-ID. These results apply to the installed default engine at test time. Typed commands remain available when speech is unavailable.

Local copies of the instrumentation result and device capability output are in deliverables/android/build-logs/fire-verified.log and fire-voice-capabilities.log.
