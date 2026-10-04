# Physical backend boundary

No physical backend exists. Implement `adapt/hal.hpp` using verified vendor SDKs
once SoC/board/recovery are identified. Do not compile a host executable as firmware.
The in-memory simulator backend is `include/adapt/host.hpp`.
