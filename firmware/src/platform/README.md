# Physical backend boundary

No physical backend exists. The compiled `target_adapt660_pending` skeleton
explicitly returns unknown observations/failure and denies boot/write operations.
`ADAPT_PHYSICAL_TARGET=ON` fails configuration; it produces no flash image.
Implement `adapt/hal.hpp` using verified vendor SDKs
once SoC/board/recovery are identified. Do not compile a host executable as firmware.
The in-memory simulator backend is `include/adapt/host.hpp`.
