# Phase 2 implementation report — 2026-10-04

Continues Phase 1 at `3340b1d` in the existing `dawitl2/ADAPT_660_HP` checkout.
Phase 3 Android/application work has not started. No physical flash, vendor command,
pairing reset, debug-voltage guess or proprietary firmware import occurred.

## Hardware and physical integration

**CONFIRMED local evidence:** Windows now enumerates seven headset-named A2DP,
HFP/AVRCP and LE records among 110 present records, using signed Microsoft drivers.
No direct-headset USB identity was found. A bounded scan of the observed LE address
did not find an advertising device, so no GATT connection/write was made.
Standard-root package search found one repository metadata JSON, no usable vendor
firmware, and 100 retained access errors. Private records stay in ignored research/.

**CONFIRMED documentation:** standard audio profiles and stock two-peer multipoint,
USB audio/update workflow and analog switching. **UNKNOWN:** exact article/board
revision, regulatory-label match, Bluetooth/audio SoC, separate MCU, DSP, flash,
touch controller, ANC electrical implementation, button routing, USB update-mode
descriptors, embedded SDK, signing, bootloader behavior and restoration procedure.
The SCBT13 certification search lead is unverified. Desktop EPOS call-control SDK
documentation is not an embedded custom-firmware SDK. See [evidence/source register](firmware/phase2-evidence.md).

**ASSUMED design:** replacement firmware feasibility, custom timing, lifecycle,
watchdog/recovery mechanisms, BLE service and wire/configuration schemas. Neither
the proposed wireless restart nor an analogy with another headset diagnoses the
original EPOS lockup or identifies its radio chip.

## Implemented firmware and targets

| Area | Result |
|---|---|
| Architecture | Existing C++17 core/C99 protocol/HAL extended; no stylistic rewrite or embedded registers |
| Real-target build | Disabled intentionally. `ADAPT_PHYSICAL_TARGET=ON` refuses configuration; no image/linker script/SDK invented |
| Pending target | `target_adapt660_pending` builds/runs on host, reports unknown sensors/radio, returns failures, denies boot and physical writes |
| Simulator platform | Functional host HAL, deterministic virtual time, interactive NDJSON process plus TCP bridge; host storage under `src/platform/sim/` |
| Purple button | Default assistant/note/study actions, authenticated configurable short/double/long mappings, validated configurable timings, protected very-long pairing/recovery |
| Persistence | Endian-explicit schema2 record with integrity check, schema1 migration, safe corruption defaults, newer-schema preservation; optional atomic host file replacement |
| Feedback | Four original bounded sine patterns with onset/release ramps; normal confirmation toggle, protected recovery indication retained |
| Standard behavior | Typed volume/media/call/ambient controls, interpreted touch mappings and multipoint preference through optional HAL; no radio/audio stack reimplementation |
| Lifecycle | All eleven requested states; distinct peer count/active peer, audio/call/USB/analog modes, disconnect/reconnect and sleep/wake |
| Recovery | Wireless-only HAL restart; 2000 ms responsiveness and 5000 ms attempt watchdogs, three failed attempts with delay then ERROR; analog-disable retries bounded too |
| Diagnostics | Fixed 32-record volatile ring, numeric event/time/serial fields, authorized retained-log query, live logs and lifecycle notifications; no private audio |
| Identity | ADAPT 660 HP / ADAPT Custom Firmware / ADAPT Control Protocol; no official identifiers/firmware impersonation |

Physical A2DP/HFP codecs, DSP/ANC, USB audio, charging and touch drivers remain SDK/
board integration work. Their host states are simulation, never a successful real
firmware takeover. Target NVM transactions, scheduler/audio-task integration,
authenticated ownership, hardware watchdog and calibrated output remain unverified.
See [standard-feature matrix](firmware/standard-behavior.md) for precise limits.

## Transport and Android handoff

Original v0.1 header/CRC and message IDs1–14 are preserved. Capability-gated optional
diagnostic, lifecycle, identity and standard-control operations are documented in
[protocol extensions](protocol-extensions.md). CRC is not authentication.

Seven project-owned stable UUIDs define the proposed BLE service and state/action/
command/configuration/diagnostic/metadata characteristics. Tested GATT ingress
requires authorized peers and write-with-response, bounds/reassembles fragments,
rejects invalid CRC/flags/lengths/routing, and clears on timeout/disconnect. It is
not a peripheral deployment. SDK concurrent BLE/audio and stock service details
remain UNKNOWN. The verified transport is host process/TCP.

The [TCP/ADB bridge](../firmware/simulator/bridge.md) is loopback-only, authenticates
with a private generated token, accepts serialized ACP frames and allowlisted mock
inputs, supplies initial/current connection/battery/ANC/lifecycle state and button
events, suppresses duplicate mock copies, supports reconnect and bounded shutdown.
No shell execution, memory write or flash upload exists. ADB reverse setup is
documented; the S20+ app itself has not been built/tested in this phase.

```powershell
python firmware/simulator/bridge.py --simulator build/adapt_sim.exe --port 6600 --settings research/sim-settings.acfg
adb reverse tcp:6600 tcp:6600
```

## Boot gate and smallest physical proof

No known-good stock package/image or its SHA-256, exact update/bootloader route,
verified stock restoration, chip SDK/license/toolchain, memory map, GPIO routing
or debug electrical specification is available. All must be established before a
custom hardware write. Stock pairing-list clear is not stock firmware restoration.
Host boot commands require separate authorization/HAL permission and default deny.
No real update or recovery experiment has been run.

Next evidence is exact article/regulatory label and board revision, direct USB
descriptors with the existing driver, a legitimate vendor package with provenance
and SHA-256, and supported recovery instructions/behavior for that exact unit.
If electrical inspection becomes necessary, use user-supplied board markings;
no programmer, voltage, pinout or undocumented reset sequence is guessed.
The first future proof must be a verified reversible vendor-supported operation
(e.g. read-only developer/RAM build or one control notification), never this host
executable. Development continues through the full simulator meanwhile.

## Validation

- Local Windows MinGW GCC12.2 default and Release builds: **16/16 CTest suites**
  passed each; warnings treated as errors. Python3.12 research tooling: **15 tests**.
- All 32 protocol shapes, independent Python CRC/PING over process/TCP, previous
  Phase 1 flows, button boundaries/bounce/remapping and protected recovery retained.
- Configuration migration/truncation/corruption, failed save/rollback, newer-schema
  preservation, restart persistence, diagnostics wrap, original tone bounds,
  standard-control failures, multipoint/touch, lifecycle/watchdog/analog retry and
  mock transport failure scenarios passed.
- Seed66002: **100,000 structured parser/core mutations** (40,284 accepted valid
  frames, 59,716 rejected without partial decode output), plus randomized GATT
  ingress on each iteration. Existing 10,000 malformed-frame corpus also passed.
- Real loopback TCP tests verify authentication, state/actions, malformed requests,
  input limits, reconnect, independent wire compatibility and child shutdown.
- Physical-target refusal exercised locally: expected configuration failure, no
  image built. The compiled pending-target smoke test checks explicit unknown/error.
- Linux Clang ASan/UBSan CI job added alongside Windows MSVC/Linux Release builds.
  Linux Release and ASan/UBSan passed all 16 suites on `766b489`. MSVC exposed
  missing explicit standard string includes in the file adapter and its test;
  both now include their own declarations rather than relying on GCC's transitive headers.
  Hosted results must be read from Actions; local MinGW32 does not supply these
  sanitizer runtimes. Final remote validation is reported separately in chat.
- Git whitespace/tracked-file review: no vendor binaries, audio captures, settings,
  tokens, keys or proprietary files tracked. Existing user `preTEXT` remains
  untouched and locally excluded as disclosed in Phase 1.

## Implementation commits

```text
8c8f76b docs: audit Phase 2 hardware evidence and safe integration blockers
0441143 firmware: add versioned configuration codec and legacy migration
67036e2 firmware: add bounded wireless lifecycle and watchdog recovery policy
19199e1 protocol: add compatible lifecycle diagnostics and custom identity extensions
08297f6 firmware: integrate lifecycle diagnostics and protected configurable actions
43d9a19 firmware: add nonflashable pending ADAPT 660 target and explicit build gate
1fed34b tools: inspect BLE service metadata and record observed headset endpoints
765b3da firmware: synthesize original quiet gesture acknowledgement tones
aec2638 sim: persist versioned settings through atomic host file replacement
20474be sim: expose lifecycle faults diagnostics tones and durable configuration scenarios
0a0775f transport: define custom BLE service and authenticated bounded GATT ingress
6c85356 transport: add authenticated loopback TCP simulator bridge for Android development
0144383 firmware: route standard headset and touch controls through optional vendor HAL
7bf4ff2 test: stress structured protocol inputs and enable Linux ASan UBSan validation
30f1439 fix: bound gesture waits and analog recovery retries and notify peer changes
8d62a2e docs: report Phase 2 implementation validation and physical integration limits
a42accd fix: include standard string declarations explicitly for MSVC file storage
766b489 fix: reset simulated radio state and distinguish software and power boot reasons
```

This report/documentation commit and any validated portability corrections follow
the implementation list. Complete phase history: `git log --reverse --oneline
3340b1d..HEAD`. Preserve all commits; push main without squash or force.
**STOP after Phase 2. Await the user's Phase 3 prompt.**
