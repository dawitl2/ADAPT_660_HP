# ADAPT Control setup

Kotlin + Compose, Android 10+ (API 29), target API 35; intended phone: Galaxy S20+.
The development transport uses Phase 2 firmware simulation. Stock EPOS events are unsupported.

## Build and simulator

JDK 17/21 and Android SDK platform/build-tools 35. Set `JAVA_HOME` and `ANDROID_HOME`
or use ignored `android/local.properties` for the SDK path.

```powershell
cd android
./gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Linux/macOS: `chmod +x gradlew`, then `./gradlew`. APKs are development-signed.
Build the host simulator first, then from the repository root:

```powershell
python firmware/simulator/bridge.py --simulator build/adapt_sim.exe --settings research/sim-settings.acfg
adb reverse tcp:6600 tcp:6600
```

Paste ignored `research/bridge-token.txt` in Settings → Advanced → Simulator setup.
Connect simulator authenticates, parses real ACP `wire_hex` frames and sends persisted
mappings. Device → Engineering Mode offers gesture simulation, safe Ping, ANC and
disconnect. For phone audio, enable Settings
→ Voice → Allow phone audio; it is off by default and resets after process death.

## Background setup

Welcome → Get started opens Home. Pair headset
audio in Android settings; companion association is a separate system chooser.
Microphone is requested at hands-free setup, Bluetooth at audio/association setup,
notifications on Android 13+, camera only for flashlight. Association cannot create
custom firmware and is not itself an Android 14+ background microphone exemption.

**Enable hands-free while the activity is visible.** A microphone-type foreground
service keeps event readiness and a Stop notification. Idle captures no audio.
Recorders open only for requested sessions. After reboot, process death, force stop
or Stop, open the app to rearm. No boot-time capture or screen-coordinate automation.
Samsung: Apps → ADAPT Control → Battery → Unrestricted, and Never sleeping apps in
Device Care. Names vary by One UI. App/URL/timer launches require a visible unlocked app.

## Gemini Live without a database

No database is required. No Room, Firestore,
Realtime Database or cloud note storage is used. Private `library.json` and WAV files
hold notes/study history. DataStore holds settings; Android Keystore protects tokens.

Firebase AI Logic is Google's Android **AI client**, independent of database products.
Register package `dev.adapt.control`, enable AI Logic with Gemini Developer API, and
place Android configuration in ignored `android/app/google-services.json`. Never
embed a service-account or Gemini server API key. Builds work without configuration
and present provider setup errors in the voice screen.

Debug uses App Check's debug provider: register its private installation token in
Firebase console. Release uses Play Integrity and needs the corresponding signing
and console setup. Configure quotas/API restrictions separately. The Live model is
editable in Settings → Advanced because preview availability changes.

Voice captures mono PCM16 at 16 kHz and plays server PCM at 24 kHz. Transcripts,
session states, cancel, focus/route loss and explicit fresh-session reconnect are
implemented. A 15-minute session limit applies. Only requested Live audio goes to
Google. SDK Live support remains preview.

System assistant launches Android `ACTION_VOICE_COMMAND` from the visible Voice
screen. Android chooses the handler; Gemini Live activation while locked is not
guaranteed. The integrated provider is the controlled path. OpenAI realtime is a
future provider boundary.

## Local notes and study

Notes end after two seconds of silence following speech, another note action, two
minutes total or 15 seconds without speech. Physical activation auto-saves; screen
activation offers Save/Discard. Basic audio saving needs no cloud AI. Android 13+
Transcribe offline uses an available on-device recognizer with a PCM pipe; recognizer
and language-pack support vary. Failures preserve audio. Current titles derive from
the transcript's first line; optional cloud cleanup/titles are future enrichment.

Study supports Explain, Quiz, Rapid Fire, Exam and Review Weak Topics, spoken dialogue,
locally saved transcripts and structured practice feedback. Questions and evaluations
arrive through the `record_study` function; scores are practice feedback. `CourseSource` is the future
course-ingestion boundary; RAG is not shipped.

## Official references checked October 4, 2026

- [Firebase Live API](https://firebase.google.com/docs/ai-logic/live-api)
- [LiveSession reference](https://firebase.google.com/docs/reference/kotlin/com/google/firebase/ai/type/LiveSession)
- [AI Logic setup](https://firebase.google.com/docs/ai-logic/get-started)
- [App Check](https://firebase.google.com/docs/ai-logic/app-check)
- [Foreground-service rules](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Audio routing](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager)
- [CompanionDeviceManager](https://developer.android.com/reference/android/companion/CompanionDeviceManager)
- [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent)
