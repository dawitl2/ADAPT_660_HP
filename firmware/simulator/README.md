# Host simulator

`build/adapt_sim.exe` (Windows) or `build/adapt_sim`. Enter `help` for commands.
Virtual time advances only through commands; `down`, `tick`, `up` permit custom
gestures/noise. Convenience short/double/long/verylong use development defaults;
after timing changes use `press MS` or raw edges. `set KEY VALUE` and `map G A`
send real serialized configuration frames to the core. `rx HEX` accepts a complete
binary frame. Events are newline-delimited JSON with payload/frame hex and decoded
action/log fields; help goes to stderr. A PC client can spawn the process and pipe
stdin/stdout without sockets. The [Phase 2 TCP bridge](bridge.md) supports Android/ADB development.

Example:
```text
short
double
long
verylong
battery 42
anc 3
jack in
jack out
bt connect
map 1 16
short
reboot
allow-boot on
reboot
state
quit
```

Boot requests default to denied. `allow-boot on` enables host-only simulated
requests; it is not a physical bootloader permission. `power-on` recreates the
core, retaining in-process settings. No binary is sent to a headset.
After entering simulated bootloader, firmware commands/gestures are blocked until
`power-on`. This host mode provides no image upload or flash operation.

Phase 2 adds `lifecycle`, `metadata`, `diagnostic INDEX`, peer count/active-peer,
audio/call/USB modes, sleep/wake, power, radio responsiveness, restart failures and
transport disconnect controls. `help` lists the exact commands. `tick MS` advances
watchdog/retry deadlines without real-time waiting. Numeric diagnostic records
and original tone patterns appear as JSON; no microphone audio is captured.

`adapt_sim --settings research/sim-settings.acfg` retains validated settings across
process restarts. Without the option, settings remain in memory. A corrupt file
emits `configuration_fallback`, uses defaults and remains untouched until an
explicit save. Storage files and bridge authentication tokens stay out of Git.
`barrier ID` emits a flush marker for serialized process clients; it performs no
firmware command. Bootloader mode still blocks every firmware command until power-on.
