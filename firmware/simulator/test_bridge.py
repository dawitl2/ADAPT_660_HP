"""Real loopback TCP, independent wire client and bridge process cleanup."""
import asyncio
import binascii
import importlib.util
import json
import struct
import sys
import tempfile
from pathlib import Path

bridge_file = Path(__file__).with_name("bridge.py")
spec = importlib.util.spec_from_file_location("bridge", bridge_file)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)

for bad in ({"id": True, "debug": "short"}, {"id": 1, "debug": "short\nallow-boot on"},
            {"id": 1, "debug": "allow-boot on"}, {"id": 1, "debug": "quit"},
            {"id": 1, "frame_hex": "ff" * 141}, {"id": 1, "frame_hex": "+a"},
            {"id": 1, "debug": "short", "extra": 0}):
    try:
        bridge.command(bad)
        raise AssertionError(bad)
    except ValueError:
        pass


async def receive(reader):
    line = await asyncio.wait_for(reader.readline(), 8)
    assert line, "unexpected EOF"
    return json.loads(line)


async def until_complete(reader, rid):
    outputs = []
    while True:
        message = await receive(reader)
        if message.get("kind") == "complete":
            assert message["request_id"] == rid
            return outputs
        outputs.append(message)


async def main():
    with tempfile.TemporaryDirectory() as directory:
        token_file = Path(directory) / "token.txt"
        process = await asyncio.create_subprocess_exec(sys.executable, str(bridge_file),
            "--simulator", str(Path(sys.argv[1]).resolve()), "--port", "0", "--token-file", str(token_file),
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        writer = None
        try:
            ready = await receive(process.stdout)
            assert ready["host"] == "127.0.0.1" and not ready["physical_backend"]
            token = token_file.read_text().strip()
            assert token not in json.dumps(ready)
            port = ready["port"]
            r, w = await asyncio.open_connection("127.0.0.1", port)
            await bridge.send(w, {"auth": "wrong"})
            assert (await receive(r))["message"] == "authentication failed"
            w.close(); await w.wait_closed()

            async def connect():
                r, w = await asyncio.open_connection("127.0.0.1", port)
                await bridge.send(w, {"auth": token})
                assert (await receive(r))["kind"] == "ready"
                initial = await until_complete(r, 0)
                assert any(e.get("type") == "GET_DEVICE_STATE" for e in initial)
                return r, w

            reader, writer = await connect()
            r2, w2 = await asyncio.open_connection("127.0.0.1", port)
            await bridge.send(w2, {"auth": token})
            assert (await receive(r2))["message"] == "simulator already has a client"
            w2.close(); await w2.wait_closed()
            for rid, debug in [(1, "short"), (2, "battery 42"), (3, "anc 3"), (4, "bt connect"), (5, "map 1 23"), (6, "short")]:
                await bridge.send(writer, {"id": rid, "debug": debug})
                events = await until_complete(reader, rid)
                wires = [e["wire_hex"] for e in events if "wire_hex" in e]
                assert len(wires) == len(set(wires)), "duplicate mock events"
                if rid in (1, 6):
                    assert [e["action"] for e in events if e.get("type") == "ACTION_EVENT"] == [1 if rid == 1 else 23]
                if rid == 4:
                    state = next(bytes.fromhex(e["payload_hex"]) for e in events if e.get("type") == "GET_DEVICE_STATE")
                    assert state[:4] == bytes([42, 1, 3, 2]), state
            payload = b"independent-ping\0\xff"
            wire = struct.pack("<2sBBBBHH", b"AC", 0, 1, 11, 0, 0x9966, len(payload)) + payload
            wire += struct.pack("<H", binascii.crc_hqx(wire, 0xffff))
            await bridge.send(writer, {"id": 7, "frame_hex": wire.hex()})
            reply = next(e for e in await until_complete(reader, 7) if e.get("type") == "PONG")
            assert reply["sequence"] == 0x9966 and bytes.fromhex(reply["payload_hex"]) == payload
            await bridge.send(writer, {"id": 8, "frame_hex": "00"})
            assert any(e.get("error") == 1 for e in await until_complete(reader, 8))
            await bridge.send(writer, {"id": 9, "debug": "allow-boot on"})
            assert (await receive(reader))["kind"] == "error"
            writer.close(); await writer.wait_closed()
            await asyncio.sleep(0.05)
            reader, writer = await connect()
            writer.write(b"x" * 2049 + b"\n"); await writer.drain()
            assert not await asyncio.wait_for(reader.readline(), 8)
            writer.close(); await writer.wait_closed()
            reader, writer = await connect()
            await bridge.send(writer, {"shutdown": True})
            assert (await receive(reader))["kind"] == "stopping"
            assert await asyncio.wait_for(process.wait(), 8) == 0
            errors = (await process.stderr.read()).decode()
            assert not errors, errors
        finally:
            if writer:
                writer.close()
            if process.returncode is None:
                process.terminate()
                await process.wait()
    print("TCP bridge auth, state/actions, CRC interoperability, bad packets, reconnect, limits and shutdown passed")


asyncio.run(main())
