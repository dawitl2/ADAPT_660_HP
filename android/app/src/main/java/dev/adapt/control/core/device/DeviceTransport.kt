package dev.adapt.control.core.device

import dev.adapt.control.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DeviceState(val connected: Boolean = false, val battery: Int? = null,
    val anc: Int = 0, val firmware: String = "Unknown", val transport: String = "Disconnected", val error: String? = null,
    val radioLink: Int = 0)
interface DeviceTransport {
    val state: StateFlow<DeviceState>
    val frames: SharedFlow<Frame>
    suspend fun connect()
    suspend fun send(frame: Frame)
    fun disconnect()
}

/** Authenticated Phase 2 loopback bridge. Never connect this development transport to a LAN host. */
class SimulatorTransport(private val token: () -> String, private val scope: CoroutineScope,
    private val host: String = "127.0.0.1") : DeviceTransport {
    private val _state = MutableStateFlow(DeviceState())
    override val state = _state.asStateFlow()
    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity=64)
    override val frames = _frames.asSharedFlow()
    private var socket: Socket? = null
    private var readerJob: Job? = null
    private val writes = Mutex()
    private var id = 0
    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        require(host == "127.0.0.1" || host == "10.0.2.2")
        require(token().matches(Regex("[0-9a-f]{64}"))) { "Enter the simulator pairing token in Settings" }
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host,6600),5000)
            s.soTimeout = 15000
            socket = s
            write(JSONObject().put("auth",token()))
            val input = BufferedInputStream(s.getInputStream())
            // Bound every envelope; BufferedReader.readLine would permit an unbounded peer allocation.
            fun line(): String {
                val bytes = ArrayList<Byte>()
                while(true) {
                    val b = input.read()
                    require(b >= 0) { "Simulator disconnected" }
                    if(b == 10) return bytes.toByteArray().toString(Charsets.UTF_8)
                    require(bytes.size < 8192) { "Oversized simulator envelope" }
                    bytes.add(b.toByte())
                }
            }
            require(JSONObject(line()).optString("kind") == "ready") { "Simulator authentication failed" }
            s.soTimeout = 0
            _state.value = DeviceState(connected=true, transport="Firmware simulator")
            readerJob = scope.launch(Dispatchers.IO) {
                try {
                    while(isActive) {
                        val json = JSONObject(line())
                        when(json.optString("kind")) {
                            "firmware" -> {
                                val f = Acp.decode(Acp.unhex(json.getString("wire_hex")))
                                if(f.type == 3 && f.flags == 1) {
                                    val p = f.payload
                                    _state.value = _state.value.copy(battery=(p[0].toInt() and 255).takeIf { it <= 100 },
                                        anc=p[2].toInt(), radioLink=p[3].toInt(), firmware=p.copyOfRange(8,24).toString(Charsets.US_ASCII).trimEnd('\u0000'))
                                }
                                _frames.emit(f)
                            }
                            "error" -> _state.value = _state.value.copy(error="Simulator rejected command")
                        }
                    }
                } catch(e: Exception) {
                    if(isActive) _state.value = DeviceState(error="Simulator connection ended")
                } finally { s.close() }
            }
        } catch(e: Exception) { s.close(); socket=null; _state.value=DeviceState(error=e.message); throw e }
    }
    private suspend fun write(json: JSONObject) = writes.withLock {
        withContext(Dispatchers.IO) {
            val s = socket ?: error("Connect the simulator first")
            s.getOutputStream().write((json.toString()+"\n").toByteArray())
            s.getOutputStream().flush()
        }
    }
    override suspend fun send(frame: Frame) = write(JSONObject().put("id",++id).put("frame_hex",Acp.hex(Acp.encode(frame))))
    suspend fun gesture(gesture: Int) { require(gesture in 1..4); write(JSONObject().put("id",++id).put("debug",listOf("short","double","long","verylong")[gesture-1])) }
    suspend fun debug(command: String) {
        require(command in listOf("bt disconnect","bt connect","battery 42","anc 1","anc 2","anc 3"))
        write(JSONObject().put("id",++id).put("debug",command))
    }
    override fun disconnect() { readerJob?.cancel(); readerJob=null; socket?.close(); socket=null; _state.value=DeviceState() }
}

/** Hardware facts remain UNKNOWN; activating this adapter requires a verified encrypted GATT contract. */
class PendingBleTransport : DeviceTransport {
    override val state = MutableStateFlow(DeviceState(error="Custom firmware GATT is not verified on stock hardware")).asStateFlow()
    override val frames = MutableSharedFlow<Frame>().asSharedFlow()
    override suspend fun connect(): Unit = error("Physical custom firmware is not available")
    override suspend fun send(frame: Frame): Unit = error("Physical custom firmware is not available")
    override fun disconnect() = Unit
}
