# ADAPT Control PC companion

Python 3.12+, Windows; direct HTTPS commands with no database or remote shell.

```powershell
python -m venv .venv
.venv/Scripts/python -m pip install -r pc/requirements.txt
.venv/Scripts/python pc/companion.py --host YOUR_PC_LAN_IP --app 'C:/Path/To/AllowedEditor.exe'
.venv/Scripts/python -m unittest discover -s pc -v
```

Default is loopback. Use your private LAN address for phone access; allow Python on
private networks only if Windows prompts. Never forward port 6601 to the internet.
Enter HTTPS origin, printed certificate SHA-256 fingerprint and eight-digit code
in Android Settings → PC Companion. Verify the fingerprint directly on your PC.

Pairing expires in five minutes, permits five failures and is single-use. Successful
pairing rotates the previous phone token. Restart to reopen the window. `--revoke`
clears the PC token; Forget PC removes only the phone copy.

DPAPI protects key/token hash in `%LOCALAPPDATA%/ADAPTControl/pc`; retain private user
ACLs. The TLS key briefly uses a private temporary subdirectory during SSL loading,
then plaintext is removed. Android pins the certificate and protects the token with
Keystore. Renewed/expired certificates require new fingerprint verification/pairing.

Allowlist: play/pause, volume up/down, mute, lock workstation, open the fixed local
`.exe` from `--app`. No shell, arguments, arbitrary paths or uploaded executables.
Bodies are bounded; actions serialized, rate-limited and deduplicated by UUID during
the process lifetime. Tests mock desktop effects and do not lock your workstation.

Android BOTH / Focus starts study then requests the PC app. Partial failures are
reported; this is not an atomic distributed transaction.

Wire: HTTPS POST `/pair`, JSON `{ "code": "8_DIGITS" }` → generated bearer token.
POST `/action`: bearer authentication, fresh UUID `id`, allowlisted `action`.
The production companion never prints tokens.
