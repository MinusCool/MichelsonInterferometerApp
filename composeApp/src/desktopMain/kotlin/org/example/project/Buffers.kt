package org.example.project

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class IntRingBuffer(private val capacity: Int) {
    private val data = ArrayDeque<Int>(capacity)
    private var oldestSeqValue = 0L

    fun append(value: Int) {
        if (data.size == capacity) {
            data.removeFirst()
            oldestSeqValue += 1L
        }
        data.addLast(value)
    }

    fun clear() {
        data.clear()
    }

    fun oldestSeq(): Long = oldestSeqValue
    fun newestSeqExclusive(): Long = oldestSeqValue + data.size

    fun readChunk(seqStart: Long, maxCount: Int): IntArray {
        if (maxCount <= 0 || data.isEmpty()) return IntArray(0)
        val start = max(seqStart, oldestSeqValue)
        val endExclusive = min(start + maxCount.toLong(), newestSeqExclusive())
        if (endExclusive <= start) return IntArray(0)
        val from = (start - oldestSeqValue).toInt()
        val to = (endExclusive - oldestSeqValue).toInt()
        return data.toList().subList(from, to).toIntArray()
    }

    fun snapshotLast(maxCount: Int): List<Int> {
        if (maxCount <= 0) return emptyList()
        val list = data.toList()
        return if (list.size <= maxCount) list else list.takeLast(maxCount)
    }

    fun snapshotAll(): List<Int> = data.toList()
}

class DoubleRingBuffer(private val capacity: Int) {
    private val data = ArrayDeque<Double>(capacity)
    private var oldestSeqValue = 0L

    fun append(value: Double) {
        if (data.size == capacity) {
            data.removeFirst()
            oldestSeqValue += 1L
        }
        data.addLast(value)
    }

    fun appendAll(values: DoubleArray) {
        values.forEach { append(it) }
    }

    fun clear() {
        data.clear()
    }

    fun oldestSeq(): Long = oldestSeqValue
    fun newestSeqExclusive(): Long = oldestSeqValue + data.size

    fun snapshotLast(maxCount: Int): List<Double> {
        if (maxCount <= 0) return emptyList()
        val list = data.toList()
        return if (list.size <= maxCount) list else list.takeLast(maxCount)
    }

    fun snapshotAll(): List<Double> = data.toList()
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
    rawBufByChannel: Map<Int, IntRingBuffer>,
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
    rawBufByChannel: Map<Int, IntRingBuffer>,
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
