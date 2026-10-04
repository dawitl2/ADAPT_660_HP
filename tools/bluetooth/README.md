# Bluetooth research boundary

No EPOS service UUID, vendor opcode, pairing key or SoC is known. v0.1 is our
proposed protocol, not an observed headset Bluetooth channel. Simulator control
and audio interfaces are separate so BLE/GATT or another control channel can be
added later without encoding transport details in packets.

Phase 2 adds optional metadata inspection using [Bleak](https://bleak.readthedocs.io/en/latest/api/client.html):
`python -m pip install -r tools/bluetooth/requirements.txt`, then
`python tools/bluetooth/services.py --address OBSERVED_ADDRESS`.
It scans for the explicitly selected device, briefly connects with `pair=False`,
enumerates service/characteristic/descriptor metadata, then disconnects. No value
reads, application writes, subscriptions or pairing reset. Existing OS pairing may
be used. A failed scan/connection is recorded as UNKNOWN; do not replace drivers or
try undocumented commands. Metadata alone cannot prove concurrent audio or a
custom service deployment route. Addresses remain in private research evidence.

For later captures, record headset variant, peer/OS, transport profile, timestamps,
button/jack gestures and link/audio state. Capture only your own controlled session;
keep MAC addresses, pairing material and audio out of Git. Android Bluetooth HCI
snoop or a dedicated air sniffer may expose different layers; document the capture
method and limitations before interpreting it. Do not write unknown GATT attributes
or assume audio/control compatibility based on a device name.
