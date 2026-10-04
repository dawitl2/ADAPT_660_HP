package dev.adapt.control.core.protocol

/** Proposed custom firmware GATT contract, not a claim about stock EPOS services. */
class GattFragments {
    private var frameId=-1
    private var characteristic=""
    private var total=0
    private var used=0
    private var lastTime=0L
    private val buffer=ByteArray(140)
    @Synchronized fun clear() { frameId=-1; characteristic=""; total=0; used=0; lastTime=0 }
    @Synchronized fun accept(channel: String,value: ByteArray,nowMs: Long): Frame? {
        if(value.size<5 || nowMs<0) { clear(); return null }
        val id=(value[0].toInt() and 255) or ((value[1].toInt() and 255) shl 8)
        val offset=value[2].toInt() and 255
        val length=value[3].toInt() and 255
        val data=value.size-4
        if(length !in 12..140 || offset+data>length) { clear(); return null }
        if(offset==0) { clear(); frameId=id; characteristic=channel; total=length; lastTime=nowMs }
        if(frameId!=id || characteristic!=channel || total!=length || offset!=used || nowMs<lastTime || nowMs-lastTime>1000) {
            clear(); return null
        }
        value.copyInto(buffer,used,4); used+=data; lastTime=nowMs
        if(used!=total) return null
        val frame=runCatching { Acp.decode(buffer.copyOf(total)) }.getOrNull()
        clear(); return frame
    }
}
