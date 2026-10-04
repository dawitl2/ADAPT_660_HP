# Custom control transport (ASSUMED design)

Verified working transport is the host simulator process/TCP bridge, carrying ACP
v0.1. Physical USB endpoints and GATT deployment are **UNKNOWN**. Windows lists a
stock LE device; our proposed service has not been discovered on stock firmware.
Standard A2DP/HFP/AVRCP audio stays in the vendor stack, separate from control.
Concurrent custom BLE depends on the exact SDK/controller being identified.

UUIDs generated once on 2026-10-04; canonical constants: `adapt/gatt_ids.hpp`.

| Attribute | UUID | Proposed semantics |
|---|---|---|
| Service | cf983bae-e1b0-454c-9b46-3d63362c42c4 | Custom ADAPT Control service |
| Device State | 3f34890e-93f9-4e64-970b-bc590eaaa343 | Read snapshot; notify lifecycle updates |
| Action Event | e9cfdc67-3639-4065-8d93-51575c03cc86 | Notify ACTION_EVENT |
| Command | 8be7fad9-8441-4ec6-a35a-cd999c02ff73 | Explicit write request; notify ACP replies |
| Configuration | 8c834302-3af3-4db8-bd95-f992cd789065 | Explicit write request for SET_MAPPING/SET_SETTING/GET_SETTING; notify replies |
| Diagnostics | d8c2a165-1836-423b-88dd-340127abaca5 | Authorized read; notify LOG_EVENT, query retained log via Command |
| Firmware Metadata | f8ee1cd2-c5f7-4c95-84ee-545d1e838503 | Read metadata |

Writes and notifications use fragment header: frame ID u16 LE, byte offset u8,
total complete ACP frame length u8, followed by 1+ bytes. At default ATT MTU23,
20-byte characteristic values leave 16 bytes for each fragment. Never exceed the
negotiated ATT payload. In-order contiguous fragments only; one bounded 140-byte
buffer per peer. Offset0 starts/replaces a frame; IDs/total/characteristic must
match until complete. A gap, malformed length/CRC/type/flag, time regression,
1000 ms inter-fragment timeout or disconnect clears assembly. Partial frames never
reach the core. Notification clients use the same rules. Read snapshots are ACP
frames read by offset (ATT long-read) and must be stable for a read transaction.

`gatt::Ingress` is tested policy/reassembly only, not a peripheral or vendor stack.
A real adapter must reject write-without-response, authenticate/encrypt the peer
using SDK pairing/bonding and user-approved ownership, enforce ATT access controls
on reads/CCCD subscriptions, serialize callbacks, and schedule bounded output.
`authorized()` must originate from that verified session, not the CRC, UUID,
device name or a caller-supplied boolean. A disconnect clears trust/reassembly.
No arbitrary address, memory-write, script-execution or image-upload command exists.
Boot commands retain the independent HAL restoration/permission gate.

UUIDs are project-owned and do not reuse EPOS USB IDs, Bluetooth company IDs,
certification markings or official firmware identity. Any later retention of an
identifier needs a compatibility justification before implementation. Semantic
changes require protocol/version review; optional Phase 2 operations use capability
bits and preserve the original frame layout.
