package org.example.project

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

data class PacketTelemetryRecord(
    val variant: Int,              // 1=text, 2=binary, 3=binary+zc
    val repId: Int,
    val seq: Long,
    val tSendUs: Long,
    val tRecvEpochUs: Long,
    val tRecvMonoUs: Long,
    val sampleCount: Int,
    val channelCount: Int,
    val sampleFmt: Int,
    val bytesOnWire: Int,          // len(payload) for all variants
    val decodeUs: Long,            // parse + decode + store
    val crcOk: Boolean? = null,
    val lenOk: Boolean? = null
)

data class RepTelemetryStats(
    val repId: Int,
    var packets: Int = 0,
    var samples: Int = 0,
    var smin: Long? = null,
    var smax: Long? = null,
    var lastSeq: Long? = null,
    var gTotal: Long = 0,
    var outOfOrder: Int = 0
) {
    fun updateSeq(seq: Long) {
        if (smin == null || seq < smin!!) smin = seq
        if (smax == null || seq > smax!!) smax = seq

        val prev = lastSeq
        if (prev == null) {
            lastSeq = seq
            return
        }

        if (seq > prev) {
            gTotal += maxOf(0L, seq - prev - 1L)
            lastSeq = seq
        } else {
            outOfOrder += 1
        }
    }
}

data class RunTelemetrySummary(
    val packets: Int,
    val totalSamples: Long,
    val avgBytesOnWire: Double,
    val latencyP50Us: Double,
    val latencyP99Us: Double,
    val latencyJitterUs: Double,
    val decodeP50Us: Double,
    val decodeP99Us: Double,
    val decodeJitterUs: Double,
    val throughputSamplesPerSec: Double,
    val crcBad: Int,
    val lenBad: Int,
    val qMax: Double,
    val durationSec: Double
)

private fun percentile(xs: List<Double>, p: Double): Double {
    if (xs.isEmpty()) return Double.NaN
    val sorted = xs.sorted()
    if (p <= 0.0) return sorted.first()
    if (p >= 100.0) return sorted.last()

    val k = (sorted.size - 1) * (p / 100.0)
    val f = floor(k).toInt()
    val c = ceil(k).toInt()
    if (f == c) return sorted[f]

    val d0 = sorted[f] * (c - k)
    val d1 = sorted[c] * (k - f)
    return d0 + d1
}

fun buildRunTelemetrySummary(records: List<PacketTelemetryRecord>): RunTelemetrySummary {
    if (records.isEmpty()) {
        return RunTelemetrySummary(
            packets = 0,
            totalSamples = 0,
            avgBytesOnWire = Double.NaN,
            latencyP50Us = Double.NaN,
            latencyP99Us = Double.NaN,
            latencyJitterUs = Double.NaN,
            decodeP50Us = Double.NaN,
            decodeP99Us = Double.NaN,
            decodeJitterUs = Double.NaN,
            throughputSamplesPerSec = Double.NaN,
            crcBad = 0,
            lenBad = 0,
            qMax = Double.NaN,
            durationSec = 0.0
        )
    }

    val reps = linkedMapOf<Int, RepTelemetryStats>()
    val packetList = records.sortedBy { it.tRecvMonoUs }

    packetList.forEach { p ->
        val rs = reps.getOrPut(p.repId) { RepTelemetryStats(repId = p.repId) }
        rs.packets += 1
        rs.samples += p.sampleCount * p.channelCount
        rs.updateSeq(p.seq)
    }

    val anchorSend = packetList.first().tSendUs
    val anchorRecvMono = packetList.first().tRecvMonoUs

    val lRel = packetList.map { p ->
        ((p.tRecvMonoUs - anchorRecvMono) - (p.tSendUs - anchorSend)).toDouble()
    }
    val d = packetList.map { it.decodeUs.toDouble() }
    val s = packetList.map { it.bytesOnWire.toDouble() }

    val p50L = percentile(lRel, 50.0)
    val p99L = percentile(lRel, 99.0)
    val p50D = percentile(d, 50.0)
    val p99D = percentile(d, 99.0)

    val t0 = packetList.first().tRecvMonoUs
    val t1 = packetList.last().tRecvMonoUs
    val dtSec = maxOf(1e-6, (t1 - t0).toDouble() / 1_000_000.0)

    val totalSamples = packetList.sumOf { (it.sampleCount * it.channelCount).toLong() }
    val throughput = totalSamples / dtSec

    val crcBad = packetList.count { it.crcOk == false }
    val lenBad = packetList.count { it.lenOk == false }

    val qMax = reps.values.mapNotNull { rs ->
        val smin = rs.smin
        val smax = rs.smax
        if (smin == null || smax == null) return@mapNotNull null
        val nExpected = (smax - smin + 1L)
        if (nExpected <= 0L) return@mapNotNull null
        rs.gTotal.toDouble() / nExpected.toDouble()
    }.maxOrNull() ?: Double.NaN

    return RunTelemetrySummary(
        packets = packetList.size,
        totalSamples = totalSamples,
        avgBytesOnWire = s.average(),
        latencyP50Us = p50L,
        latencyP99Us = p99L,
        latencyJitterUs = p99L - p50L,
        decodeP50Us = p50D,
        decodeP99Us = p99D,
        decodeJitterUs = p99D - p50D,
        throughputSamplesPerSec = throughput,
        crcBad = crcBad,
        lenBad = lenBad,
        qMax = qMax,
        durationSec = dtSec
    )
}

private fun experimentsDir(): File {
    val dir = File("experiments")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun timestampLabel(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

fun exportPacketTelemetryCsv(records: List<PacketTelemetryRecord>): File {
    val file = File(experimentsDir(), "packet_telemetry_${timestampLabel()}.csv")
    file.bufferedWriter().use { w ->
        w.appendLine(
            "variant,rep_id,seq,t_send_us,t_recv_epoch_us,t_recv_mono_us," +
                    "sample_count,channel_count,sample_fmt,bytes_on_wire,decode_us,crc_ok,len_ok"
        )
        records.forEach { r ->
            w.appendLine(
                listOf(
                    r.variant,
                    r.repId,
                    r.seq,
                    r.tSendUs,
                    r.tRecvEpochUs,
                    r.tRecvMonoUs,
                    r.sampleCount,
                    r.channelCount,
                    r.sampleFmt,
                    r.bytesOnWire,
                    r.decodeUs,
                    r.crcOk?.toString() ?: "",
                    r.lenOk?.toString() ?: ""
                ).joinToString(",")
            )
        }
    }
    return file
}

fun exportRunTelemetrySummaryCsv(summary: RunTelemetrySummary): File {
    val file = File(experimentsDir(), "run_telemetry_summary_${timestampLabel()}.csv")
    file.bufferedWriter().use { w ->
        w.appendLine(
            "packets,total_samples,avg_bytes_on_wire," +
                    "latency_p50_us,latency_p99_us,latency_jitter_us," +
                    "decode_p50_us,decode_p99_us,decode_jitter_us," +
                    "throughput_samples_per_sec,crc_bad,len_bad,q_max,duration_sec"
        )
        w.appendLine(
            listOf(
                summary.packets,
                summary.totalSamples,
                summary.avgBytesOnWire,
                summary.latencyP50Us,
                summary.latencyP99Us,
                summary.latencyJitterUs,
                summary.decodeP50Us,
                summary.decodeP99Us,
                summary.decodeJitterUs,
                summary.throughputSamplesPerSec,
                summary.crcBad,
                summary.lenBad,
                summary.qMax,
                summary.durationSec
            ).joinToString(",")
        )
    }
    return file
}