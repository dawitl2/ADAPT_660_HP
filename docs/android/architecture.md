# Planned Android boundary (ASSUMED)

Transport interface: BLE/USB/mock. AI provider interface: Android system assistant,
integrated Gemini Live, future OpenAI realtime providers. Control packets carry
actions, not microphone samples. Audio lifecycle is a separate service boundary.
Hands-free launch, background restrictions and S20+ audio routing need real tests
in the Android phase. No Android UI is implemented now.
