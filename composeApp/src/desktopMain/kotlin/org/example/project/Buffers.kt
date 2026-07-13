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

    private var totalWrittenValue = 0L
    private var circularOverwriteSamplesValue = 0L
    private var circularOverwriteEventsValue = 0L

    fun clear() {
        head = 0
        size = 0
        oldestSeqValue = 0L
        totalWrittenValue = 0L
        circularOverwriteSamplesValue = 0L
        circularOverwriteEventsValue = 0L
    }

    fun oldestSeq(): Long = oldestSeqValue
    fun newestSeqExclusive(): Long = oldestSeqValue + size

    fun capacity(): Int = capacity
    fun currentSize(): Int = size
    fun totalWritten(): Long = totalWrittenValue
    fun circularOverwriteSamples(): Long = circularOverwriteSamplesValue
    fun circularOverwriteEvents(): Long = circularOverwriteEventsValue
    fun circularOverwriteDetected(): Boolean =
        circularOverwriteSamplesValue > 0L || circularOverwriteEventsValue > 0L

    fun stats(): RingBufferStats {
        return RingBufferStats(
            capacity = capacity,
            currentSize = size,
            oldestSeq = oldestSeqValue,
            newestSeqExclusive = newestSeqExclusive(),
            totalWritten = totalWrittenValue,
            circularOverwriteSamples = circularOverwriteSamplesValue,
            circularOverwriteEvents = circularOverwriteEventsValue,
            circularOverwriteDetected = circularOverwriteDetected()
        )
    }

    fun appendAll(values: ShortArray, offset: Int = 0, length: Int = values.size - offset): Int {
        if (values.isEmpty()) return 0

        val safeOffset = offset.coerceIn(0, values.size)
        val safeLength = length.coerceIn(0, values.size - safeOffset)
        if (safeLength <= 0) return 0

        if (safeLength >= capacity) {
            val srcStart = safeOffset + safeLength - capacity
            val discarded = size + (safeLength - capacity)

            System.arraycopy(values, srcStart, data, 0, capacity)

            recordCircularOverwrite(discarded)

            oldestSeqValue = newestSeqExclusive() + safeLength - capacity
            head = 0
            size = capacity
            totalWrittenValue += safeLength.toLong()
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
        totalWrittenValue += safeLength.toLong()
        return safeLength
    }

    fun appendAllElementWise(
        values: ShortArray,
        offset: Int = 0,
        length: Int = values.size - offset
    ): Int {
        if (values.isEmpty()) return 0

        val safeOffset = offset.coerceIn(0, values.size)
        val safeLength = length.coerceIn(0, values.size - safeOffset)
        if (safeLength <= 0) return 0

        if (safeLength >= capacity) {
            val srcStart = safeOffset + safeLength - capacity
            val discarded = size + (safeLength - capacity)

            for (i in 0 until capacity) {
                data[i] = values[srcStart + i]
            }

            recordCircularOverwrite(discarded)

            oldestSeqValue = newestSeqExclusive() + safeLength - capacity
            head = 0
            size = capacity
            totalWrittenValue += safeLength.toLong()
            return safeLength
        }

        makeRoomFor(safeLength)

        val tail = (head + size) % capacity
        val firstPart = min(safeLength, capacity - tail)

        var src = safeOffset
        var dst = tail

        repeat(firstPart) {
            data[dst] = values[src]
            src += 1
            dst += 1
        }

        val remaining = safeLength - firstPart
        if (remaining > 0) {
            dst = 0
            repeat(remaining) {
                data[dst] = values[src]
                src += 1
                dst += 1
            }
        }

        size += safeLength
        totalWrittenValue += safeLength.toLong()
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
        require(offset + requiredBytes <= src.size) {
            "appendDecodedInt16LeFromByteArray out of bounds"
        }

        if (sampleCount >= capacity) {
            val keep = capacity
            val startSample = sampleCount - keep
            val startOffset = offset + startSample * 2
            val discarded = size + (sampleCount - keep)

            recordCircularOverwrite(discarded)

            oldestSeqValue = newestSeqExclusive() + sampleCount - keep
            head = 0
            size = keep

            decodeIntoContiguous(src, startOffset, keep, physicalStart = 0)
            totalWrittenValue += sampleCount.toLong()
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
        totalWrittenValue += sampleCount.toLong()
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
        require(offset + requiredBytes <= src.size) {
            "appendDecodedInt16LeFromByteArrayFast out of bounds"
        }

        // Jalur BIN+ZC tidak boleh fallback ke jalur append biasa.
        // Data payload langsung di-decode dari ByteArray MQTT ke backing ShortArray
        // ring buffer, termasuk saat posisi ring sudah circular.
        if (sampleCount >= capacity) {
            val keep = capacity
            val startSample = sampleCount - keep
            val startOffset = offset + startSample * 2
            val discarded = size + (sampleCount - keep)

            recordCircularOverwrite(discarded)

            oldestSeqValue = newestSeqExclusive() + sampleCount - keep
            head = 0
            size = keep

            decodeIntoContiguous(src, startOffset, keep, physicalStart = 0)
            totalWrittenValue += sampleCount.toLong()
            return sampleCount
        }

        makeRoomFor(sampleCount)

        val tail = (head + size) % capacity
        val firstPart = min(sampleCount, capacity - tail)
        decodeIntoContiguous(src, offset, firstPart, physicalStart = tail)

        val remaining = sampleCount - firstPart
        if (remaining > 0) {
            decodeIntoContiguous(
                src = src,
                byteOffset = offset + firstPart * 2,
                sampleCount = remaining,
                physicalStart = 0
            )
        }

        size += sampleCount
        totalWrittenValue += sampleCount.toLong()
        return sampleCount
    }

    private fun makeRoomFor(incomingCount: Int) {
        val discardCount = max(0, size + incomingCount - capacity)
        if (discardCount > 0) {
            recordCircularOverwrite(discardCount)

            head = (head + discardCount) % capacity
            size -= discardCount
            oldestSeqValue += discardCount.toLong()
        }
    }

    private fun recordCircularOverwrite(discardCount: Int) {
        if (discardCount <= 0) return
        circularOverwriteSamplesValue += discardCount.toLong()
        circularOverwriteEventsValue += 1L
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
        if (count <= 0) return
        require(size + count <= capacity) {
            "commitAppend would exceed capacity: size=$size count=$count capacity=$capacity"
        }

        size += count
        totalWrittenValue += count.toLong()
    }
}

data class RingBufferStats(
    val capacity: Int,
    val currentSize: Int,
    val oldestSeq: Long,
    val newestSeqExclusive: Long,
    val totalWritten: Long,
    val circularOverwriteSamples: Long,
    val circularOverwriteEvents: Long,
    val circularOverwriteDetected: Boolean
)

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

data class ShortAppendReservation(
    val start1: Int,
    val len1: Int,
    val start2: Int,
    val len2: Int,
    val count: Int
)