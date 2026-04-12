package org.example.project

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class IntRingBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val data = IntArray(capacity)
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

    fun append(value: Int) {
        if (size < capacity) {
            val tail = (head + size) % capacity
            data[tail] = value
            size++
        } else {
            data[head] = value
            head = (head + 1) % capacity
            oldestSeqValue++
        }
    }

    fun appendAll(values: IntArray, offset: Int = 0, length: Int = values.size - offset) {
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

        makeRoomFor(safeLength)

        val tail = (head + size) % capacity
        val firstPart = min(safeLength, capacity - tail)
        System.arraycopy(values, safeOffset, data, tail, firstPart)

        val remaining = safeLength - firstPart
        if (remaining > 0) {
            System.arraycopy(values, safeOffset + firstPart, data, 0, remaining)
        }

        size += safeLength
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
            dstData[dst] = ((hi shl 8) or lo).toShort().toInt()

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
            out[i] = data[(head + startIndex + i) % capacity]
        }
        return out
    }

    fun snapshotLast(maxCount: Int): List<Int> {
        if (maxCount <= 0 || size == 0) return emptyList()
        val count = min(maxCount, size)
        val out = ArrayList<Int>(count)
        val startIndex = size - count
        for (i in 0 until count) {
            out.add(data[(head + startIndex + i) % capacity])
        }
        return out
    }
}

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

    fun append(value: Short) {
        if (size < capacity) {
            val tail = (head + size) % capacity
            data[tail] = value
            size++
        } else {
            data[head] = value
            head = (head + 1) % capacity
            oldestSeqValue++
        }
    }

    fun appendAll(values: ShortArray, offset: Int = 0, length: Int = values.size - offset) {
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

        makeRoomFor(safeLength)

        val tail = (head + size) % capacity
        val firstPart = min(safeLength, capacity - tail)
        System.arraycopy(values, safeOffset, data, tail, firstPart)

        val remaining = safeLength - firstPart
        if (remaining > 0) {
            System.arraycopy(values, safeOffset + firstPart, data, 0, remaining)
        }

        size += safeLength
    }

    fun appendAllFromIntArray(values: IntArray, offset: Int = 0, length: Int = values.size - offset): Int {
        if (values.isEmpty()) return 0

        val safeOffset = offset.coerceIn(0, values.size)
        val safeLength = length.coerceIn(0, values.size - safeOffset)
        if (safeLength <= 0) return 0

        if (safeLength >= capacity) {
            val keep = capacity
            val srcStart = safeOffset + safeLength - keep
            for (i in 0 until keep) {
                data[i] = values[srcStart + i].toShort()
            }
            oldestSeqValue = newestSeqExclusive() + safeLength - keep
            head = 0
            size = capacity
            return safeLength
        }

        makeRoomFor(safeLength)

        val tail = (head + size) % capacity
        val firstPart = min(safeLength, capacity - tail)
        for (i in 0 until firstPart) {
            data[tail + i] = values[safeOffset + i].toShort()
        }

        val remaining = safeLength - firstPart
        if (remaining > 0) {
            for (i in 0 until remaining) {
                data[i] = values[safeOffset + firstPart + i].toShort()
            }
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
}

class DoubleRingBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val data = DoubleArray(capacity)
    private var head = 0
    private var size = 0
    private var oldestSeqValue = 0L

    fun append(value: Double) {
        if (size < capacity) {
            val tail = (head + size) % capacity
            data[tail] = value
            size++
        } else {
            data[head] = value
            head = (head + 1) % capacity
            oldestSeqValue++
        }
    }

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

    fun oldestSeq(): Long = oldestSeqValue
    fun newestSeqExclusive(): Long = oldestSeqValue + size

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
}

class LocalProcessorClient(
    private val kalmanXByChannel: MutableMap<Int, Double>,
    private val kalmanPByChannel: MutableMap<Int, Double>
) : ProcessorClient {
    override fun stop() = Unit

    override fun process(
        channel: Int,
        seqStart: Long,
        rawTail: IntArray,
        rawChunk: IntArray,
        params: ProcParams
    ): ProcResponse {
        val filtered = when (params.type) {
            "Kalman" -> localKalman(channel, rawChunk, params.kalmanQ, params.kalmanR)
            else -> rawChunk.map { it.toDouble() }.toDoubleArray()
        }
        return ProcResponse(
            channel = channel,
            seqStart = seqStart,
            paramsVersion = params.version,
            filtered = filtered,
            peakCount = estimatePeaks(filtered),
            fftDominantFreq = null,
            fftDominantAmp = null,
            fftFreq = doubleArrayOf(),
            fftSpec = doubleArrayOf()
        )
    }

    override fun renderPlot(
        channel: Int,
        paramsVersion: Long,
        plotKind: PlotKind,
        raw: IntArray,
        filtered: DoubleArray,
        fftFreq: DoubleArray,
        fftSpec: DoubleArray,
        viewStartIndex: Int?,
        viewEndExclusive: Int?
    ): PlotRenderResult = PlotRenderResult(channel, paramsVersion, plotKind, "")

    private fun localKalman(channel: Int, chunk: IntArray, q: Double, r: Double): DoubleArray {
        var x = kalmanXByChannel[channel] ?: chunk.firstOrNull()?.toDouble() ?: 0.0
        var p = kalmanPByChannel[channel] ?: 1.0
        val out = DoubleArray(chunk.size)
        for (i in chunk.indices) {
            val z = chunk[i].toDouble()
            p += q
            val k = p / (p + r)
            x += k * (z - x)
            p *= (1.0 - k)
            out[i] = x
        }
        kalmanXByChannel[channel] = x
        kalmanPByChannel[channel] = p
        return out
    }

    private fun estimatePeaks(values: DoubleArray): Int {
        if (values.size < 3) return 0
        var count = 0
        for (i in 1 until values.lastIndex) {
            if (values[i] > values[i - 1] && values[i] >= values[i + 1]) count++
        }
        return count
    }
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