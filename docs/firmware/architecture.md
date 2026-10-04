# Firmware architecture

Core C++17 uses fixed-sized state and HAL references. `hal.hpp` defines independent
button/touch, Bluetooth control/audio, USB control, ANC, microphones, speakers,
battery/charging, jack/wear, settings, feedback, clock and boot/recovery boundaries.
No vendor SDK or hardware register is referenced. A target may wrap C vendor APIs
behind these interfaces; the protocol public boundary is C-compatible.

Host backend uses virtual time, process pipes and optional atomic file settings.
State is synthetic. Settings survive simulated reboot; with `--settings`, they
also survive process exit. The authenticated loopback TCP bridge consumes the same
frames and provides Android/ADB development inputs without headset hardware.
Unavailable sensors explicitly report unknown. Production backend must provide
atomic durable storage, debounce sampling cadence, authenticated commands,
nonblocking transports, complete-frame reassembly and a verified recovery policy.

Button edges are backdated to the first sample of a stable level. Sampling must
be monotonic and frequent enough to observe each edge. Long triggers on release;
very long triggers once while held. This avoids an AI action before pairing.

Phase 2 extends this core, retaining Phase 1 packet/button APIs: lifecycle policy
and bounded watchdog/analog recovery, fixed diagnostic ring, versioned endian-explicit
configuration, original sine acknowledgements, optional typed standard/touch
controls and authenticated GATT reassembly. Vendor SDK/controller/audio-stack code
belongs behind HAL; no audio profile implementation is substituted with control
packets. Host allocation/OS storage is confined to the simulation platform.
`target_adapt660_pending` compiles an explicit unknown/failure backend; physical
target configuration is disabled until hardware and restoration evidence exist.
