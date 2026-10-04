"""Compare stable fields in two Windows/PyUSB snapshots, excluding timestamps."""
import argparse
import json
from pathlib import Path


def compare(before, after):
    def index(snapshot):
        devices = snapshot.get("devices", [])
        result = {}
        for item in devices:
            key = item.get("device_id") or item.get("identity")
            if not key or key in result:
                raise ValueError("missing or duplicate device identity")
            result[key] = item
        return result
    old, new = index(before), index(after)
    return {
        "added": [new[k] for k in sorted(new.keys() - old.keys())],
        "removed": [old[k] for k in sorted(old.keys() - new.keys())],
        "changed": [{"identity": k, "before": old[k], "after": new[k]}
                    for k in sorted(old.keys() & new.keys()) if old[k] != new[k]],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = compare(*(json.loads(p.read_text(encoding="utf-8-sig")) for p in (args.before, args.after)))
    content = json.dumps(result, indent=2, sort_keys=True)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content + "\n", encoding="utf-8")
    else:
        print(content)


if __name__ == "__main__":
    main()
