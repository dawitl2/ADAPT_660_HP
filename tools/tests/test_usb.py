import importlib.util
import unittest
from types import SimpleNamespace
from pathlib import Path


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parents[1] / "usb" / f"{name}.py")
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


class UsbTests(unittest.TestCase):
    def test_standard_interface_read_never_claims(self):
        calls = []
        class Backend:
            def open_device(self, device):
                calls.append("open")
                return "handle"
            def close_device(self, handle):
                calls.append("close")
            def ctrl_transfer(self, handle, kind, request, value, interface, buffer, timeout):
                calls.append((kind, request, value, interface, timeout))
                buffer[0] = 42
                return 1
        device = SimpleNamespace(_ctx=SimpleNamespace(backend=Backend(), dev="fake"))
        self.assertEqual(module("descriptors").interface_read_no_claim(device, 6, 0x2200, 3, 63), bytes([42]))
        self.assertEqual(calls, ["open", (0x81, 6, 0x2200, 3, 1000), "close"])

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
