"""Extended host process scenarios; no physical headset IO."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

sim = sys.argv[1]


def run(commands, settings=None):
    args = [sim] + (["--settings", str(settings)] if settings else [])
    p = subprocess.run(args, input="\n".join(commands + ["quit", ""]), text=True,
                       capture_output=True, check=True, timeout=10)
    return [json.loads(line) for line in p.stdout.splitlines()]


events = run(["metadata", "peers 2", "active 1", "call on", "call off", "audio on",
              "audio off", "usb-audio on", "usb-audio off", "jack in", "jack out",
              "peers 1", "sleep", "wake", "recover fail", "radio stalled", "tick 2000",
              "tick 500", "tick 1000", "lifecycle", "recover ok", "verylong", "lifecycle",
              "diagnostic 0", "short", "double", "long", "transport both off", "short",
              "transport both on", "diagnostic 0", "barrier 77"])
life = [bytes.fromhex(e["payload_hex"])[0] for e in events if e.get("type") == "LIFECYCLE_STATE"]
assert {0, 2, 3, 4, 5, 6, 7, 8, 10} <= set(life), life
assert any(e.get("feedback") == 13 and e["tone_ms"] == 360 for e in events)
assert any(e.get("feedback") == 10 and e["tone_ms"] == 80 for e in events)
assert any(e.get("feedback") == 11 and e["tone_ms"] == 170 for e in events)
assert any(e.get("feedback") == 12 and e["tone_ms"] == 210 for e in events)
assert any(e.get("feedback") == 4 for e in events)
assert events[-1] == {"barrier": 77}
metadata = bytes.fromhex(next(e["payload_hex"] for e in events if e.get("type") == "FIRMWARE_METADATA"))
assert metadata[:32].rstrip(b"\0") == b"ADAPT 660 HP" and metadata[-1] == 0

with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / "settings.acfg"
    run(["map 1 23", "set 7 0", "set 8 3"], path)
    events = run(["short", "get 7", "get 8"], path)
    assert any(e.get("action") == 23 for e in events)
    assert not any(e.get("feedback") == 10 for e in events)
    assert [int.from_bytes(bytes.fromhex(e["payload_hex"])[1:], "little") for e in events
            if e.get("type") == "GET_SETTING"] == [0, 3]
    path.write_bytes(b"corrupt")
    events = run(["short"], path)
    assert any(e.get("configuration_fallback") for e in events)
    assert any(e.get("action") == 1 for e in events)
    assert path.read_bytes() == b"corrupt" # startup never overwrites evidence
print("extended simulator lifecycle, watchdog, tones, transport faults and durable process reload passed")
