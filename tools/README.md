# Research tools

| Tool | Purpose | Dependencies |
|---|---|---|
| `usb/inventory.ps1` | Present-only Windows PnP/driver/interface snapshot | built-in PnpDevice/CIM |
| `usb/descriptors.py` | Selected device strings/configurations/HID descriptor reads | optional pinned PyUSB/libusb-package |
| `usb/compare.py` | Added/removed/changed identities across snapshots | Python standard library |
| `firmware/find_packages.py` | Vendor-named package/cache/log candidates | Python standard library |
| `firmware/hash.py` | Streaming SHA-256 of arbitrary-size files | Python standard library |
| `firmware/analyze.py` | Bounded recursive ZIP/container/strings/entropy report | Python standard library; optional Binwalk |
| `firmware/make_fixture.py` | Create explicitly synthetic analysis input | Python standard library |

```powershell
python -m unittest discover -s tools/tests -v
python tools/firmware/make_fixture.py
python tools/firmware/analyze.py research/synthetic-package.zip --output research/synthetic-report.json
python tools/usb/compare.py research/usb-phase1.json research/usb-phase1.json
```

Use fresh extraction directory names when re-running `--extract`. Reports, packages,
captures and extracted binaries stay in ignored research locations. Tool limitations
and optional capture setup are documented in each subdirectory. A CI job builds
and tests the host on Windows/Linux; CI evidence is separate from local results.
