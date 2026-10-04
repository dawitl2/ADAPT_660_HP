"""Optional PyUSB standard descriptor reads; never claim/set configuration/detach."""
import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


def hid_report_length(extra):
    """Walk interface extra descriptors, return bounded report descriptor length."""
    offset = 0
    while offset + 2 <= len(extra):
        length, kind = extra[offset:offset + 2]
        if length < 2 or offset + length > len(extra):
            return None
        if kind == 0x21 and length >= 6:
            count = extra[offset + 5]
            if length < 6 + 3 * count:
                return None
            for i in range(count):
                start = offset + 6 + 3 * i
                if extra[start] == 0x22:
                    size = int.from_bytes(extra[start + 1:start + 3], "little")
                    return size if 0 < size <= 4096 else None
        offset += length
    return None


def inspect(device, read_hid=False):
    import usb.util
    result = {"identity": f"{device.bus}:{device.address}:{device.idVendor:04x}:{device.idProduct:04x}",
              "vid": f"{device.idVendor:04X}", "pid": f"{device.idProduct:04X}",
              "bcd_device": device.bcdDevice, "device_class": device.bDeviceClass,
              "device_subclass": device.bDeviceSubClass, "device_protocol": device.bDeviceProtocol,
              "strings": {}, "configurations": [], "errors": []}
    for name, number in (("manufacturer", device.iManufacturer), ("product", device.iProduct), ("serial", device.iSerialNumber)):
        try:
            result["strings"][name] = usb.util.get_string(device, number) if number else None
        except Exception as exc:
            result["errors"].append(f"{name}: {exc}")
    try:
        active_config = None
        if read_hid:
            try:
                active_config = int(device.ctrl_transfer(0x80, 0x08, 0, 0, 1, timeout=1000)[0])
            except Exception as exc:
                result["errors"].append(f"active configuration: {exc}; HID reads skipped")
        for config in device:
            interfaces = []
            for interface in config:
                item = {"number": interface.bInterfaceNumber, "alternate": interface.bAlternateSetting,
                        "class": interface.bInterfaceClass, "subclass": interface.bInterfaceSubClass,
                        "protocol": interface.bInterfaceProtocol,
                        "extra_hex": bytes(interface.extra_descriptors).hex(),
                        "endpoints": [{"address": ep.bEndpointAddress, "attributes": ep.bmAttributes,
                                       "max_packet_size": ep.wMaxPacketSize, "interval": ep.bInterval} for ep in interface],
                        "hid_report_descriptor_hex": None}
                size = hid_report_length(bytes(interface.extra_descriptors))
                # Reading inactive alternate/config interfaces is intentionally skipped.
                if read_hid and size and interface.bAlternateSetting == 0 and config.bConfigurationValue == active_config:
                    try:
                        item["hid_report_descriptor_hex"] = bytes(device.ctrl_transfer(
                            0x81, 0x06, 0x2200, interface.bInterfaceNumber, size, timeout=1000)).hex()
                    except Exception as exc:
                        item["hid_error"] = str(exc)
                interfaces.append(item)
            result["configurations"].append({"value": config.bConfigurationValue,
                                              "attributes": config.bmAttributes, "interfaces": interfaces})
    except Exception as exc:
        result["errors"].append(f"configurations: {exc}")
    finally:
        usb.util.dispose_resources(device)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vid", type=lambda x: int(x, 16), required=True)
    parser.add_argument("--pid", type=lambda x: int(x, 16), required=True)
    parser.add_argument("--hid", action="store_true", help="read standard HID report descriptors if accessible")
    parser.add_argument("--output", type=Path, default=Path("research/usb-descriptors.json"))
    args = parser.parse_args()
    if not 0 <= args.vid <= 65535 or not 0 <= args.pid <= 65535:
        parser.error("VID/PID must be 16-bit hexadecimal")
    try:
        import usb.core
        devices = list(usb.core.find(find_all=True, idVendor=args.vid, idProduct=args.pid))
        result = {"schema": "adapt.usb.descriptors.v1", "captured_at_utc": datetime.now(timezone.utc).isoformat(),
                  "devices": [inspect(device, args.hid) for device in devices],
                  "limitations": ["Standard descriptor reads only; existing Windows driver may deny access.",
                                  "No set_configuration, interface claim or kernel driver detach."]}
    except Exception as exc:
        parser.exit(2, f"Descriptor backend unavailable: {exc}. Use inventory.ps1; do not replace headset drivers.\n")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Recorded {len(result['devices'])} selected devices to {args.output}")


if __name__ == "__main__":
    main()
