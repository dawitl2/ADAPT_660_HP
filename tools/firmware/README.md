# Offline package analysis

Python standard library only. Input must be a legitimate package/cache provided by
EPOS Connect/support; the analyzer does not authenticate provenance. It does not
download, execute, flash, decrypt, patch signatures or communicate with hardware.

```powershell
python tools/firmware/analyze.py research/official.zip --output research/report.json --extract research/extracted-new
python tools/firmware/analyze.py research/image.bin --output research/image-report.json --binwalk
```

Outputs SHA-256, sizes, ASCII/UTF-16LE strings with offsets, entropy/windows,
container/certificate/signature magic candidates, ELF/PE machine fields, provisional
architecture hints and recursive ZIP contents/compression/CRC/encryption flags.
JSON metadata is parsed as data, never instructions. SHA-256 hashes every analyzed
file. ZIP CRC verification is container integrity, not signature authenticity.
High entropy alone cannot distinguish encryption from compression.

Limits: input 64 MiB, each expanded member 8 MiB, global expansion 32 MiB, global
member count 1000, recursion 3. Skipped/encrypted entries and limits are reported.
Extraction is opt-in, top-level ZIP only, to a NEW directory. It rejects traversal,
absolute/drive/ADS paths, symlinks, Windows reserved names and case-colliding entries.
Partial output may remain if a corrupt member fails CRC after preflight; never
execute extracted files. Nested ZIPs are analyzed in memory, not auto-extracted.

For deeper embedded non-ZIP analysis use the established open-source
[Binwalk](https://github.com/ReFirmLabs/binwalk), optionally installed in an isolated
WSL/container environment. `--binwalk` invokes an already installed tool without
extraction. Basic toolkit intentionally avoids recreating a disassembler or crypto
validator. Do not infer a headset SoC from a PC updater executable's architecture.
