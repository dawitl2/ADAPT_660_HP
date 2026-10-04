# Bluetooth research boundary

No EPOS service UUID, vendor opcode, pairing key or SoC is known. v0.1 is our
proposed protocol, not an observed headset Bluetooth channel. Simulator control
and audio interfaces are separate so BLE/GATT or another control channel can be
added later without encoding transport details in packets.

For later captures, record headset variant, peer/OS, transport profile, timestamps,
button/jack gestures and link/audio state. Capture only your own controlled session;
keep MAC addresses, pairing material and audio out of Git. Android Bluetooth HCI
snoop or a dedicated air sniffer may expose different layers; document the capture
method and limitations before interpreting it. Do not write unknown GATT attributes
or assume audio/control compatibility based on a device name.
