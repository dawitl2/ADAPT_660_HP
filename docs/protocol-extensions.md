# Optional Phase 2 v0.1 extensions (ASSUMED design)

Original frame layout, version bytes, message IDs 1–14, state length/offsets and
error codes remain unchanged. HELLO is still 0.1. GET_CAPABILITIES advertises
optional additions: bit5 retained diagnostics, bit6 lifecycle, bit7 metadata.
Clients must check bits before sending these requests. Old clients can continue
using only their original messages. Unknown operations still fail closed.

| ID | Payload |
|---|---|
| 15 GET_DIAGNOSTIC | Request: newest-first index u8. Authorized response: count u8, serial u32, time_ms u64, code u16, value u32 (19 bytes). Missing index returns INVALID. |
| 16 LIFECYCLE_STATE | Empty request; response or event: state u8, peers u8, active peer u8 (255 none), restart attempts u8, maximum peers u8. |
| 17 FIRMWARE_METADATA | Empty request; response: product ASCII[32], firmware ASCII[24], protocol ASCII[24], config schema u16, physical target ready u8. Strings NUL-padded; host/pending ready=0. |

Retained log is a fixed 32-record volatile ring, overwritten oldest first, lost
on reboot. Index zero is newest; live inserts can shift indices between reads.
Use serial/timestamp to detect overlaps; query while simulator time is paused
for a stable snapshot. Records contain numeric policy observations, never audio,
keys, Bluetooth addresses, transcriptions or raw request payloads. Serial wraps
u32; event sequence wraps u16 independently. Diagnostics require authorization.

New setting keys: 7 confirmation tones boolean, 8 preferred action u16 carried in
u32, 9 multipoint preference boolean, 10 diagnostic level 0–2. Read-only keys
16/17/18 return short/double/long mapping; changes still use SET_ACTION_MAPPING.
Boolean inputs must be exactly 0 or 1. Very-long has no mapping key.

Additional log codes: 10 boot reason, 11 connection created (peer count), 12
connection lost (peer count), 13 wireless restart (success boolean), 14 jack
inserted, 15 jack removed, 16 button gesture, 17 ANC change, 18 protocol error
(error enum only), 19 watchdog restart request, 20 lifecycle state. Existing
codes 1–6 retain their meanings. Level0 suppresses new records/live logs; level1
records these events; level2 is reserved for future additional nonprivate detail.
