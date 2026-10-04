package dev.adapt.control
import dev.adapt.control.core.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class AcpTest {
    @Test fun crcReference() { assertEquals(0x29b1,Acp.crc("123456789".toByteArray())) }
    @Test fun roundTripAndCorruption() {
        val f=Frame(11,0,65535,byteArrayOf(0,42,-1)); val bytes=Acp.encode(f)
        assertArrayEquals(f.payload,Acp.decode(bytes).payload)
        bytes[10]=1
        assertThrows(IllegalArgumentException::class.java) { Acp.decode(bytes) }
    }
    @Test fun truncationAndOversize() {
        val b=Acp.encode(Frame(1,0,1,byteArrayOf(0,1)))
        for(i in b.indices) assertThrows(IllegalArgumentException::class.java) { Acp.decode(b.copyOf(i)) }
        assertThrows(IllegalArgumentException::class.java) { Acp.decode(ByteArray(141)) }
    }
    @Test fun recoveryCannotRouteToApplication() {
        val p=ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN).put(4).putShort(1).putLong(10).array()
        assertThrows(IllegalArgumentException::class.java) { Acp.button(Frame(4,2,0,p)) }
        p[0]=2
        assertEquals(2,Acp.button(Frame(4,2,0,p)).gesture)
    }
    @Test fun flagsAndLengths() {
        assertThrows(IllegalArgumentException::class.java) { Acp.encode(Frame(4,0,0,ByteArray(11))) }
        assertThrows(IllegalArgumentException::class.java) { Acp.encode(Frame(3,1,0,ByteArray(25))) }
    }
}
