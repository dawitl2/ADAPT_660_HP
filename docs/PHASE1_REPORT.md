# Phase 1 implementation report — 2026-10-04

## Delivered

C++17 allocation-free firmware core with all requested HAL boundaries; host mock
backend; configurable, debounced purple-button interpreter; protected very-long
pairing; shared C99 v0.1 protocol; validated configuration/settings transactions;
synthetic jack recovery state machine; interactive simulator with JSON process
pipes and incoming binary frames. Audio/USB/Bluetooth hardware remains simulated.

Research tools include present-only Windows PnP/interface/driver inventory,
optional standard USB/HID descriptor reads without interface claims or driver
changes, snapshot diff, vendor package candidate search, streaming SHA-256,
bounded recursive ZIP analysis/extraction, strings, entropy, signatures and
architecture clues. Binwalk integration is optional. Android/PC remain documented
boundaries; no Android UI, real assistant session or firmware target was created.

## Directory tree

```text
ADAPT_660_HP/
├── .github/workflows/host-validation.yml
├── .gitignore, .gitattributes, README.md
├── docs/
│   ├── PROJECT.md, ROADMAP.md, protocol.md, PHASE1_REPORT.md
│   ├── firmware/{architecture,assumptions,hardware-map,recovery,reverse-engineering}.md
│   └── android/architecture.md
├── firmware/
│   ├── CMakeLists.txt
│   ├── include/adapt/{button,core,hal,host,model}.hpp
│   ├── src/core/{button,core}.cpp
│   ├── src/platform/README.md
│   ├── tests/{button_tests,core_tests}.cpp, check.hpp
│   └── simulator/{main.cpp,test_simulator.py,README.md}
├── protocol/{CMakeLists.txt,include/,src/,tests/,docs/}
├── tools/
│   ├── usb/{inventory.ps1,descriptors.py,compare.py,requirements.txt,README.md}
│   ├── firmware/{analyze.py,find_packages.py,hash.py,make_fixture.py,README.md}
│   ├── tests/{test_firmware,test_search,test_usb}.py
│   ├── bluetooth/README.md, capture/README.md, README.md
├── android/README.md, pc/README.md, assets/README.md, captures/README.md
└── research/, build/, build-release/, .venv/ (local, ignored)
```

## Validation (local CONFIRMED results)

- GCC 12.2, CMake 4.3.2, Python 3.12 on Windows. C/C++ warnings treated as errors.
- Default host build and separate Release build: successful; CTest **4/4** each
  (button, core, simulator process flow, protocol).
- Protocol: all 23 valid wire shapes, CRC reference vector, 64-bit action fields,
  malformed lengths/types/versions/flags/checksums and 10,000 seeded invalid frames.
- Simulator: actions 1/2/3, protected pairing, mappings, settings retained over
  synthetic reboot, analog transitions, malformed commands and boot policy;
  independently encoded Python PING round-trips through C firmware as PONG.
- Research tooling: **14 Python tests passed**, including bounded expansion,
  recursive ZIPs, safe extraction, traversal/symlink/case-collision rejection,
  entropy/hash/string checks, inventory diff and no-claim USB read shim.
- Analyzer executed on synthetic 684-byte ZIP with manifest and nested binary;
  extraction and hashes verified. This is test data, not EPOS firmware.
- Windows inventory ran and captured 15 present devices/interfaces; self-comparison
  returned no differences. Optional PyUSB 1.3.1/libusb-package 1.0.30.0 installed
  in ignored venv; selected absent-device probe returned a valid empty report.
- Git whitespace checks and tracked-file review: no secrets, vendor images,
  captures, generated builds or signing materials included.
- Windows/Linux GitHub Actions workflow added; local results do not imply that
  hosted CI has already completed. See Actions for current remote result.

## Hardware/firmware findings and limits

**CONFIRMED:** no ADAPT device was present in USB/HID/audio inventory; no EPOS
Connect installation was identified in standard uninstall registry keys. Filename
search of seven standard roots found no usable official package/cache/log; the
only candidate was repository metadata. Access-error records are capped at 100;
inaccessible/skipped locations remain unsearched. No unofficial firmware download.

**CONFIRMED documentation:** direct USB updates, manual ZIP selection, stock
pairing/history-reset controls and a recovery-mode reference for the AMC variant.
See [recovery](firmware/recovery.md) for primary sources and exact scope.

**ASSUMED:** button defaults, replacement firmware feasibility, HAL/synthetic
states, v0.1 wire format and the cause modeled by wireless recovery after analog
use. Stock analog behavior is documented; the proposed bug mechanism is unproven.

**UNKNOWN:** exact variant/SoC/SDK/pins/DSP/flash; actual control endpoints;
image version/content/signing/encryption; update-mode identity; rollback and a
verified stock restoration path. No physical flash/update/reset was attempted.

Private evidence remains in ignored `research/`. Existing user file `preTEXT`
was left untouched and excluded locally through `.git/info/exclude`; it was not
committed. Working-tree cleanliness includes those explicit research exclusions.

## Implementation commits

```text
e422207 chore: establish monorepo boundaries and research exclusions
7c0aa35 firmware: implement configurable debounced purple button interpreter
348480f test: cover button boundaries noise and rapid repeated presses
8cabcc2 firmware: define platform HAL and explicit hardware assumptions
9d0dd9e protocol: add versioned transport independent C codec and framing tests
842ea87 firmware: connect HAL state actions settings and protected recovery commands
bd9ffb9 sim: add interactive headset simulator and JSON pipe transport
8f5150c tools: add read only Windows USB inventory descriptor reads and snapshot diff
d7adb15 fix: avoid reserved PowerShell PID variable in USB inventory
0348271 tools: add bounded recursive firmware analysis and safe ZIP extraction
191ae08 tools: locate legitimate vendor package cache and log candidates
929d39c fix: prevent implicit USB interface claims and load bundled descriptor backend
0aa7193 docs: record official recovery evidence hardware unknowns and jack hypothesis
b1fa65e test: cover every protocol shape and enforce simulated bootloader lifecycle
9c75ffe ci: validate host firmware protocol simulator and research tools on Windows and Linux
3e8e6db test: verify Python client interoperability through simulator frames
```

The final report commit follows these implementation commits. To reproduce the
complete phase list: `git log --reverse --oneline c6b3c1d..HEAD`. Preserve history
and push `main` to the verified `dawitl2/ADAPT_660_HP` origin without force/squash.
