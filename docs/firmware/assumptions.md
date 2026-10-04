# Evidence register

## CONFIRMED

- User identifies the project headset as EPOS/Sennheiser ADAPT 660 and phone as S20+.
- User reports that analog-cable use restored otherwise unusable Bluetooth. This
  confirms a reported observation, not a diagnosed cause or repeatable lab result.
- Host builds/tests use GCC 12.2 (MinGW, 32-bit), CMake 4.3.2 and Python 3.12.

## ASSUMED

- Firmware replacement will eventually be possible; no flashable target exists.
- Button timings: debounce 25 ms; short ≤650 ms; second down edge ≤400 ms after
  first release; long ≥1500 ms on release; very long ≥5000 ms while held.
- Holds in (650,1500) ms deliberately produce no gesture. Single short waits for
  double timeout; a failed second hold preserves the first short.
- Very-long pairing is reserved and cannot be remapped. No long action fires before it.
- Jack transitions/reset hypotheses, control packet format and all host HAL states
  are design choices, not reverse-engineered EPOS behavior.

## UNKNOWN

SoC, DSP, GPIOs, flash layout, boot ROM, bootloader, SDK, update signing/encryption,
control services/endpoints, physical button timing, USB update identity, rollback,
and a verified method for restoring stock firmware. No chipset address is invented.
