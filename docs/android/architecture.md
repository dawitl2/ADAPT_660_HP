# Android architecture

`AdaptApplication` owns `AppGraph`; Compose reads lifecycle-aware state flows. The navigation graph contains Home, Actions, Device, Settings, Voice, Notes and Study, with a one-screen welcome.

| Boundary | Implementation |
| --- | --- |
| Headset commands | `DeviceTransport`, authenticated simulator transport, ACP codec and bounded GATT reassembly |
| Audio | `AudioRoute`, PCM recorder/playback, focus and route interruption handling |
| Voice providers | `VoiceAssistantProvider`, Gemini Live, Android system assistant, future OpenAI boundary |
| Requested sessions | Microphone foreground service with stop notification; idle readiness captures no audio |
| Phone and PC actions | `ActionProvider`, phone utilities, pinned HTTPS PC provider, combined action results |
| Persistence | DataStore preferences, atomic private JSON library, WAV files, Keystore credentials |
| Study | Structured question/evaluation feedback and local history; `CourseSource` ingestion boundary |

The firmware HAL and transport interfaces keep platform-specific integration separate from Compose, action routing and voice providers. The ACP command layer carries control messages; requested session audio has its own lifecycle.
