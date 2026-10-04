# Firmware architecture

Core C++17 uses fixed-sized state and HAL references. `hal.hpp` defines independent
button/touch, Bluetooth control/audio, USB control, ANC, microphones, speakers,
battery/charging, jack/wear, settings, feedback, clock and boot/recovery boundaries.
No vendor SDK or hardware register is referenced. A target may wrap C vendor APIs
behind these interfaces; the protocol public boundary is C-compatible.

Host backend uses virtual time, in-memory settings and process pipes. State is
synthetic. Settings survive simulated reboot within a process, not process exit.
Unavailable sensors explicitly report unknown. Production backend must provide
atomic durable storage, debounce sampling cadence, authenticated commands,
nonblocking transports, complete-frame reassembly and a verified recovery policy.

Button edges are backdated to the first sample of a stable level. Sampling must
be monotonic and frequent enough to observe each edge. Long triggers on release;
very long triggers once while held. This avoids an AI action before pairing.
