import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("finder", Path(__file__).parents[1] / "firmware/find_packages.py")
finder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(finder)


class SearchTests(unittest.TestCase):
    def test_vendor_boundaries_and_dedup(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in ("repositories.zip", "EPOS/cache/update.zip", "ADAPT_660_1.0.zip", "node_modules/EPOS/update.zip"):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"synthetic")
            report = finder.scan([root, root])
            self.assertEqual(len(report["candidates"]), 2)
            self.assertFalse(any("repositories" in x["path"] for x in report["candidates"]))
            self.assertFalse(any("node_modules" in x["path"] for x in report["candidates"]))

    def test_missing_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = finder.scan([Path(temporary) / "missing"])
            self.assertFalse(report["roots"][0]["exists"])
            self.assertEqual(report["candidates"], [])
