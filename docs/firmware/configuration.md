# Versioned configuration (ASSUMED storage design)

`config::Record` is endian-explicit, independent of C++ ABI: magic `ACFG`, schema
u16, payload length u16, generation u32, payload, CRC16/CCITT-FALSE u16 over all
preceding bytes. Schema 2 is 46 bytes. Payload offsets from the record start:
12–31 five timing u32s in Phase 1 order, 32–37 three action u16s, 38 ANC u8,
39 confirmation boolean, 40 preferred action u16, 42 multipoint boolean, 43
diagnostic level (0 off / 1 normal / 2 verbose). Only 0/1 boolean bytes accepted.
CRC is corruption detection, not authentication. Generation wraps modulo u32.
All gesture timing thresholds/windows are bounded to 60000 ms; an excessive
double window cannot leave a single press waiting for days.

Schema 1 is an explicit 41-byte legacy record with a 27-byte payload ending at
ANC; it migrates by supplying defaults for new fields. It is a designed legacy
format, not a record extracted from EPOS or serialized in Phase 1. Unknown future
schemas, invalid fields, wrong lengths and corrupt records fail without changing
the caller's output. The core starts with safe defaults on a failed load.

Target persistence must implement the HAL's atomic save contract with a vendor
NVM transaction or verified dual-bank journal. It must survive power loss without
damaging the previous valid record. Physical flash endurance/erase layout remain
UNKNOWN; no addresses or erase operations are supplied. A host file adapter is
development storage only, and cannot establish physical NVM durability.

Simulator `--settings PATH` uses the schema codec, an exclusive temporary file,
file flush, and same-directory atomic replacement (MoveFileEx with write-through
on Windows; rename after fsync on POSIX, directory fsync best effort). Single
writer only. A failed write before replacement retains the old record. Missing or
corrupt records load safe defaults; a newer schema is preserved and updates
return STORAGE until an explicit separate store is chosen. Do not infer hardware
power-loss guarantees from host-filesystem tests. Use ignored `research/` paths.

The multipoint value is a preference; unsupported hardware must report its limit
instead of implying a second peer exists. Preferred action is explicit stored
metadata for future configurable entry points; it does not override the three
purple-button mappings. Very-long pairing remains outside the mapping array.
