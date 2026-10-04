import importlib.util
import unittest
from pathlib import Path
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location("ble_services", Path(__file__).parents[1] / "bluetooth/services.py")
services = importlib.util.module_from_spec(spec)
spec.loader.exec_module(services)


class BluetoothMetadataTests(unittest.TestCase):
    def test_metadata_only_and_limits(self):
        desc = SimpleNamespace(uuid="descriptor", handle=3)
        char = SimpleNamespace(uuid="characteristic", handle=2, properties=["read"], descriptors=[desc])
        service = SimpleNamespace(uuid="service", handle=1, characteristics=[char])
        report = services.describe([service])
        self.assertEqual(report[0]["characteristics"][0]["descriptors"], [{"uuid": "descriptor", "handle": 3}])
        with self.assertRaises(ValueError):
            services.describe([service] * 257)
        char.descriptors *= 65
        with self.assertRaises(ValueError):
            services.describe([service])
