import json
import ssl
import threading
import unittest
import urllib.request
import uuid
from unittest.mock import Mock
from companion import Pairing, StateStore, protect, new_identity, handler, BoundedTlsServer
from pathlib import Path
import tempfile
import os

class PairingTests(unittest.TestCase):
    def setUp(self):
        self.clock = Mock(return_value=100)
        self.persist = Mock()
        self.p = Pairing({'token_hash': ''}, self.persist, self.clock)
    def test_expiry_and_attempt_limit(self):
        for _ in range(5):
            with self.assertRaises(PermissionError): self.p.pair({'code': 'invalid'})
        with self.assertRaises(PermissionError): self.p.pair({'code': self.p.code})
        self.p = Pairing({'token_hash': ''}, self.persist, self.clock)
        self.clock.return_value = 401
        with self.assertRaises(PermissionError): self.p.pair({'code': self.p.code})
    def test_pair_and_auth_and_single_use(self):
        token = self.p.pair({'code': self.p.code})['token']
        self.p.authorize('Bearer ' + token)
        self.assertNotIn(token, json.dumps(self.p.state))
        with self.assertRaises(PermissionError): self.p.authorize('Bearer wrong')
        with self.assertRaises(PermissionError): self.p.pair({'code': self.p.code})
    def test_failed_persistence_does_not_consume_code(self):
        self.persist.side_effect = OSError('disk')
        with self.assertRaises(OSError): self.p.pair({'code': self.p.code})
        self.assertFalse(self.p.used)
        self.assertEqual('', self.p.state['token_hash'])
    def test_allowlist_replay_and_rate_limit(self):
        run = Mock()
        message = {'id': str(uuid.uuid4()), 'action': 'play_pause'}
        self.p.action(message, run); self.p.action(message, run)
        run.assert_called_once_with('play_pause')
        with self.assertRaises(ValueError): self.p.action(dict(message, action='powershell'), run)
        with self.assertRaises(ValueError): self.p.action(dict(message, command='whoami'), run)
        for _ in range(9): self.p.action({'id':str(uuid.uuid4()), 'action':'mute'}, run)
        with self.assertRaises(PermissionError): self.p.action({'id':str(uuid.uuid4()), 'action':'mute'}, run)
    @unittest.skipUnless(os.name == 'nt', 'DPAPI Windows test')
    def test_dpapi_and_identity(self):
        with tempfile.TemporaryDirectory() as d:
            store = StateStore(Path(d)); state = new_identity(); store.save(state)
            self.assertEqual(state, store.load())
            self.assertNotIn(b'PRIVATE KEY', store.file.read_bytes())
            self.assertEqual(b'private',protect(protect(b'private'),True))
    def test_https_pairing_and_action(self):
        state = new_identity()
        with tempfile.TemporaryDirectory() as d:
            cert, key = Path(d)/'cert', Path(d)/'key'
            cert.write_text(state['cert']); key.write_text(state['key'])
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); tls.load_cert_chain(cert,key)
            run = Mock(); pairing = Pairing(state,Mock())
            server = BoundedTlsServer(('127.0.0.1',0),handler(pairing,run)); server.tls=tls
            thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
            client=ssl.create_default_context(cafile=str(cert)); client.check_hostname=False
            base=f'https://127.0.0.1:{server.server_port}'
            try:
                request=urllib.request.Request(base+'/pair',json.dumps({'code':pairing.code}).encode(),{'Content-Type':'application/json'})
                with urllib.request.urlopen(request,context=client,timeout=5) as reply: token=json.load(reply)['token']
                request=urllib.request.Request(base+'/action',json.dumps({'id':str(uuid.uuid4()),'action':'play_pause'}).encode(),{'Authorization':'Bearer '+token})
                with urllib.request.urlopen(request,context=client,timeout=5) as reply: self.assertTrue(json.load(reply)['ok'])
                run.assert_called_once_with('play_pause')
                with self.assertRaises(urllib.error.HTTPError): urllib.request.urlopen(urllib.request.Request(base+'/action',b'{}'),context=client,timeout=5)
            finally: server.shutdown(); server.server_close(); thread.join(5)

if __name__ == '__main__': unittest.main()
