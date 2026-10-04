"""Authenticated loopback-only TCP bridge to the host simulator; stdlib only."""
import argparse
import asyncio
import json
import re
import secrets
from pathlib import Path

MAX_LINE = 2048
SIMPLE = {"short", "double", "long", "verylong", "down", "up", "hello", "capabilities",
          "state", "lifecycle", "metadata", "sleep", "wake", "pairing"}
DEBUG = re.compile(r"(?:tick|press|battery|anc|diagnostic|peers|active|get) [0-9]{1,5}|"
                   r"(?:map|set) [0-9]{1,3} [0-9]{1,5}|jack (?:in|out)|bt (?:connect|disconnect)|"
                   r"(?:power|audio|call|connecting|usb-audio|charging) (?:on|off)|"
                   r"radio (?:healthy|stalled)|recover (?:ok|fail)|transport (?:bt|usb|both) (?:on|off)")


def command(message):
    if not isinstance(message, dict) or type(message.get("id")) is not int or not 1 <= message["id"] <= 0xffffffff:
        raise ValueError("id must be a positive u32")
    if set(message) == {"id", "frame_hex"}:
        frame = message["frame_hex"]
        if not isinstance(frame, str) or not 0 < len(frame) <= 280 or len(frame) % 2 or not re.fullmatch("[0-9a-fA-F]+", frame):
            raise ValueError("frame_hex must contain one bounded ACP frame")
        return "rx " + frame
    if set(message) == {"id", "debug"}:
        debug = message["debug"]
        if isinstance(debug, str) and (debug in SIMPLE or DEBUG.fullmatch(debug)):
            return debug
    raise ValueError("expected frame_hex or an allowed simulator debug command")


async def send(writer, message):
    writer.write((json.dumps(message, separators=(",", ":")) + "\n").encode())
    await asyncio.wait_for(writer.drain(), 3)


class Bridge:
    def __init__(self, process, token):
        self.process, self.token = process, token
        self.owner = None
        self.clients = 0
        self.lock = asyncio.Lock()
        self.serial = 0
        self.stopped = asyncio.Event()

    async def transaction(self, writer, rid, commands):
        async with self.lock:
            self.serial = (self.serial + 1) & 0xffffffff
            marker = self.serial
            self.process.stdin.write(("\n".join(commands + [f"barrier {marker}"]) + "\n").encode())
            await self.process.stdin.drain()
            seen = set()
            while True:
                line = await asyncio.wait_for(self.process.stdout.readline(), 5)
                if not line:
                    raise RuntimeError("simulator exited")
                output = json.loads(line)
                if output.get("barrier") == marker:
                    break
                wire = output.get("wire_hex")
                if wire and wire in seen:
                    continue # same event sent through both mock transports
                if wire:
                    seen.add(wire)
                await send(writer, {"kind": "firmware", "request_id": rid, **output})
            await send(writer, {"kind": "complete", "request_id": rid})

    async def client(self, reader, writer):
        self.clients += 1
        try:
            if self.clients > 4:
                await send(writer, {"kind": "error", "message": "connection limit"})
                return
            line = await asyncio.wait_for(reader.readline(), 5)
            auth = json.loads(line)
            supplied = auth.get("auth") if isinstance(auth, dict) else None
            if not isinstance(supplied, str) or not supplied.isascii() or len(supplied) > 128 or not secrets.compare_digest(supplied, self.token):
                await send(writer, {"kind": "error", "message": "authentication failed"})
                return
            if self.owner is not None:
                await send(writer, {"kind": "error", "message": "simulator already has a client"})
                return
            self.owner = writer
            await send(writer, {"kind": "ready", "protocol": "0.1", "physical_backend": False})
            await self.transaction(writer, 0, ["hello", "capabilities", "state", "lifecycle", "metadata"])
            while line := await reader.readline():
                try:
                    message = json.loads(line)
                    if message == {"shutdown": True}:
                        await send(writer, {"kind": "stopping"})
                        self.stopped.set()
                        return
                    cmd = command(message)
                except (ValueError, TypeError):
                    await send(writer, {"kind": "error", "message": "invalid request"})
                    continue
                await self.transaction(writer, message["id"], [cmd, "state", "lifecycle"])
        except (ValueError, TimeoutError, ConnectionError, RuntimeError, asyncio.IncompleteReadError):
            # A partially consumed process transaction cannot be safely replayed.
            # Drain to a fresh barrier before the next authenticated session.
            if self.owner is writer and self.process.returncode is None:
                try:
                    async with self.lock:
                        self.serial = (self.serial + 1) & 0xffffffff
                        self.process.stdin.write(f"barrier {self.serial}\n".encode())
                        await self.process.stdin.drain()
                        while True:
                            output = json.loads(await asyncio.wait_for(self.process.stdout.readline(), 5))
                            if output.get("barrier") == self.serial:
                                break
                except (ValueError, TimeoutError, ConnectionError):
                    self.process.terminate()
        finally:
            if self.owner is writer:
                self.owner = None
            self.clients -= 1
            writer.close()
            try:
                await asyncio.wait_for(writer.wait_closed(), 3)
            except (ConnectionError, TimeoutError):
                pass


async def main(args):
    args.token_file.parent.mkdir(parents=True, exist_ok=True)
    try:
        with args.token_file.open("x", encoding="ascii") as file:
            file.write(secrets.token_hex(32) + "\n")
        args.token_file.chmod(0o600)
    except FileExistsError:
        pass
    token = args.token_file.read_text(encoding="ascii").strip()
    if not re.fullmatch("[0-9a-f]{64}", token):
        raise ValueError("token file must contain 64 lowercase hex characters")
    invocation = [str(args.simulator.resolve())]
    if args.settings:
        invocation += ["--settings", str(args.settings.resolve())]
    process = await asyncio.create_subprocess_exec(*invocation, stdin=asyncio.subprocess.PIPE,
                                                 stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
                                                 limit=16384)
    bridge = Bridge(process, token)
    try:
        server = await asyncio.start_server(bridge.client, "127.0.0.1", args.port, limit=MAX_LINE)
        print(json.dumps({"kind": "listening", "host": "127.0.0.1", "port": server.sockets[0].getsockname()[1],
                          "token_file": str(args.token_file.resolve()), "physical_backend": False}), flush=True)
        async with server:
            await bridge.stopped.wait()
    finally:
        if bridge.owner:
            bridge.owner.close()
        if process.returncode is None:
            try:
                process.stdin.write(b"quit\n")
                await process.stdin.drain()
                await asyncio.wait_for(process.wait(), 2)
            except (TimeoutError, ConnectionError):
                process.terminate()
                await process.wait()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", type=Path, required=True)
    parser.add_argument("--port", type=int, default=6600)
    parser.add_argument("--token-file", type=Path, default=Path("research/bridge-token.txt"))
    parser.add_argument("--settings", type=Path)
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error("port must be 0..65535")
    try:
        asyncio.run(main(args))
    except KeyboardInterrupt:
        pass
