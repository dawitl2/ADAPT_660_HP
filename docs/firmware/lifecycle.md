# Custom connection lifecycle (ASSUMED policy)

States are OFF=0, BOOTING=1, PAIRABLE=2, CONNECTING=3, CONNECTED=4,
AUDIO_ACTIVE=5, CALL_ACTIVE=6, ANALOG_MODE=7, USB_MODE=8, RECOVERY=9, ERROR=10.
Calls take precedence over music, analog over wireless, and USB audio over normal
wireless activity. CONNECTING with no active attempt means idle reconnect policy;
it does not assert that a connection exists. Peer count and active peer are separate
observations, so losing one of two connections does not imply total disconnect.

Sleep/power-off cancels pending recovery; wake traverses BOOTING. Analog insertion
cancels radio recovery and requests radio disable through the existing mode HAL.
Removal requests a wireless-only restart, then ordinary vendor reconnection.
No policy code implements pairing cryptography, an audio profile or a radio stack.

The HAL must provide a meaningful nonblocking responsiveness/connection-attempt
observation. An unresponsive stack for 2000 ms, or a connection attempt stalled
for 5000 ms, requests `recover_wireless`. At most three failed restart attempts
occur, with 500/1000 ms delays, then ERROR. Manual protected recovery can request
another bounded cycle. Successful restart does not invent a connected peer.
Unavailable radio hardware produces ERROR, never simulated success.

These software defaults are replaceable in `RecoveryPolicy` and tested on the
host. A target must adapt scheduler timing and SDK timeouts to measured behavior.
`recover_wireless` must be a bounded operation scheduled on the vendor radio task;
it may restart only radio/audio facilities where supported, preserving independent
configuration and charging. If the SDK cannot restart that subsystem, report
failure; do not silently escalate to a full physical power cycle. A hardware
watchdog and its exact reset scope remain UNKNOWN. This custom defensive policy
does not establish the cause of the user's original EPOS Bluetooth lockup.
