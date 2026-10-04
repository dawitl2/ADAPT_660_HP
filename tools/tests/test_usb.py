import importlib.util
import unittest
from pathlib import Path


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parents[1] / "usb" / f"{name}.py")
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


class UsbTests(unittest.TestCase):
    def test_inventory_diff(self):
        compare = module("compare").compare
        a = {"devices": [{"device_id": "headset", "pid": "0010"}, {"device_id": "gone"}]}
        b = {"devices": [{"device_id": "headset", "pid": "0011"}, {"device_id": "boot"}]}
        result = compare(a, b)
        self.assertEqual(result["added"], [{"device_id": "boot"}])
        self.assertEqual(result["removed"], [{"device_id": "gone"}])
        self.assertEqual(result["changed"][0]["identity"], "headset")
        self.assertEqual(compare(a, a), {"added": [], "removed": [], "changed": []})

    def test_duplicate_identity(self):
        with self.assertRaises(ValueError):
            module("compare").compare({"devices": [{"device_id": "x"}] * 2}, {"devices": []})

    def test_hid_descriptor(self):
        length = module("descriptors").hid_report_length
        self.assertEqual(length(bytes.fromhex("092111010001223f00")), 63)
        for data in (b"", b"\x00\x21", b"\x09\x21", bytes.fromhex("092111010002223f00"), bytes.fromhex("09211101000122ffff")):
            self.assertIsNone(length(data))
