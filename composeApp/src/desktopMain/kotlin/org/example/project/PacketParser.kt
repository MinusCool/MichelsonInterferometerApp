package org.example.project

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Variant(val label: String, val code: Int) {
    TEXT("TEXT", 1),
    BIN("BIN", 2),
    BIN_ZC("BIN+ZC", 3),
    UNKNOWN("UNKNOWN", 0)
}

object PacketSpec {
    const val MAGIC = 0xB547
    const val VERSION = 1
    const val MSG_TYPE_DATA = 0
    const val HEADER_LEN = 30
    const val CHANNEL_COUNT_1 = 1
    const val SAMPLE_FMT_INT16_LE = 1
    const val FLAG_ZEROCOPY_HINT = 0x01
}

data class TextPacket(
    val runId: Long?,
    val repId: Int,
    val seq: Long,
    val tSendUs: Long,
    val sampleCount: Int,
    val channelCount: Int,
    val sampleFmt: Int,
    val samples: IntArray
)

data class BinaryHeader(
    val flags: Int,
    val repId: Int,
    val seq: Long,
    val tSendUs: Long,
    val sampleCount: Int,
    val channelCount: Int,
    val sampleFmt: Int,
    val payloadBytes: Int,
    val payloadCrc32: Long,
    val lenOk: Boolean,
    val crcOk: Boolean,
    val payloadOffset: Int
)

fun detectVariant(payload: ByteArray): Variant {
    if (
        payload.startsWithPrefix("run_id=".toByteArray()) ||
        payload.startsWithPrefix("rep_id=".toByteArray())
    ) {
        return Variant.TEXT
    }

    if (payload.size >= 2) {
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.short.toInt() and 0xFFFF
        if (magic == PacketSpec.MAGIC) {
            val flags = if (payload.size > 6) payload[6].toInt() and 0xFF else 0
            return if ((flags and PacketSpec.FLAG_ZEROCOPY_HINT) != 0) Variant.BIN_ZC else Variant.BIN
        }
    }

    return Variant.UNKNOWN
}

fun parseTextPacket(payload: ByteArray): TextPacket {
    val s = payload.toString(Charsets.UTF_8).trim()
    val parts = s.split("|")
    require(parts.size >= 8) { "TEXT: fields kurang" }

    val kv = mutableMapOf<String, String>()
    for (part in parts.dropLast(1)) {
        val idx = part.indexOf('=')
        if (idx > 0) {
            kv[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
        }
    }

    val runId = kv["run_id"]?.toLong()
    val repId = kv["rep_id"]?.toInt() ?: error("TEXT: rep_id missing")
    val seq = kv["seq"]?.toLong() ?: error("TEXT: seq missing")
    val tSendUs = kv["t_send_us"]?.toLong() ?: error("TEXT: t_send_us missing")
    val sampleCount = kv["sample_count"]?.toInt() ?: error("TEXT: sample_count missing")
    val channelCount = kv["channel_count"]?.toInt() ?: error("TEXT: channel_count missing")
    val sampleFmt = kv["sample_fmt"]?.toInt() ?: error("TEXT: sample_fmt missing")

    val samples = parts.last()
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .map { it.toInt() }
        .toIntArray()

    require(samples.size == sampleCount) {
        "TEXT: sample_count mismatch ($sampleCount != ${samples.size})"
    }

    return TextPacket(
        runId = runId,
        repId = repId,
        seq = seq,
        tSendUs = tSendUs,
        sampleCount = sampleCount,
        channelCount = channelCount,
        sampleFmt = sampleFmt,
        samples = samples
    )
}

fun parseBinaryPacketHeader(payload: ByteArray): BinaryHeader {
    require(payload.size >= PacketSpec.HEADER_LEN) { "BIN: payload < header_len" }

    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

    val magic = bb.short.toInt() and 0xFFFF
    val version = bb.get().toInt() and 0xFF
    val msgType = bb.get().toInt() and 0xFF
    val headerLen = bb.short.toInt() and 0xFFFF
    val flags = bb.get().toInt() and 0xFF
    val repId = bb.get().toInt() and 0xFF
    val seq = bb.int.toLong() and 0xFFFFFFFFL
    val tSendUs = bb.long
    val sampleCount = bb.short.toInt() and 0xFFFF
    val channelCount = bb.get().toInt() and 0xFF
    val sampleFmt = bb.get().toInt() and 0xFF
    val payloadBytes = bb.short.toInt() and 0xFFFF
    val payloadCrc32 = bb.int.toLong() and 0xFFFFFFFFL

    require(magic == PacketSpec.MAGIC) { "BIN: bad magic" }
    require(version == PacketSpec.VERSION) { "BIN: bad version $version" }
    require(msgType == PacketSpec.MSG_TYPE_DATA) { "BIN: bad msg_type $msgType" }
    require(headerLen == PacketSpec.HEADER_LEN) { "BIN: bad header_len $headerLen" }
    require(channelCount == PacketSpec.CHANNEL_COUNT_1) { "BIN: unexpected channel_count $channelCount" }
    require(sampleFmt == PacketSpec.SAMPLE_FMT_INT16_LE) { "BIN: unexpected sample_fmt $sampleFmt" }
    require(payloadBytes % 2 == 0) { "BIN: payload_bytes not multiple of 2" }
    require((payloadBytes / 2) == sampleCount * channelCount) { "BIN: count mismatch" }

    val totalNeeded = headerLen + payloadBytes
    val lenOk = payload.size == totalNeeded
    val crcOk = lenOk && crc32Of(payload, headerLen, payloadBytes) == payloadCrc32

    return BinaryHeader(
        flags = flags,
        repId = repId,
        seq = seq,
        tSendUs = tSendUs,
        sampleCount = sampleCount,
        channelCount = channelCount,
        sampleFmt = sampleFmt,
        payloadBytes = payloadBytes,
        payloadCrc32 = payloadCrc32,
        lenOk = lenOk,
        crcOk = crcOk,
        payloadOffset = headerLen
    )
}

fun decodeInt16LeToIntArray(
    src: ByteArray,
    offset: Int,
    sampleCount: Int
): IntArray {
    if (sampleCount <= 0) return IntArray(0)
    val requiredBytes = sampleCount * 2
    require(offset >= 0) { "offset must be >= 0" }
    require(offset + requiredBytes <= src.size) { "decodeInt16LeToIntArray out of bounds" }

    val out = IntArray(sampleCount)
    var p = offset
    for (i in 0 until sampleCount) {
        val lo = src[p].toInt() and 0xFF
        val hi = src[p + 1].toInt()
        out[i] = ((hi shl 8) or lo).toShort().toInt()
        p += 2
    }
    return out
}

fun decodeInt16LeToShortArray(
    src: ByteArray,
    offset: Int,
    sampleCount: Int
): ShortArray {
    if (sampleCount <= 0) return ShortArray(0)
    val requiredBytes = sampleCount * 2
    require(offset >= 0) { "offset must be >= 0" }
    require(offset + requiredBytes <= src.size) { "decodeInt16LeToShortArray out of bounds" }

    val out = ShortArray(sampleCount)
    var p = offset
    for (i in 0 until sampleCount) {
        val lo = src[p].toInt() and 0xFF
        val hi = src[p + 1].toInt()
        out[i] = ((hi shl 8) or lo).toShort()
        p += 2
    }
    return out
}

fun decodeControlText(payload: ByteArray): String =
    payload.toString(Charsets.UTF_8).trim().removeSurrounding("\"").trim()

fun textPreview(bytes: ByteArray, limit: Int = 160): String {
    val s = bytes.toString(Charsets.UTF_8).replace("\r", "\\r").replace("\n", "\\n")
    return if (s.length > limit) s.take(limit) + "..." else s
}

fun hexPreview(bytes: ByteArray, limit: Int = 32): String {
    val s = bytes.take(limit).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
    return if (bytes.size > limit) "$s ..." else s
}

private fun ByteArray.startsWithPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

fun decodeInt16LeIntoShortArray(
    src: ByteArray,
    offset: Int,
    sampleCount: Int,
    dst: ShortArray,
    dstOffset: Int
) {
    var p = offset
    var d = dstOffset
    repeat(sampleCount) {
        val lo = src[p].toInt() and 0xFF
        val hi = src[p + 1].toInt()
        dst[d] = ((hi shl 8) or lo).toShort()
        p += 2
        d += 1
    }
}