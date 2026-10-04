# ADAPT 660 HP / ADAPT Control

Replacement-firmware research and host simulation for the EPOS ADAPT 660.
**No physical firmware target is supported yet. Never flash a host build.**

Phase 1: C++17 firmware core, HAL, shared v0.1 protocol, simulator and read-only
research tools. Android and PC applications await later phase prompts.

## Host build

CMake 3.16+, C++17 compiler; Python 3.10+ for research tools.

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
