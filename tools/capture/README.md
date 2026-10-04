# Capture setup

Phase 1 does not install kernel capture drivers, reboot Windows, initiate firmware
updates or interrupt updates. Firmware/USB evidence goes in ignored `research/` or
`captures/`; packets may include other devices and private data.

For a future authorized, normal official update:

1. Install [Wireshark](https://www.wireshark.org/download.html) from its official
   distribution. Its Windows installer offers USBPcap; choose it if needed.
   [USBPcap](https://desowin.org/usbpcap/) is a Windows USB capture driver and its
   installation may require administrator rights and a restart. No automated
   driver installation is performed by this project.
2. Use the USBPcap interface for the root hub containing the directly connected
   headset. Record initial VID/PID and port path; disconnect unrelated devices
   before starting the session when convenient. Filter a saved copy by device
   address after inspecting enumeration; an update may change that address.
3. Collect before/during/after inventories with `tools/usb/inventory.ps1`, annotate
   official Connect version, firmware version and package hash, and preserve
   USB SETUP/descriptor exchanges. Never unplug or cancel mid-update to test recovery.
4. Compare snapshots. Treat changes as observations; HID/vendor payload meanings,
   bootloader state and restoration guarantees remain UNKNOWN without evidence.
5. Stop capture after completion. Keep original evidence private and immutable;
   publish only reviewed, redacted summaries, never audio/keys or vendor binaries.

Binwalk/Wireshark/USBPcap were unavailable on the inspected host. The standard
library analyzer and Windows inventory run without them.
