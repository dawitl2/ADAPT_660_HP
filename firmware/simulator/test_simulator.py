"""End-to-end process/pipe validation; requires a built simulator path."""
import json
import subprocess
import sys

run = subprocess.run([sys.argv[1]], input="\n".join([
    "hello", "capabilities", "short", "double", "long", "verylong",
    "battery 42", "anc 3", "jack in", "jack out", "bt connect",
    "map 1 16", "short", "reboot", "allow-boot on", "reboot", "short",
    "state", "rx 00", "rx +a", "battery 101", "bootloader", "short", "power-on", "short", "quit", ""
]), text=True, capture_output=True, check=True, timeout=10)
events = [json.loads(line) for line in run.stdout.splitlines()]
actions = [e["action"] for e in events if e.get("type") == "ACTION_EVENT" and e["transport"] == "mock-usb"]
assert actions == [1, 2, 3, 16, 16, 16], actions
modes = [e["value"] for e in events if e.get("code") == 1 and e["transport"] == "mock-usb"]
assert modes == [2, 1, 3, 4, 0], modes
assert any(e.get("error") == 2 for e in events), events
assert any(e.get("simulated_reboot") for e in events)
assert any(e.get("simulated_bootloader") for e in events)
assert any(e.get("command_error") for e in events)
states = [bytes.fromhex(e["payload_hex"]) for e in events if e.get("type") == "GET_DEVICE_STATE"]
assert states[-1][0] == 42 and states[-1][2] == 3 and states[-1][24:] == bytes([0, 1])
print(f"simulator pipe flow passed ({len(events)} structured outputs)")
