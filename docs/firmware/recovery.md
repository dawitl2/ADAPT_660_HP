# Recovery research — 2026-10-04

## CONFIRMED

- EPOS documents direct USB headset updates through EPOS Connect (user guide,
  printed page 27). The dongle has a separate update procedure.
- The guide documents approximately 4-second Teams/pairing holds to enter pairing
  (page 9), then another hold to clear pairing history (page 29). These operations
  are distinct from restoring firmware; neither was performed in this phase.
  [ADAPT 66X guide](https://shop.eposaudio.com/globalassets/__pim/products/adapt-600/adapt-660/3748334b-be64-4be8-b6d8-8dc815d4a328_51128_adapt66x_userguide_a07_1124_en_int_original.pdf).
- EPOS Connect has an Update Overview → Update from file flow for selecting a ZIP
  and installing selected updates. [Official manual-update article](https://www.eposaudio.com/en/im/support/knowledge-base/software/epos-connect/manual-firmware-update-from-file).
- EPOS Connect release history records a recovery-mode fix for **ADAPT 660 AMC**
  in version 7.6.0 (2023-06-01). This confirms a documented recovery feature for
  that variant, not a proven restoration procedure for this user's headset.
  [Official 8.5.0 release notes, printed page 10](https://www.eposaudio.com/globalassets/___image-library/_enterprise/files/english/epos-connect/epos-connect-8.5.0/release-notes_epos-connect_winmac_v8.5.0.pdf).

## ASSUMED

Simulator very-long hold at 5000 ms reserves pairing independent of app mappings.
Pairing does not erase stored peers/settings. Reboot and bootloader requests need
an authorized transport AND explicit HAL permission. Host defaults deny both;
`allow-boot on` enables synthetic requests only. A target HAL must schedule a
permitted boot after the reply is flushed, rather than reset inside its callback.

## UNKNOWN

| Question | Current evidence / next non-destructive step |
|---|---|
| Exact model's recovery/update entry path | Confirm article number and inspect official UI/support guidance when connected |
| Interrupted-update behavior | Reviewed ADAPT guide/manual-update article do not specify it; no interruption experiment |
| USB enumeration change | No update was run; compare before/during/after snapshots during a separately authorized normal update |
| Stock firmware rollback | No official package or rollback policy observed; ZIP-selection does not prove downgrade support |
| Bootloader interface/VID/PID | Not observed; capture enumeration during official update, never send speculative vendor commands |
| Signing/encryption and restoration | No legitimate firmware package found; analyze verified package before any physical custom flash |

A verified restoration gate must eventually include exact variant, known-good
official image/hash and provenance, supported entry/restore procedure, observed
enumeration and recovery behavior. Do not remove stock pairing/recovery or flash
a custom binary before that gate is satisfied. No restoration claim is made here.
