# Phase 2 evidence audit — 2026-10-04

Baseline: `3340b1d`. Read existing tracked documentation, firmware/protocol/tool
sources, tests and chronological commit history before implementation. Phase 1
host validation remains 4/4 passing. Private research consists of OS inventory,
empty absent-device descriptors, filename search and synthetic ZIP analysis; no
actual update capture or original headset image exists in this evidence set.

| Question | Status | Evidence / remaining work |
|---|---|---|
| Device family | CONFIRMED user identification | EPOS/Sennheiser ADAPT 660; exact article/board revision not observed |
| Stock Bluetooth profiles | CONFIRMED documentation | HFP/HSP, AVRCP and A2DP; guide p8 |
| Stock multipoint | CONFIRMED documentation | Two connected peers, up to eight stored pairings; guide p8 |
| ANC, microphones, touch, ambient, charging, USB audio and analog | CONFIRMED documentation | Guide product overview and operation sections; electrical implementation unknown |
| Stock pairing / USB update | CONFIRMED documentation | Guide p9/p27; official Connect supports update-from-ZIP |
| Main Bluetooth/audio SoC | UNKNOWN | No headset descriptors, verified package or verified component record |
| Separate MCU / DSP / flash / touch controller | UNKNOWN | Product functions do not identify ICs |
| ANC implementation / button routing | UNKNOWN | No board electrical map or SDK; no guessed pins |
| USB boot/update architecture | UNKNOWN | An official update workflow does not identify its boot protocol or allow custom images |
| Concurrent BLE and Bluetooth audio | UNKNOWN | Bluetooth version alone does not establish concurrent custom GATT support |
| Custom replacement feasibility | ASSUMED | Software/HAL design target only |
| Wireless restart resolves reported lockup | ASSUMED | Defensive custom policy, not a diagnosis of the original firmware |

Primary sources reviewed:

- [EPOS ADAPT 66X guide A07](https://shop.eposaudio.com/globalassets/__pim/products/adapt-600/adapt-660/3748334b-be64-4be8-b6d8-8dc815d4a328_51128_adapt66x_userguide_a07_1124_en_int_original.pdf).
- [Official support, article 1000200](https://demant-epi.eposaudio.com/en/us/products/adapt-660-bluetooth-headset-1000200/support) lists conformity documents and several distinct variants. This does not identify the user's article number.
- [EPOS Developer Portal](https://demant-epi.eposaudio.com/en/us/software/developer-portal) describes desktop USB integration/call-control SDKs for Windows/macOS/Linux. It supplies no confirmed embedded SDK or headset firmware build route in the public page.
- The linked EU declaration PDF returned no extractable text through the web tool,
  and direct downloads returned HTTP 403. Its contents were not established.
  Public certification search results suggested SCBT13/R3USCBT13 and attachment
  4664899; the primary FCC attachment was inaccessible. This is an **UNKNOWN
  verification lead**, not confirmed hardware identity. No third-party chipset
  assertion or unsolicited internal board photograph is used.

No known-good stock image, SHA-256, bootloader behavior or tested restoration
procedure is available. Hardware writes remain blocked. Next useful evidence:
exact article/regulatory label, direct-headset USB inventory/descriptors with the
existing driver, legitimate EPOS package/cache and its provenance/hash, and
official recovery instructions for that exact variant. Only if necessary later,
user-supplied inspection photos can establish component markings and revision.
No programming voltage, debug connector or reset sequence is prescribed.

See [recovery](recovery.md) for the original restoration gate. The smallest future
physical proof must use a verified vendor-supported reversible route; the host
pending-target executable is never such a proof and cannot be flashed.
