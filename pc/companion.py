"""ADAPT Control PC companion: pinned HTTPS, DPAPI secrets, fixed action allowlist."""
from __future__ import annotations
import argparse
import ctypes
from ctypes import wintypes
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import secrets
import ssl
import subprocess
import tempfile
import threading
import time
import uuid
from collections import OrderedDict
from http.server import HTTPServer, BaseHTTPRequestHandler

ACTIONS = frozenset({'play_pause', 'volume_up', 'volume_down', 'mute', 'open_app', 'lock'})


def protect(data: bytes, decrypt: bool = False) -> bytes:
    if os.name != 'nt':
        raise RuntimeError('Production secret storage requires Windows DPAPI')
    class Blob(ctypes.Structure):
        _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]
    buffer = ctypes.create_string_buffer(data)
    src = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    dst = Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    operation = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    operation.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                          ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    operation.restype = wintypes.BOOL
    if not operation(ctypes.byref(src), None, None, None, None, 1, ctypes.byref(dst)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(dst.data, dst.size)
    finally:
        kernel.LocalFree(dst.data)


class StateStore:
    def __init__(self, root: Path):
        self.root = root
        root.mkdir(parents=True, exist_ok=True)
        self.file = root / 'secrets.dpapi'
    def save(self, value):
        temp = self.root / 'secrets.pending'
        temp.write_bytes(protect(json.dumps(value).encode()))
        os.replace(temp, self.file)
    def load(self):
        if self.file.exists():
            return json.loads(protect(self.file.read_bytes(), decrypt=True))
        return None


def new_identity():
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(x509.oid.NameOID.COMMON_NAME, 'ADAPT Control PC')])
    now = dt.datetime.now(dt.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - dt.timedelta(minutes=5)).not_valid_after(now + dt.timedelta(days=365))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .sign(key, hashes.SHA256()))
    return {'cert': cert.public_bytes(serialization.Encoding.PEM).decode(),
            'key': key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                     serialization.NoEncryption()).decode(), 'token_hash': ''}


class Pairing:
    def __init__(self, state, persist, clock=time.monotonic):
        self.state, self.persist, self.clock = state, persist, clock
        self.code = f'{secrets.randbelow(100000000):08d}'
        self.expires = clock() + 300
        self.failures = 0
        self.used = False
        self.requests = OrderedDict()
        self.last_calls = []
    def pair(self, message):
        if (set(message) != {'code'} or not isinstance(message['code'], str)
                or self.clock() >= self.expires or self.used or self.failures >= 5):
            raise PermissionError('Pairing unavailable')
        if not secrets.compare_digest(message['code'], self.code):
            self.failures += 1
            raise PermissionError('Invalid pairing code')
        token = secrets.token_hex(32)
        updated = dict(self.state, token_hash=hashlib.sha256(token.encode()).hexdigest())
        self.persist(updated)
        self.state.update(updated)
        self.used = True
        return {'token': token}
    def authorize(self, header):
        expected = self.state.get('token_hash', '')
        if not expected or not isinstance(header, str) or not header.startswith('Bearer '):
            raise PermissionError('Unauthorized')
        supplied = header[7:]
        if len(supplied) != 64 or not secrets.compare_digest(hashlib.sha256(supplied.encode()).hexdigest(), expected):
            raise PermissionError('Unauthorized')
    def action(self, message, execute):
        if set(message) != {'id', 'action'} or message['action'] not in ACTIONS:
            raise ValueError('Unsupported action')
        request_id = str(uuid.UUID(message['id']))
        if request_id in self.requests:
            return {'ok': True, 'duplicate': True}
        now = self.clock()
        self.last_calls = [t for t in self.last_calls if now - t < 1]
        if len(self.last_calls) >= 10:
            raise PermissionError('Rate limit')
        self.last_calls.append(now)
        execute(message['action'])
        self.requests[request_id] = True
        if len(self.requests) > 1024:
            self.requests.popitem(last=False)
        return {'ok': True}


def windows_action(action: str, app: Path | None):
    if action not in ACTIONS:
        raise ValueError('Unsupported action')
    if os.name != 'nt':
        raise RuntimeError('Windows actions require Windows')
    user = ctypes.WinDLL('user32', use_last_error=True)
    if action == 'lock':
        if not user.LockWorkStation():
            raise ctypes.WinError(ctypes.get_last_error())
    elif action == 'open_app':
        if not app or not app.is_file() or app.suffix.lower() != '.exe':
            raise ValueError('Configure an existing .exe on the PC')
        subprocess.Popen([str(app.resolve())], shell=False)
    else:
        key = {'play_pause': 0xb3, 'volume_up': 0xaf, 'volume_down': 0xae, 'mute': 0xad}[action]
        user.keybd_event(key, 0, 0, 0)
        user.keybd_event(key, 0, 2, 0)


def handler(pairing: Pairing, execute):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'
        def setup(self):
            super().setup()
            self.connection.settimeout(5)
        def log_message(self, *args):
            pass  # Never log tokens, pairing codes, bodies or private network addresses.
        def do_POST(self):
            try:
                length = int(self.headers.get('Content-Length', '-1'))
                if not 0 < length <= 2048 or self.headers.get('Transfer-Encoding'):
                    raise ValueError('Bounded body required')
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise ValueError('Incomplete body')
                message = json.loads(raw)
                if not isinstance(message, dict):
                    raise ValueError('Object required')
                if self.path == '/pair':
                    result = pairing.pair(message)
                elif self.path == '/action':
                    pairing.authorize(self.headers.get('Authorization'))
                    result = pairing.action(message, execute)
                else:
                    raise ValueError('Unknown endpoint')
                self.respond(200, result)
            except PermissionError:
                self.respond(403, {'error': 'unauthorized or rate limited'})
            except (ValueError, TypeError, KeyError, OSError):
                self.respond(400, {'error': 'invalid request or action unavailable'})
        def respond(self, code, body):
            data = json.dumps(body).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(data)
            self.close_connection = True
    return Handler


class BoundedTlsServer(HTTPServer):
    # Single client processing limits concurrent actions; handshake/read timeouts bound stalled clients.
    def get_request(self):
        sock, address = self.socket.accept()
        sock.settimeout(5)
        try:
            return self.tls.wrap_socket(sock, server_side=True), address
        except Exception:
            sock.close()
            raise


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--host', default='127.0.0.1')
    p.add_argument('--port', type=int, default=6601)
    p.add_argument('--state', type=Path, default=Path(os.environ.get('LOCALAPPDATA', '.')) / 'ADAPTControl' / 'pc')
    p.add_argument('--app', type=Path, help='Fixed allowed executable, configured locally')
    p.add_argument('--revoke', action='store_true')
    args = p.parse_args()
    store = StateStore(args.state)
    state = store.load() or new_identity()
    if args.revoke:
        state['token_hash'] = ''
        store.save(state)
        print('Phone token revoked')
        return
    store.save(state)
    pairing = Pairing(state, store.save)
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes
    fingerprint = x509.load_pem_x509_certificate(state['cert'].encode()).fingerprint(hashes.SHA256()).hex()
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    # SSLContext needs file paths; use a private per-user temp folder and remove plaintext immediately.
    with tempfile.TemporaryDirectory(dir=store.root) as directory:
        cert, key = Path(directory) / 'cert.pem', Path(directory) / 'key.pem'
        cert.write_text(state['cert']); key.write_text(state['key'])
        tls.load_cert_chain(cert, key)
    server = BoundedTlsServer((args.host, args.port), handler(pairing, lambda action: windows_action(action, args.app)))
    server.tls = tls
    print(f'ADAPT Control HTTPS on {args.host}:{server.server_port}')
    print(f'Certificate SHA-256: {fingerprint}')
    print(f'Pairing code (5 minutes, single use): {pairing.code}')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
