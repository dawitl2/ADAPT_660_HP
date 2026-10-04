import hashlib
import importlib.util
import io
import json
import stat
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("analyze", Path(__file__).parents[1] / "firmware" / "analyze.py")
analyze = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analyze)


def package(files):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    return output.getvalue()


class FirmwareTests(unittest.TestCase):
    def test_measurements(self):
        self.assertEqual(analyze.entropy(b"\x00" * 4096), 0)
        self.assertEqual(analyze.entropy(bytes(range(256))), 8)
        report = analyze.describe(b"ARM Cortex-M4\x00" + "version 0.1".encode("utf-16le"))
        self.assertEqual(report["sha256"], hashlib.sha256(b"ARM Cortex-M4\x00" + "version 0.1".encode("utf-16le")).hexdigest())
        self.assertTrue(report["architecture_clues"])
        self.assertTrue(any(s["encoding"] == "UTF-16LE" for s in report["strings"]))

    def test_recursive_zip_and_repeatability(self):
        data = package({"manifest.json": json.dumps({"version": "synthetic-0.1"}),
                        "nested.zip": package({"image.bin": b"\x00" * 128})})
        report = analyze.describe(data)
        self.assertEqual(report, analyze.describe(data))
        manifest = report["members"][0]
        self.assertEqual(manifest["parsed_json"]["version"], "synthetic-0.1")
        self.assertTrue(manifest["zip_crc_verified"])
        self.assertEqual(report["members"][1]["analysis"]["members"][0]["name"], "image.bin")

    def test_unsafe_names(self):
        for name in ("../escape.bin", "/absolute.bin", "C:/file", "x\\..\\bad", "CON.txt", "a:stream", "a/./b", "a//b", "a. ", "a\x01b", "a/NUL"):
            self.assertFalse(analyze.safe_member(zipfile.ZipInfo(name)), name)
        self.assertTrue(analyze.safe_member(zipfile.ZipInfo("firmware/image.bin")))

    def test_symlink_rejected(self):
        info = zipfile.ZipInfo("link")
        info.create_system = 3
        info.external_attr = (stat.S_IFLNK | 0o777) << 16
        self.assertFalse(analyze.safe_member(info))

    def test_bounded_expansion_and_depth(self):
        data = package({"large.bin": b"x" * 1024})
        report = analyze.describe(data, budget={"bytes": 100, "members": 10})
        self.assertIn("skipped", report["members"][0])
        self.assertIn("depth limit", analyze.describe(data, depth=3)["warnings"][0])
        self.assertIn("member limit", analyze.describe(data, budget={"bytes": 1000, "members": 0})["warnings"][0])

    def test_safe_extraction_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "fixture.zip"
            source.write_bytes(package({"firmware/image.bin": b"synthetic"}))
            out = analyze.safe_extract(source, root / "extracted")
            self.assertEqual((out / "firmware/image.bin").read_bytes(), b"synthetic")
            self.assertEqual(analyze.sha256_file(source), hashlib.sha256(source.read_bytes()).hexdigest())
            with self.assertRaises(FileExistsError):
                analyze.safe_extract(source, out)

    def test_traversal_and_case_collision_preflight(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "fixture.zip"
            for files in ({"../bad": b"x"}, {"A.bin": b"a", "a.bin": b"b"}):
                source.write_bytes(package(files))
                with self.assertRaises(ValueError):
                    analyze.safe_extract(source, root / "new")
                self.assertFalse((root / "new").exists())

    def test_elf_field_not_hardware_claim(self):
        elf = bytearray(64); elf[:4] = b"\x7fELF"; elf[5] = 1; elf[18] = 40
        clue = analyze.describe(bytes(elf))["architecture_clues"][0]
        self.assertEqual(clue["meaning"], "ARM")
        self.assertIn("physical SoC", analyze.describe(bytes(elf))["evidence"]["UNKNOWN"])
