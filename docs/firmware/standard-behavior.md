# Preserving standard headset behavior

Core owns action/configuration/lifecycle policy; a legal vendor SDK must own the
controller, audio codecs, A2DP/HFP/HSP as applicable, AVRCP and secure pairing.
No Bluetooth stack, DSP firmware or proprietary audio code is recreated here.
The pending target cannot stream or preserve physical stock features yet.

| Feature | Host implementation | Physical status |
|---|---|---|
| Pairing / purple button | HAL pairing + independent protected hold | Routing/SDK UNKNOWN |
| A2DP music / HFP call + microphones | Synthetic audio/call/microphone states | Vendor profile/codec integration pending |
| AVRCP / call controls | Typed PlayPause/Next/Previous/Answer/End/Mute operations | Vendor command adapter pending |
| Volume | Validated 0–100 control + touch steps | Gain calibration/SDK pending |
| ANC / TalkThrough-style ambient | ANC settings + ambient HAL | ANC DSP/control algorithm UNKNOWN |
| Battery / charging | Explicit observed or unknown fields; simulated inputs | Measurement/calibration/charger unchanged until verified |
| Multipoint | 0–2 synthetic peers, active peer; persistent preference | SDK/controller limits UNKNOWN |
| USB audio / analog | Lifecycle observations; analog cancels wireless recovery | USB audio endpoints/jack circuitry UNKNOWN |
| Touch / wear | Synthetic interpreted gestures; raw touch/wear can remain unavailable | Controller/sensor and gesture decoding UNKNOWN |
| Feedback | Original sine patterns + bounded HAL signals | Mixer/LED adapter pending |

Optional `hal::StandardControls` exposes capability bits (volume/media/ambient/
multipoint), typed operations and interpreted touch events. Unsupported hardware
fails explicitly. Core consumes at most one queued touch per tick: tap toggles
music or ends an active call, double tap toggles ambient, vertical swipes step
volume by five with clamping, horizontal swipes select next/previous. These are
**ASSUMED custom mappings**, not a claim about exact stock gesture semantics.
Sleep/off suppresses controls from physical inputs. Remote standard controls
require authenticated transport. Existing peers are not erased by recovery.

Multipoint preference is stored even on unsupported hardware; if supported, HAL
application and storage use rollback on failure. Disabling it while two simulated
peers are connected fails instead of silently dropping a peer. Host max-peer
report then reflects 1 or 2. Real SDK must define its compatible disconnect policy.
No hardware pairing list erase, update, full reset or power-cycle escalation occurs.
