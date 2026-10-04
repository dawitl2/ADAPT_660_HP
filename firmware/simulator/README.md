# Host simulator

`build/adapt_sim.exe` (Windows) or `build/adapt_sim`. Enter `help` for commands.
Virtual time advances only through commands; `down`, `tick`, `up` permit custom
gestures/noise. Convenience short/double/long/verylong use development defaults;
after timing changes use `press MS` or raw edges. `set KEY VALUE` and `map G A`
send real serialized configuration frames to the core. `rx HEX` accepts a complete
binary frame. Events are newline-delimited JSON with payload/frame hex and decoded
action/log fields; help goes to stderr. A PC client can spawn the process and pipe
stdin/stdout without sockets. Android direct pipe communication needs a later bridge.

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
