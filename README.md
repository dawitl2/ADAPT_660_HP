# ADAPT 660 HP / ADAPT Control

Replacement-firmware research and host simulation for the EPOS ADAPT 660.
**No physical firmware target is supported yet. Never flash a host build.**

Phase 2: C++17 core/HAL, lifecycle/watchdog recovery, protected button mappings,
versioned settings, original tones, retained diagnostics and typed standard
controls. Shared v0.1 protocol has compatible capability-gated extensions.
The host simulator and authenticated TCP bridge support development without
physical firmware. Android and PC applications await later phase prompts.

## Host build

CMake 3.16+, C++17 compiler; Python 3.11+ for simulator bridge/BLE tools.

```powershell
cmake -S firmware -B build -G "MinGW Makefiles"
cmake --build build
ctest --test-dir build --output-on-failure
python -m unittest discover -s tools/tests -v
build/adapt_sim.exe
```

Visual Studio: omit generator; pass `--config Debug` to build/test.
Linux/macOS: omit generator and use `build/adapt_sim`.
See [project](docs/PROJECT.md), [protocol](docs/protocol.md),
[assumptions](docs/firmware/assumptions.md), [research](docs/firmware/reverse-engineering.md).

`help` lists simulator commands. [Simulator usage](firmware/simulator/README.md)
and [research toolkit](tools/README.md) include repeatable examples and limitations.
Windows/Linux CI runs CMake/CTest and offline Python tests. All embedded addresses,
firmware images and restore procedures remain unverified; host outputs are simulation.

Start the [Android/ADB bridge](firmware/simulator/bridge.md):

```powershell
python firmware/simulator/bridge.py --simulator build/adapt_sim.exe --settings research/sim-settings.acfg
```

The compiled `target_adapt660_pending` is a host-only failure/unknown-state skeleton.
`-DADAPT_PHYSICAL_TARGET=ON` deliberately fails; no flashable image is produced.
See [Phase 2 evidence](docs/firmware/phase2-evidence.md), [standard features](docs/firmware/standard-behavior.md),
[BLE contract](docs/firmware/control-transport.md), [configuration](docs/firmware/configuration.md),
and [lifecycle](docs/firmware/lifecycle.md).
