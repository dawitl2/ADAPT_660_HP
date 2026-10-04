# Android/ADB simulator transport

Python 3.11+ standard library, compiled host simulator, no headset/Android app required:

```powershell
python firmware/simulator/bridge.py --simulator build/adapt_sim.exe --port 6600 --settings research/sim-settings.acfg
adb reverse tcp:6600 tcp:6600
```

On Linux use `build/adapt_sim`. Future S20+ code opens a TCP socket to
`127.0.0.1:6600` after ADB reverse; an emulator can use its documented host access
route. The server always binds Windows/host loopback, has one authenticated client,
and prints its listening port/token-file location. It never exposes a LAN listener.
Stop with Ctrl+C or send `{"shutdown":true}` after authentication; shutdown also
closes the child simulator. It spawns only the explicit simulator executable, without a shell.

Wire format is UTF-8 JSON per line (maximum 2048 bytes). First send
`{"auth":"TOKEN_FROM_PRIVATE_FILE"}`. The generated 256-bit token stays in ignored
`research/bridge-token.txt`; do not commit, echo or use it as a production credential.
Windows users should retain normal per-user directory ACLs; chmod is not a Windows
ACL replacement. This unencrypted development transport relies on local OS/ADB
access. Production BLE must use authenticated encrypted SDK sessions separately.

After `kind=ready`, initial firmware messages (HELLO, capabilities, state, lifecycle,
metadata) use `kind=firmware, request_id=0`, followed by `kind=complete`. Send either:

```json
{"id":1,"debug":"short"}
{"id":2,"debug":"battery 42"}
{"id":3,"debug":"anc 3"}
{"id":4,"debug":"bt connect"}
{"id":5,"debug":"peers 2"}
{"id":6,"debug":"jack in"}
{"id":7,"debug":"jack out"}
{"id":8,"frame_hex":"COMPLETE_ACP_FRAME_AS_HEX"}
```

`id` is positive u32. `frame_hex` carries the identical C codec bytes used by the
firmware; Python does not reinterpret or generate a vendor protocol. The last
example is a format placeholder, not a valid frame. Debug commands are explicitly
allowlisted simulation inputs. No arbitrary process launch, path, shell, boot
permission toggle or image upload is accepted. Raw ACP boot requests still fail
the simulator's default HAL gate. Every command then queries state and lifecycle,
so connection, battery, ANC and purple ACTION_EVENT can be received immediately.
Virtual time advances only through gestures/`tick`; watchdog scenarios are deterministic.

Each firmware envelope includes `request_id`, original decoded fields and
`wire_hex`. ACP sequence numbers remain separate from bridge request IDs. Duplicate
mock Bluetooth/USB copies are suppressed per transaction. `kind=complete` flushes
the command's outputs; it does not mean Android performed the action. Reconnect
keeps the running simulator/settings but authenticates again and sends fresh state.
Idle frames are not generated without virtual-time/input changes. Read timeouts,
malformed/oversized lines, unauthorized clients and stalled socket writes are
bounded; an incomplete transaction is drained before reuse. Transport fault debug
commands can suppress firmware outputs while the bridge connection remains live.

No Android application, AI integration or physical Bluetooth peripheral is created
in Phase 2. This endpoint is the Phase 3 development contract.
