package dev.adapt.control.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Frame(val type: Int, val flags: Int, val sequence: Int, val payload: ByteArray)
data class ButtonEvent(val gesture: Int, val action: Int, val timestamp: Long, val sequence: Int)

/** Project ACP v0.1. CRC is corruption detection, never authentication. */
object Acp {
    fun crc(bytes: ByteArray): Int {
        var crc = 0xffff
        for (b in bytes) {
            crc = crc xor ((b.toInt() and 255) shl 8)
            repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xffff }
        }
        return crc
    }
    private fun validate(f: Frame) {
        require(f.type in 1..18 && f.flags in 0..2 && f.sequence in 0..65535 && f.payload.size <= 128)
        when (f.type) {
            4,14 -> require(f.flags == 2)
            12,13 -> require(f.flags == 1)
            else -> require(f.flags != 2 || f.type == 16)
        }
        if (f.type == 11) require(f.flags == 0)
        val response = f.flags != 0
        val length = when(f.type) {
            1 -> 2; 2 -> if(response) 4 else 0; 3 -> if(response) 26 else 0
            4 -> 11; 5,13,18 -> 3; 6 -> 5; 7 -> if(response) 5 else 1
            11,12 -> f.payload.size; 14 -> 6; 15 -> if(response) 19 else 1
            16 -> if(response) 5 else 0; 17 -> if(response) 83 else 0; else -> 0
        }
        require(f.payload.size == length) { "Invalid ACP payload length" }
    }
    fun encode(f: Frame): ByteArray {
        validate(f)
        val b = ByteBuffer.allocate(12 + f.payload.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0x41).put(0x43).put(0).put(1).put(f.type.toByte()).put(f.flags.toByte())
        b.putShort(f.sequence.toShort()).putShort(f.payload.size.toShort()).put(f.payload)
        b.putShort(crc(b.array().copyOf(b.position())).toShort())
        return b.array()
    }
    fun decode(bytes: ByteArray): Frame {
        require(bytes.size in 12..140)
        require(bytes[0] == 0x41.toByte() && bytes[1] == 0x43.toByte())
        require(bytes[2] == 0.toByte() && bytes[3] == 1.toByte())
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val length = b.getShort(8).toInt() and 65535
        require(length <= 128 && bytes.size == length + 12)
        require((b.getShort(bytes.size-2).toInt() and 65535) == crc(bytes.copyOf(bytes.size-2)))
        return Frame(bytes[4].toInt() and 255, bytes[5].toInt() and 255,
            b.getShort(6).toInt() and 65535, bytes.copyOfRange(10,bytes.size-2)).also(::validate)
    }
    fun button(f: Frame): ButtonEvent {
        require(f.type == 4 && f.flags == 2 && f.payload.size == 11)
        val b = ByteBuffer.wrap(f.payload).order(ByteOrder.LITTLE_ENDIAN)
        val gesture = b.get().toInt() and 255
        val action = b.short.toInt() and 65535
        require(gesture in 1..3 && (action in 1..6 || action in 16..23)) { "Protected or unknown gesture/action" }
        return ButtonEvent(gesture,action,b.long,f.sequence)
    }
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun unhex(hex: String): ByteArray {
        require(hex.length in 24..280 && hex.length % 2 == 0 && hex.all { it in "0123456789abcdefABCDEF" })
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
