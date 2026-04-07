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
    val lenOk: Boolean? = null,
    val accepted: Boolean,
    val writtenSamples: Int
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

data class RepTelemetrySummary(
    val repId: Int,
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
    val q: Double,
    val durationSec: Double
)

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
    val durationSec: Double,
    val repetitionCount: Int
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

private fun recordDurationFallbackSec(validPackets: List<PacketTelemetryRecord>): Double {
    if (validPackets.isEmpty()) return 1e-6
    if (validPackets.size == 1) return 1e-3

    val recvSorted = validPackets.sortedBy { it.tRecvMonoUs }
    val dtUs = (recvSorted.last().tRecvMonoUs - recvSorted.first().tRecvMonoUs).toDouble()
    return maxOf(1e-6, dtUs / 1_000_000.0)
}

fun buildPerRepTelemetrySummaries(records: List<PacketTelemetryRecord>): List<RepTelemetrySummary> {
    if (records.isEmpty()) return emptyList()

    return records
        .groupBy { it.repId }
        .toSortedMap()
        .map { (repId, repRecords) ->
            val packetList = repRecords.sortedBy { it.tRecvMonoUs }
            val validPackets = packetList.filter { it.accepted && it.writtenSamples > 0 }

            val rs = RepTelemetryStats(repId = repId)
            packetList.forEach { p ->
                rs.packets += 1
                rs.samples += p.writtenSamples
                rs.updateSeq(p.seq)
            }

            val crcBad = packetList.count { it.crcOk == false }
            val lenBad = packetList.count { it.lenOk == false }

            val smin = rs.smin
            val smax = rs.smax
            val q = if (smin != null && smax != null && smax >= smin) {
                val nExpected = (smax - smin + 1L).toDouble()
                if (nExpected > 0.0) rs.gTotal.toDouble() / nExpected else Double.NaN
            } else {
                Double.NaN
            }

            if (validPackets.isEmpty()) {
                val dAll = packetList.map { it.decodeUs.toDouble() }
                return@map RepTelemetrySummary(
                    repId = repId,
                    packets = packetList.size,
                    totalSamples = 0,
                    avgBytesOnWire = packetList.map { it.bytesOnWire.toDouble() }.average(),
                    latencyP50Us = Double.NaN,
                    latencyP99Us = Double.NaN,
                    latencyJitterUs = Double.NaN,
                    decodeP50Us = percentile(dAll, 50.0),
                    decodeP99Us = percentile(dAll, 99.0),
                    decodeJitterUs = percentile(dAll, 99.0) - percentile(dAll, 50.0),
                    throughputSamplesPerSec = 0.0,
                    crcBad = crcBad,
                    lenBad = lenBad,
                    q = q,
                    durationSec = 0.0
                )
            }

            val recvSorted = validPackets.sortedBy { it.tRecvMonoUs }

            // Proposal: L = t_recv - t_send
            // Karena offset antar clock dianggap konstan dalam satu sesi/repetisi,
            // kita normalisasi terhadap offset paket valid pertama.
            val rawLatencies = recvSorted.map { p ->
                (p.tRecvMonoUs - p.tSendUs).toDouble()
            }
            val baseOffset = rawLatencies.first()
            val l = rawLatencies.map { it - baseOffset }

            val d = recvSorted.map { it.decodeUs.toDouble() }
            val s = recvSorted.map { it.bytesOnWire.toDouble() }

            val p50L = percentile(l, 50.0)
            val p99L = percentile(l, 99.0)
            val p50D = percentile(d, 50.0)
            val p99D = percentile(d, 99.0)

            val totalSamples = recvSorted.sumOf { it.writtenSamples.toLong() }

            // Proposal: R = N_samples / T
            // T dipakai sebagai durasi pengujian repetisi pada sisi penerima,
            // bukan active send window.
            val dtSec = if (recvSorted.size >= 2) {
                maxOf(
                    1e-6,
                    (recvSorted.last().tRecvMonoUs - recvSorted.first().tRecvMonoUs).toDouble() / 1_000_000.0
                )
            } else {
                1e-6
            }

            val throughput = totalSamples / dtSec

            RepTelemetrySummary(
                repId = repId,
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
                q = q,
                durationSec = dtSec
            )
        }
}

fun buildRunTelemetrySummary(records: List<PacketTelemetryRecord>): RunTelemetrySummary {
    val perRep = buildPerRepTelemetrySummaries(records)
    if (perRep.isEmpty()) {
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
            durationSec = 0.0,
            repetitionCount = 0
        )
    }

    return RunTelemetrySummary(
        packets = perRep.sumOf { it.packets },
        totalSamples = perRep.sumOf { it.totalSamples },
        avgBytesOnWire = perRep.map { it.avgBytesOnWire }.average(),
        latencyP50Us = percentile(perRep.map { it.latencyP50Us }, 50.0),
        latencyP99Us = percentile(perRep.map { it.latencyP99Us }, 50.0),
        latencyJitterUs = percentile(perRep.map { it.latencyJitterUs }, 50.0),
        decodeP50Us = percentile(perRep.map { it.decodeP50Us }, 50.0),
        decodeP99Us = percentile(perRep.map { it.decodeP99Us }, 50.0),
        decodeJitterUs = percentile(perRep.map { it.decodeJitterUs }, 50.0),
        throughputSamplesPerSec = percentile(perRep.map { it.throughputSamplesPerSec }, 50.0),
        crcBad = perRep.sumOf { it.crcBad },
        lenBad = perRep.sumOf { it.lenBad },
        qMax = perRep.map { it.q }.maxOrNull() ?: Double.NaN,
        durationSec = perRep.sumOf { it.durationSec },
        repetitionCount = perRep.size
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
                    "sample_count,channel_count,sample_fmt,bytes_on_wire,decode_us,crc_ok,len_ok,accepted,written_samples"
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
                    r.lenOk?.toString() ?: "",
                    r.accepted,
                    r.writtenSamples
                ).joinToString(",")
            )
        }
    }
    return file
}

fun exportRunTelemetrySummaryCsv(
    summary: RunTelemetrySummary,
    perRep: List<RepTelemetrySummary>
): File {
    val file = File(experimentsDir(), "run_telemetry_summary_${timestampLabel()}.csv")
    file.bufferedWriter().use { w ->
        w.appendLine(
            "scope,rep_id,packets,total_samples,avg_bytes_on_wire," +
                    "latency_p50_us,latency_p99_us,latency_jitter_us," +
                    "decode_p50_us,decode_p99_us,decode_jitter_us," +
                    "throughput_samples_per_sec,crc_bad,len_bad,q,duration_sec,repetition_count"
        )

        perRep.forEach { r ->
            w.appendLine(
                listOf(
                    "rep",
                    r.repId,
                    r.packets,
                    r.totalSamples,
                    r.avgBytesOnWire,
                    r.latencyP50Us,
                    r.latencyP99Us,
                    r.latencyJitterUs,
                    r.decodeP50Us,
                    r.decodeP99Us,
                    r.decodeJitterUs,
                    r.throughputSamplesPerSec,
                    r.crcBad,
                    r.lenBad,
                    r.q,
                    r.durationSec,
                    ""
                ).joinToString(",")
            )
        }

        w.appendLine(
            listOf(
                "agg",
                "",
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
                summary.durationSec,
                summary.repetitionCount
            ).joinToString(",")
        )
    }
    return file
}