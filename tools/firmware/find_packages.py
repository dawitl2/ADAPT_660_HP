"""Locate vendor-named packages/cache/logs without reading or modifying contents."""
import argparse
import json
import os
import re
from pathlib import Path

SKIP = {".git", ".codex", "node_modules", ".venv", ".gradle", "__pycache__", "build", "target"}
VENDOR = re.compile(r"(?:^|[\\/_. -])(?:epos(?:connect)?|sennheiser)(?:$|[\\/_. -])", re.I)
HEADSET = re.compile(r"adapt[_. -]?660", re.I)
EXTENSIONS = {".zip", ".bin", ".dfu", ".hex", ".dat", ".fw", ".upd", ".xml", ".json", ".log", ".exe", ".msi"}


def scan(roots):
    report = {"schema": "adapt.firmware.search.v1", "roots": [], "candidates": [], "errors": [],
              "note": "Names are candidates only; legitimate provenance must be verified. No file content was read."}
    seen = set()
    for root in roots:
        root = Path(root).absolute()
        report["roots"].append({"path": str(root), "exists": root.is_dir()})
        if not root.is_dir():
            continue
        def onerror(error):
            if len(report["errors"]) < 100:
                report["errors"].append(str(error))
        for directory, children, filenames in os.walk(root, followlinks=False, onerror=onerror):
            children[:] = [name for name in children if name.lower() not in SKIP and not Path(directory, name).is_symlink()
                           and not getattr(Path(directory, name), "is_junction", lambda: False)()]
            for name in filenames:
                path = Path(directory, name)
                key = str(path).casefold()
                if path.suffix.lower() not in EXTENSIONS or key in seen:
                    continue
                if not (VENDOR.search(str(path)) or HEADSET.search(name)):
                    continue
                seen.add(key)
                try:
                    metadata = path.stat()
                    report["candidates"].append({"path": str(path), "size": metadata.st_size,
                        "mtime_unix": metadata.st_mtime,
                        "kind": "installer candidate" if path.suffix.lower() in {".exe", ".msi"} else "package/cache/log candidate",
                        "label": "UNKNOWN provenance"})
                except OSError as error:
                    onerror(error)
    report["candidates"].sort(key=lambda item: item["path"].casefold())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="*", type=Path)
    parser.add_argument("--output", type=Path, default=Path("research/package-search.json"))
    args = parser.parse_args()
    roots = args.roots or [Path.home() / "Downloads", Path.home() / "Desktop",
        *(Path(os.environ[name]) for name in ("ProgramData", "LOCALAPPDATA", "APPDATA", "ProgramFiles", "ProgramFiles(x86)") if name in os.environ)]
    report = scan(roots)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Recorded {len(report['candidates'])} candidates, {len(report['errors'])} access errors to {args.output}")


if __name__ == "__main__":
    main()
