"""Create synthetic non-vendor firmware analysis fixture in ignored research/."""
import io
import json
import zipfile
from pathlib import Path

output = Path("research/synthetic-package.zip")
output.parent.mkdir(parents=True, exist_ok=True)
nested = io.BytesIO()
with zipfile.ZipFile(nested, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("image.bin", b"SYNTHETIC ONLY: Cortex-M clue is test data\x00" + bytes(range(256)) * 4)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("manifest.json", json.dumps({"version": "synthetic-0.1", "vendor_firmware": False}))
    archive.writestr("nested.zip", nested.getvalue())
print(output)
