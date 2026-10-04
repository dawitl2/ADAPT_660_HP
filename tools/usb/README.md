# Read-only USB toolkit

```powershell
./tools/usb/inventory.ps1 -Output research/before.json
# Repeat while a normal, user-initiated official update is running, without interruption:
./tools/usb/inventory.ps1 -Output research/during.json
python tools/usb/compare.py research/before.json research/during.json --output research/usb-diff.json
```

Windows inventory requires built-in PnpDevice/CIM; no extra dependency, administrator
mode or driver replacement. Includes present USB/HID/Bluetooth and audio interfaces,
OS strings, hardware/compatible IDs, interface class/subclass/protocol when exposed,
parent/location and signed-driver information. Reported PnP properties are not raw
USB descriptor bytes. Device IDs and locations may be private.

Optional PyUSB standard descriptor reads (requires accessible existing USB backend):
```powershell
python -m venv .venv
.venv/Scripts/python -m pip install pyusb libusb-package
.venv/Scripts/python tools/usb/descriptors.py --vid XXXX --pid YYYY --hid
```
Use observed VID/PID, not guessed values. Existing Windows audio/HID drivers may
deny access. Script never sets configuration, claims interfaces, detaches drivers,
writes endpoints or sends vendor commands. Missing access is recorded, not worked
around by replacing headset drivers. Raw HID reads are only for accessible active
configuration/alternate 0; current nonzero alternate settings remain a limitation.
PyUSB docs: https://github.com/pyusb/pyusb/blob/master/docs/tutorial.rst
For device-level descriptor inspection with native Windows drivers, Microsoft's
USBView is another option: https://learn.microsoft.com/windows-hardware/drivers/debugger/usbview
