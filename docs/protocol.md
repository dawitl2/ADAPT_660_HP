# ADAPT Control Protocol v0.1 (ASSUMED design)

This is our protocol, not an EPOS command format. Shared C99 implementation:
`protocol/include/adapt_protocol.h`; C++17 clients use the same API.
Capability-gated [Phase 2 extensions](protocol-extensions.md) preserve this base format.

One frame: magic `41 43`, major `00`, minor `01`, type u8, flags u8, sequence u16,
payload length u16, payload (0–128 bytes), CRC16 u16. All integers little endian.
Header is 10 bytes; maximum frame is 140. CRC16/CCITT-FALSE covers header+payload
(polynomial 0x1021, initial 0xffff, no reflection/xorout; `123456789` → 0x29b1).
Flags 0=request, 1=response, 2=event; other combinations rejected. Replies echo
request sequence; events use an independent wrapping u16 sequence. Complete frames
only: stream/HID/GATT adapters must bound/reassemble fragments before decoding.
Major/minor must exactly match v0.1; negotiate via HELLO before future upgrades.
CRC detects corruption; it provides no authentication or encryption.

| ID | Message | Request → response payload |
|---|---|---|
| 1 | HELLO | major u8, minor u8 → same |
| 2 | GET_CAPABILITIES | empty → bitmap u32 |
| 3 | GET_DEVICE_STATE | empty → 26-byte state below |
| 4 | ACTION_EVENT | event only: gesture u8, action u16, monotonic ms u64 |
| 5 | SET_ACTION_MAPPING | gesture u8 (1–3), action u16 → same |
| 6 | SET_SETTING | key u8, value u32 → same |
| 7 | GET_SETTING | key u8 → key u8, value u32 |
| 8 | ENTER_PAIRING | empty → empty |
| 9 | REQUEST_REBOOT | empty → empty or ERROR |
| 10 | REQUEST_BOOTLOADER | empty → empty or ERROR |
| 11 | PING | opaque bytes → PONG with same bytes |
| 12 | PONG | response only, opaque bytes |
| 13 | ERROR | response only: error u16, original type u8 |
| 14 | LOG_EVENT | event only: code u16, value u32 |

State bytes: battery (0–100,255 unknown), charging (0 unknown,1 no,2 yes), ANC
(0 unknown,1 off,2 on,3 adaptive), link (0 unknown,1 disconnected,2 connected,3 pairing),
audio/mic/wear (each 0 unknown,1 inactive,2 active), mode (0 wireless,1 analog,
2 to-analog,3 to-wireless,4 recovery), firmware version ASCII[16] NUL-padded,
protocol major/minor. Thus state offsets 0–7, version 8–23, protocol 24–25.

Action IDs: 1 assistant, 2 voice note, 3 study companion, 4 phone, 5 PC, 6 combined,
16–23 custom 1–8. Gesture IDs: 1 short, 2 double, 3 long, 4 very long. Gesture 4
is reserved for local pairing and cannot be configured as an app action.
Setting keys: 1 short max, 2 double window, 3 long, 4 very long, 5 debounce
(all milliseconds); 6 ANC enum. Invalid combinations fail atomically; changing
timing while a button action is pending returns BUSY. Errors: 1 invalid, 2 denied,
3 busy, 4 storage, 5 unsupported, 6 HAL. Log codes: 1 mode transition, 2 pairing,
3 boot request, 4 dropped event, 5 invalid persisted settings, 6 HAL failure.
Capability bits: 0 host simulation, 1 button mapping, 2 settings, 3 jack hypothesis,
4 pairing; no claim of physical BLE, USB, audio or bootloader implementation.

Configuration/recovery commands require an authorized transport; production
adapters must establish peer identity. Boot additionally requires HAL permission.
No automatic retry/deduplication is defined yet: clients must not replay a boot
request. Physical recovery authentication/rate limits are UNKNOWN.
