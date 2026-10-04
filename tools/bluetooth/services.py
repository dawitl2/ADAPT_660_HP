"""Enumerate BLE service metadata for an explicitly selected existing device.

Temporarily connects; no pairing request, characteristic/descriptor value reads,
subscriptions, application writes or firmware operations. OS cache may be stale.
"""
import argparse
import asyncio
import json
from datetime import datetime, timezone
from pathlib import Path


def describe(services):
    result = []
    for service in services:
        if len(result) >= 256:
            raise ValueError("service count exceeds inspection limit")
        chars = []
        for char in service.characteristics:
            if len(chars) >= 256 or len(char.descriptors) > 64:
                raise ValueError("attribute count exceeds inspection limit")
            chars.append({"uuid": char.uuid, "handle": char.handle,
                          "properties": list(char.properties),
                          "descriptors": [{"uuid": d.uuid, "handle": d.handle} for d in char.descriptors]})
        result.append({"uuid": service.uuid, "handle": service.handle, "characteristics": chars})
    return result


async def inspect(address):
    from bleak import BleakClient, BleakScanner
    device = await BleakScanner.find_device_by_address(address, timeout=8)
    if device is None:
        raise RuntimeError("selected device was not advertising during the bounded scan")
    async with asyncio.timeout(20):
        async with BleakClient(device, pair=False, timeout=12) as client:
            return {"name": device.name, "services": describe(client.services)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", required=True, help="observed Bluetooth address/OS device UUID")
    parser.add_argument("--output", type=Path, default=Path("research/ble-services.json"))
    args = parser.parse_args()
    report = {"schema": "adapt.ble.services.v1", "captured_at_utc": datetime.now(timezone.utc).isoformat(),
              "services": [], "status": "UNKNOWN", "errors": [],
              "limitations": ["Metadata discovery only; temporary normal BLE connection.",
                              "No application writes, value reads, subscriptions or pairing request.",
                              "Discovery does not establish custom firmware or concurrent audio support."]}
    try:
        report.update(asyncio.run(inspect(args.address)))
        report["status"] = "CONFIRMED observed service metadata"
    except Exception as exc:
        report["errors"].append(f"{type(exc).__name__}: {exc}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"{report['status']}: {len(report['services'])} services; report {args.output}")


if __name__ == "__main__":
    main()
