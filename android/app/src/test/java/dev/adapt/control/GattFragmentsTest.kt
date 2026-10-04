package dev.adapt.control
import dev.adapt.control.core.protocol.*
import org.junit.Assert.*
import org.junit.Test
class GattFragmentsTest {
    private val frame=Acp.encode(Frame(11,0,42,ByteArray(128) { it.toByte() }))
    private fun part(offset: Int,count: Int,id: Int=1)=byteArrayOf(id.toByte(),0,offset.toByte(),frame.size.toByte())+frame.copyOfRange(offset,offset+count)
    @Test fun mtu23AssemblyRoundTrip() {
        val a=GattFragments(); var decoded: Frame?=null
        for(offset in frame.indices step 16) decoded=a.accept("command",part(offset,minOf(16,frame.size-offset)),offset.toLong())
        assertEquals(42,decoded!!.sequence); assertEquals(128,decoded.payload.size)
    }
    @Test fun crossCharacteristicAndTimeoutDoNotExecutePartialFrame() {
        val a=GattFragments(); assertNull(a.accept("events",part(0,16),0)); assertNull(a.accept("state",part(16,16),1))
        assertNull(a.accept("state",part(32,16),2)); a.accept("events",part(0,16),10)
        assertNull(a.accept("events",part(16,16),1011)); assertNull(a.accept("events",part(32,16),1012))
    }
    @Test fun gapsRegressionDisconnectAndCorruption() {
        val a=GattFragments(); a.accept("events",part(0,16),20); assertNull(a.accept("events",part(16,16),19))
        a.accept("events",part(0,16),30); assertNull(a.accept("events",part(32,16),31))
        a.accept("events",part(0,16),40); a.clear(); assertNull(a.accept("events",part(16,16),41))
        val corrupt=frame.copyOf().also { it[30]=(-1).toByte() }
        assertNull(a.accept("events",byteArrayOf(1,0,0,140.toByte())+corrupt,50))
    }
}
