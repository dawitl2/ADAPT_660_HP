# Protocol documentation

Canonical wire specification: [v0.1](../../docs/protocol.md).
Codec is allocation-free, endian explicit, and rejects unknown types, malformed
lengths/flags, incompatible versions and checksum errors without partial output.
Semantic value validation is firmware core responsibility.
