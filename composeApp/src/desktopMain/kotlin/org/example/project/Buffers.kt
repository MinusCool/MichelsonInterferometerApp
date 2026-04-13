package org.example.project

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class ShortRingBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val data = ShortArray(capacity)
    private var head = 0
    private var size = 0
    private var oldestSeqValue = 0L

    fun clear() {
        head = 0
        size = 0
        oldestSeqValue = 0L
    }

    fun oldestSeq(): Long = oldestSeqValue
    fun newestSeqExclusive(): Long = oldestSeqValue + size

    fun appendAll(values: ShortArray, offset: Int = 0, length: Int = values.size - offset): Int {
        if (values.isEmpty()) return 0

        val safeOffset = offset.coerceIn(0, values.size)
        val safeLength = length.coerceIn(0, values.size - safeOffset)
        if (safeLength <= 0) return 0

        if (safeLength >= capacity) {
            val srcStart = safeOffset + safeLength - capacity
            System.arraycopy(values, srcStart, data, 0, capacity)
            oldestSeqValue = newestSeqExclusive() + safeLength - capacity
            head = 0
            size = capacity
            return safeLength
        }

        makeRoomFor(safeLength)

        val tail = (head + size) % capacity
        val firstPart = min(safeLength, capacity - tail)
        System.arraycopy(values, safeOffset, data, tail, firstPart)

        val remaining = safeLength - firstPart
        if (remaining > 0) {
            System.arraycopy(values, safeOffset + firstPart, data, 0, remaining)
        }

        size += safeLength
        return safeLength
    }

    fun appendDecodedInt16LeFromByteArray(
        src: ByteArray,
        offset: Int,
        sampleCount: Int
    ): Int {
        if (sampleCount <= 0) return 0

        val requiredBytes = sampleCount * 2
        require(offset >= 0) { "offset must be >= 0" }
        require(offset + requiredBytes <= src.size) { "appendDecodedInt16LeFromByteArray out of bounds" }

        if (sampleCount >= capacity) {
            val keep = capacity
            val startSample = sampleCount - keep
            val startOffset = offset + startSample * 2

            oldestSeqValue = newestSeqExclusive() + sampleCount - keep
            head = 0
            size = keep

            decodeIntoContiguous(src, startOffset, keep, physicalStart = 0)
            return sampleCount
        }

        makeRoomFor(sampleCount)

        val tail = (head + size) % capacity
        val firstPart = min(sampleCount, capacity - tail)
        decodeIntoContiguous(src, offset, firstPart, physicalStart = tail)

        val remaining = sampleCount - firstPart
        if (remaining > 0) {
            decodeIntoContiguous(src, offset + firstPart * 2, remaining, physicalStart = 0)
        }

        size += sampleCount
        return sampleCount
    }

    fun appendDecodedInt16LeFromByteArrayFast(
        src: ByteArray,
        offset: Int,
        sampleCount: Int
    ): Int {
        if (sampleCount <= 0) return 0

        val requiredBytes = sampleCount * 2
        require(offset >= 0) { "offset must be >= 0" }
        require(offset + requiredBytes <= src.size) { "appendDecodedInt16LeFromByteArrayFast out of bounds" }

        // Fast path: buffer masih kontigu, belum wrap, dan belum perlu discard
        if (head == 0 && size + sampleCount <= capacity) {
            val tail = size
            decodeIntoContiguous(src, offset, sampleCount, physicalStart = tail)
            size += sampleCount
            return sampleCount
        }

        return appendDecodedInt16LeFromByteArray(src, offset, sampleCount)
    }

    private fun makeRoomFor(incomingCount: Int) {
        val discardCount = max(0, size + incomingCount - capacity)
        if (discardCount > 0) {
            head = (head + discardCount) % capacity
            size -= discardCount
            oldestSeqValue += discardCount.toLong()
        }
    }

    private fun decodeIntoContiguous(
        src: ByteArray,
        byteOffset: Int,
        sampleCount: Int,
        physicalStart: Int
    ) {
        val dstData = data
        var p = byteOffset
        var dst = physicalStart
        var remaining = sampleCount

        while (remaining > 0) {
            val lo = src[p].toInt() and 0xFF
            val hi = src[p + 1].toInt()
            dstData[dst] = ((hi shl 8) or lo).toShort()
            p += 2
            dst += 1
            remaining -= 1
        }
    }

    fun readChunk(seqStart: Long, maxCount: Int): IntArray {
        if (maxCount <= 0 || size == 0) return IntArray(0)

        val start = max(seqStart, oldestSeqValue)
        val endExclusive = min(start + maxCount.toLong(), newestSeqExclusive())
        if (endExclusive <= start) return IntArray(0)

        val outSize = (endExclusive - start).toInt()
        val out = IntArray(outSize)
        val startIndex = (start - oldestSeqValue).toInt()

        for (i in 0 until outSize) {
            out[i] = data[(head + startIndex + i) % capacity].toInt()
        }
        return out
    }

    fun snapshotLast(maxCount: Int): List<Int> {
        if (maxCount <= 0 || size == 0) return emptyList()
        val count = min(maxCount, size)
        val out = ArrayList<Int>(count)
        val startIndex = size - count
        for (i in 0 until count) {
            out.add(data[(head + startIndex + i) % capacity].toInt())
        }
        return out
    }

    fun reserveAppend(count: Int): ShortAppendReservation {
        require(count in 1..capacity)
        makeRoomFor(count)
        val tail = (head + size) % capacity
        val len1 = min(count, capacity - tail)
        val len2 = count - len1
        return ShortAppendReservation(
            start1 = tail,
            len1 = len1,
            start2 = 0,
            len2 = len2,
            count = count
        )
    }

    fun backingArray(): ShortArray = data

    fun commitAppend(count: Int) {
        size += count
    }
}

class DoubleRingBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val data = DoubleArray(capacity)
    private var head = 0
    private var size = 0
    private var oldestSeqValue = 0L

    fun appendAll(values: DoubleArray, offset: Int = 0, length: Int = values.size - offset) {
        if (values.isEmpty()) return
        val safeOffset = offset.coerceIn(0, values.size)
        val safeLength = length.coerceIn(0, values.size - safeOffset)
        if (safeLength <= 0) return

        if (safeLength >= capacity) {
            val srcStart = safeOffset + safeLength - capacity
            System.arraycopy(values, srcStart, data, 0, capacity)
            oldestSeqValue = newestSeqExclusive() + safeLength - capacity
            head = 0
            size = capacity
            return
        }

        val discardCount = max(0, size + safeLength - capacity)
        if (discardCount > 0) {
            head = (head + discardCount) % capacity
            size -= discardCount
            oldestSeqValue += discardCount.toLong()
        }

        val tail = (head + size) % capacity
        val firstPart = min(safeLength, capacity - tail)
        System.arraycopy(values, safeOffset, data, tail, firstPart)

        val remaining = safeLength - firstPart
        if (remaining > 0) {
            System.arraycopy(values, safeOffset + firstPart, data, 0, remaining)
        }

        size += safeLength
    }

    fun clear() {
        head = 0
        size = 0
        oldestSeqValue = 0L
    }

    fun snapshotLast(maxCount: Int): List<Double> {
        if (maxCount <= 0 || size == 0) return emptyList()
        val count = min(maxCount, size)
        val out = ArrayList<Double>(count)
        val startIndex = size - count
        for (i in 0 until count) {
            out.add(data[(head + startIndex + i) % capacity])
        }
        return out
    }

    fun oldestSeq(): Long = oldestSeqValue
    fun newestSeqExclusive(): Long = oldestSeqValue + size
}

suspend fun awaitFilterCatchUp(
    rawBufByChannel: Map<Int, ShortRingBuffer>,
    nextSeqToProcess: Map<Int, Long>,
    timeoutMs: Long,
    pollMs: Long
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val allCaughtUp = rawBufByChannel.all { (ch, buf) ->
            (nextSeqToProcess[ch] ?: buf.oldestSeq()) >= buf.newestSeqExclusive()
        }
        if (allCaughtUp) return true
        kotlinx.coroutines.delay(pollMs)
    }
    return false
}

fun exportCsvSnapshotToExperiments(
    rawBufByChannel: Map<Int, ShortRingBuffer>,
    filtBufByChannel: Map<Int, DoubleRingBuffer>,
    rawMax: Int,
    filtMax: Int
): File {
    val dir = File("experiments").apply { mkdirs() }
    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val file = File(dir, "experiment_$ts.csv")
    file.bufferedWriter().use { w ->
        w.appendLine("channel,index,raw,filtered")
        val channels = (rawBufByChannel.keys + filtBufByChannel.keys).toSortedSet()
        for (ch in channels) {
            val raw = rawBufByChannel[ch]?.snapshotLast(rawMax).orEmpty()
            val filt = filtBufByChannel[ch]?.snapshotLast(filtMax).orEmpty()
            val n = max(raw.size, filt.size)
            for (i in 0 until n) {
                val rv = raw.getOrNull(i)?.toString().orEmpty()
                val fv = filt.getOrNull(i)?.toString().orEmpty()
                w.appendLine("$ch,$i,$rv,$fv")
            }
        }
    }
    return file
}

data class ShortAppendReservation(
    val start1: Int,
    val len1: Int,
    val start2: Int,
    val len2: Int,
    val count: Int
)