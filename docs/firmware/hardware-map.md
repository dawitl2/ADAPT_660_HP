# Hardware map — 2026-10-04

| Item | Evidence | Status |
|---|---|---|
| Project headset | ADAPT 660, identified by user | CONFIRMED user identification |
| Teams/pairing button, touch pad, ANC and Bluetooth switches | Product overview, printed pages 4–6 | CONFIRMED documented controls |
| Audio/USB sockets, microphones, LEDs | Product overview, printed page 4 | CONFIRMED documented components |
| Actual USB VID/PID/interfaces/drivers | No ADAPT device found in present-only inventory | UNKNOWN |
| SoC/DSP/flash/bootloader, pin assignments and SDK | No teardown or legitimate image available | UNKNOWN |
| Wear sensor / electrical interface | Host supports unknown state; no evidence collected for exact variant | UNKNOWN |
| Host backend pin/register map | None; intentionally interfaces only | ASSUMED software model |

Source: [EPOS ADAPT 66X user guide A07, 11/24](https://shop.eposaudio.com/globalassets/__pim/products/adapt-600/adapt-660/3748334b-be64-4be8-b6d8-8dc815d4a328_51128_adapt66x_userguide_a07_1124_en_int_original.pdf).

Read-only Windows inspection captured 15 present devices/interfaces (USB roots,
Intel Bluetooth, ELAN fingerprint, internal audio/HID). No EPOS/Sennheiser/ADAPT
USB, audio or HID identity was present. Private raw evidence:
`research/usb-phase1.json` (ignored). These unrelated device IDs are not headset IDs.
Do not substitute a dongle VID/PID for the direct headset's identity.
