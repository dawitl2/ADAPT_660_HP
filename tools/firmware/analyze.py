"""Bounded, offline firmware/ZIP inspection. Never execute or flash input files."""
import argparse
import hashlib
import io
import json
import math
import re
import shutil
import stat
import subprocess
import zipfile
from collections import Counter
from pathlib import Path, PurePosixPath

MAX_INPUT = 64 * 1024 * 1024
MAX_MEMBER = 8 * 1024 * 1024
MAX_EXPANDED = 32 * 1024 * 1024
MAX_MEMBERS = 1000
MAX_DEPTH = 3
SIGNATURES = {"ZIP": b"PK\x03\x04", "GZIP": b"\x1f\x8b\x08", "XZ": b"\xfd7zXZ\x00",
              "7ZIP": b"7z\xbc\xaf\x27\x1c", "ELF": b"\x7fELF", "PE candidate": b"MZ",
              "uImage": b"\x27\x05\x19\x56", "DfuSe": b"DfuSe",
              "PEM certificate": b"-----BEGIN CERTIFICATE-----",
              "PEM signature": b"-----BEGIN SIGNATURE-----"}
RESERVED = {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def entropy(data):
    if not data:
        return 0.0
    return round(-sum((count / len(data)) * math.log2(count / len(data)) for count in Counter(data).values()), 6)


def strings(data):
    result = []
    for encoding, pattern in (("ASCII", rb"[\x20-\x7e]{4,}"), ("UTF-16LE", rb"(?:[\x20-\x7e]\x00){4,}")):
        for match in re.finditer(pattern, data):
            result.append({"offset": match.start(), "encoding": encoding,
                           "text": match.group()[:512].decode("ascii" if encoding == "ASCII" else "utf-16le")})
            if len(result) >= 256:
                return result
    return result


def safe_member(info):
    name = info.filename
    path = PurePosixPath(name)
    if not name or "\\" in name or path.is_absolute() or "\x00" in name:
        return False
    # Prevent Windows drive/ADS, traversal, reserved devices and name aliases.
    parts = name.rstrip("/").split("/")
    if any(part in ("", ".", "..") or any(ord(c) < 32 for c in part)
           or any(c in part for c in ':<>"|?*') or part.endswith((".", " "))
           or part.split(".")[0].upper() in RESERVED for part in parts):
        return False
    mode = (info.external_attr >> 16) & 0xffff
    if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR)):
        return False
    return True


def architecture_clues(data, extracted_strings):
    clues = []
    if data.startswith(b"\x7fELF") and len(data) >= 20 and data[5] in (1, 2):
        machine = int.from_bytes(data[18:20], "little" if data[5] == 1 else "big")
        clues.append({"label": "CONFIRMED", "kind": "ELF e_machine field", "value": machine,
                      "meaning": {3: "x86", 40: "ARM", 62: "x86-64", 183: "AArch64", 243: "RISC-V"}.get(machine, "unmapped")})
    if data.startswith(b"MZ") and len(data) >= 64:
        offset = int.from_bytes(data[60:64], "little")
        if offset + 6 <= len(data) and data[offset:offset + 4] == b"PE\x00\x00":
            clues.append({"label": "CONFIRMED", "kind": "PE machine field", "value": int.from_bytes(data[offset + 4:offset + 6], "little")})
    if len(data) >= 8:
        sp, reset = int.from_bytes(data[:4], "little"), int.from_bytes(data[4:8], "little")
        if 0x20000000 <= sp < 0x40000000 and reset & 1 and reset not in (1, 0xffffffff):
            clues.append({"label": "ASSUMED", "kind": "possible Cortex-M vector table at offset zero",
                          "stack_value": hex(sp), "reset_value": hex(reset), "note": "heuristic, not chipset identification"})
    for item in extracted_strings:
        if re.search(r"\b(ARM|Thumb|Cortex|CSR|QCC\w*|Qualcomm|Kalimba|Xtensa|DSP|RISC-V)\b", item["text"], re.I):
            clues.append({"label": "ASSUMED", "kind": "architecture-related string", **item})
    return clues


def describe(data, name="input", depth=0, budget=None):
    if budget is None:
        budget = {"bytes": MAX_EXPANDED, "members": MAX_MEMBERS}
    extracted_strings = strings(data)
    result = {"name": name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
              "entropy_bits_per_byte": entropy(data),
              "entropy_windows": [{"offset": i, "value": entropy(data[i:i + 4096])}
                                  for i in range(0, min(len(data), 4 * 1024 * 1024), 4096)],
              "entropy_window_scope": "first 4 MiB, 4096-byte windows",
              "strings": extracted_strings, "string_limit": 256,
              "signatures": [], "architecture_clues": architecture_clues(data, extracted_strings),
              "members": [], "warnings": [],
              "evidence": {"CONFIRMED": "size/hash/entropy/byte offsets and successfully parsed container fields",
                           "ASSUMED": "magic matches, architecture string/vector hints, metadata candidate names",
                           "UNKNOWN": "physical SoC, authenticity, executable role, signature validity, firmware encryption"}}
    for kind, magic in SIGNATURES.items():
        start = 0
        for _ in range(64):
            offset = data.find(magic, start)
            if offset < 0:
                break
            result["signatures"].append({"kind": kind, "offset": offset, "label": "ASSUMED"})
            start = offset + len(magic)
    if zipfile.is_zipfile(io.BytesIO(data)):
        if depth >= MAX_DEPTH:
            result["warnings"].append("ZIP recursion depth limit reached")
            return result
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for info in archive.infolist():
                if budget["members"] <= 0:
                    result["warnings"].append("global member limit reached")
                    break
                budget["members"] -= 1
                item = {"name": info.filename, "declared_size": info.file_size,
                        "compressed_size": info.compress_size, "compression_method": info.compress_type,
                        "zip_crc32": f"{info.CRC:08x}", "zip_encrypted_flag": bool(info.flag_bits & 1),
                        "safe_path": safe_member(info)}
                if re.search(r"manifest|version|\.json$|\.xml$|\.sig$|\.cert$|\.crt$|\.pem$|checksum", info.filename, re.I):
                    item["metadata_candidate"] = "ASSUMED: name only; inspect content and provenance"
                result["members"].append(item)
                if info.is_dir():
                    continue
                if not item["safe_path"] or info.flag_bits & 1 or info.file_size > min(MAX_MEMBER, budget["bytes"]):
                    item["skipped"] = "unsafe path, encrypted member or expansion limit"
                    continue
                try:
                    with archive.open(info) as stream:
                        content = stream.read(min(MAX_MEMBER, budget["bytes"]) + 1)
                    if len(content) > min(MAX_MEMBER, budget["bytes"]):
                        item["skipped"] = "actual expansion limit exceeded"
                        continue
                    budget["bytes"] -= len(content)
                    item["analysis"] = describe(content, info.filename, depth + 1, budget)
                    item["zip_crc_verified"] = True
                    if info.filename.lower().endswith(".json") and len(content) <= 65536:
                        try:
                            item["parsed_json"] = json.loads(content)
                        except (ValueError, UnicodeError):
                            item["metadata_parse_error"] = "invalid JSON"
                except (zipfile.BadZipFile, RuntimeError, NotImplementedError, OSError) as exc:
                    item["read_error"] = str(exc)
    return result


def safe_extract(path, destination):
    """Extract only one ZIP level to a NEW directory after preflight validation."""
    destination = Path(destination).absolute()
    with zipfile.ZipFile(path) as archive:
        members = archive.infolist()
        total = 0
        names = set()
        if len(members) > MAX_MEMBERS:
            raise ValueError("member limit exceeded")
        for info in members:
            key = info.filename.rstrip("/").casefold()
            if not safe_member(info) or key in names or info.flag_bits & 1 or info.file_size > MAX_MEMBER:
                raise ValueError(f"unsafe/duplicate/encrypted/oversized member: {info.filename}")
            names.add(key)
            total += info.file_size
            if total > MAX_EXPANDED:
                raise ValueError("expansion limit exceeded")
        destination.mkdir(parents=True, exist_ok=False) # never overwrite user files
        for info in members:
            target = destination.joinpath(*PurePosixPath(info.filename).parts)
            if not target.resolve().is_relative_to(destination.resolve()):
                raise ValueError("path escapes extraction root")
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, target.open("xb") as output:
                    content = source.read(MAX_MEMBER + 1)
                    if len(content) > MAX_MEMBER:
                        raise ValueError("actual expansion limit exceeded")
                    output.write(content)
    return destination


def markdown(report):
    lines = ["# Offline firmware analysis", "", f"Filename: `{report['name']}`",
             f"Size: {report['size']} bytes", f"SHA-256: `{report['sha256']}`", "",
             "CONFIRMED: file measurements and successfully parsed fields only.",
             "ASSUMED: signature matches, vector/string clues and candidate metadata names.",
             "UNKNOWN: hardware target, authenticity, signing validity, firmware encryption.", "",
             f"Entropy: {report['entropy_bits_per_byte']} bits/byte. High entropy alone does not prove encryption.", "",
             "ZIP contents (full recursive detail and strings in companion JSON):", "",
             "| Member | Bytes | Method | Encrypted flag |", "|---|---:|---:|---|"]
    for item in report["members"]:
        name = item["name"].replace("|", "\\|").replace("\n", " ").replace("\r", " ")
        lines.append(f"| {name} | {item['declared_size']} | {item['compression_method']} | {item['zip_encrypted_flag']} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path, default=Path("research/firmware-report.json"))
    parser.add_argument("--extract", type=Path, help="new directory; top-level ZIP only")
    parser.add_argument("--binwalk", action="store_true", help="also run installed Binwalk without extraction")
    args = parser.parse_args()
    try:
        if args.input.stat().st_size > MAX_INPUT:
            parser.error("input exceeds 64 MiB analysis limit; hash separately or inspect externally")
        with args.input.open("rb") as stream:
            data = stream.read(MAX_INPUT + 1)
        if len(data) > MAX_INPUT:
            parser.error("input grew beyond analysis limit")
        report = describe(data, args.input.name)
        if args.extract:
            report["extraction"] = str(safe_extract(args.input, args.extract))
        if args.binwalk:
            executable = shutil.which("binwalk")
            if executable:
                run = subprocess.run([executable, str(args.input.resolve())], capture_output=True, text=True, timeout=30, check=False)
                report["binwalk"] = {"returncode": run.returncode, "stdout": run.stdout[:65536], "stderr": run.stderr[:4096]}
            else:
                report["warnings"].append("Binwalk not installed; basic bounded analysis still complete")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        args.output.with_suffix(".md").write_text(markdown(report), encoding="utf-8")
        print(f"{report['name']}: {report['size']} bytes SHA-256 {report['sha256']}")
        print(f"Reports: {args.output} and {args.output.with_suffix('.md')}")
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.TimeoutExpired) as exc:
        parser.exit(2, f"Analysis failed safely: {exc}\n")


if __name__ == "__main__":
    main()
