"""Streaming SHA-256 for files of any size; no content interpretation."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", type=Path, nargs="+")
    args = parser.parse_args()
    for path in args.files:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        print(json.dumps({"filename": path.name, "size": path.stat().st_size, "sha256": digest.hexdigest()}))


if __name__ == "__main__":
    main()
