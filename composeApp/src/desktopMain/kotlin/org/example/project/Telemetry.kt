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
    val espMinusPcOffsetUs: Long? = null,
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
    val variant: Int,
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
    val variant: Int,
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
    val clean = xs.filter { it.isFinite() }
    if (clean.isEmpty()) return Double.NaN

    val sorted = clean.sorted()
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

private fun packetLatencyUs(packet: PacketTelemetryRecord): Double {
    // Dua-way sync memberi estimasi:
    // clock ESP = clock PC + theta
    // Maka timestamp ESP pada domain PC:
    // t_send_pc_domain = t_send_esp - theta
    //
    // Latency:
    // L = t_recv_pc - (t_send_esp - theta)
    //   = t_recv_pc - t_send_esp + theta
    val theta = packet.espMinusPcOffsetUs?.toDouble() ?: 0.0
    return packet.tRecvMonoUs.toDouble() - packet.tSendUs.toDouble() + theta
}

private fun durationSecFromSenderTimestamps(validPackets: List<PacketTelemetryRecord>): Double {
    if (validPackets.isEmpty()) return 0.0
    if (validPackets.size == 1) return 1e-6

    val sendSorted = validPackets.sortedBy { it.tSendUs }
    val first = sendSorted.first().tSendUs.toDouble()
    val last = sendSorted.last().tSendUs.toDouble()

    val dtUs = last - first
    return maxOf(1e-6, dtUs / 1_000_000.0)
}

//private fun durationSecFromValidPackets(validPackets: List<PacketTelemetryRecord>): Double {
//    if (validPackets.isEmpty()) return 0.0
//    if (validPackets.size == 1) return 1e-6
//
//    val recvSorted = validPackets.sortedBy { it.tRecvMonoUs }
//    val dtUs = (recvSorted.last().tRecvMonoUs - recvSorted.first().tRecvMonoUs).toDouble()
//    return maxOf(1e-6, dtUs / 1_000_000.0)
//}

private fun gapRateFromReceiveOrder(packetList: List<PacketTelemetryRecord>): Double {
    if (packetList.isEmpty()) return Double.NaN

    val rs = RepTelemetryStats(repId = -1)
    packetList.sortedBy { it.tRecvMonoUs }.forEach { p ->
        rs.updateSeq(p.seq)
    }

    val smin = rs.smin
    val smax = rs.smax
    if (smin == null || smax == null || smax < smin) return Double.NaN

    val nExpected = (smax - smin + 1L).toDouble()
    return if (nExpected > 0.0) rs.gTotal.toDouble() / nExpected else Double.NaN
}

fun buildPerRepTelemetrySummaries(records: List<PacketTelemetryRecord>): List<RepTelemetrySummary> {
    if (records.isEmpty()) return emptyList()

    return records
        .groupBy { it.repId }
        .toSortedMap()
        .map { (repId, repRecords) ->
            val packetList = repRecords.sortedBy { it.tRecvMonoUs }
            val variant = packetList.firstOrNull()?.variant ?: 0
            val validPackets = packetList.filter { it.accepted && it.writtenSamples > 0 }

            val crcBad = packetList.count { it.crcOk == false }
            val lenBad = packetList.count { it.lenOk == false }
            val q = gapRateFromReceiveOrder(packetList)

            if (validPackets.isEmpty()) {
                val dAll = packetList.map { it.decodeUs.toDouble() }
                return@map RepTelemetrySummary(
                    variant = variant,
                    repId = repId,
                    packets = packetList.size,
                    totalSamples = 0L,
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

            val latencies = validPackets.map(::packetLatencyUs)
            val decodes = validPackets.map { it.decodeUs.toDouble() }
            val bytesAll = packetList.map { it.bytesOnWire.toDouble() }

            val p50L = percentile(latencies, 50.0)
            val p99L = percentile(latencies, 99.0)
            val p50D = percentile(decodes, 50.0)
            val p99D = percentile(decodes, 99.0)

            val totalSamples = validPackets.sumOf { it.writtenSamples.toLong() }

            // R = N_samples / T
            // T dipakai sebagai durasi aktif pengiriman pada sisi sender
            // berdasarkan rentang timestamp t_send_us paket valid.
            val dtSec = durationSecFromSenderTimestamps(validPackets)
            val throughput = if (dtSec > 0.0) totalSamples / dtSec else 0.0

            RepTelemetrySummary(
                variant = variant,
                repId = repId,
                packets = packetList.size,
                totalSamples = totalSamples,
                avgBytesOnWire = bytesAll.average(),
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
    if (records.isEmpty()) {
        return RunTelemetrySummary(
            variant = 0,
            packets = 0,
            totalSamples = 0L,
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

    val allPackets = records.sortedBy { it.tRecvMonoUs }
    val validPackets = allPackets.filter { it.accepted && it.writtenSamples > 0 }
    val perRep = buildPerRepTelemetrySummaries(records)
    val variant = allPackets.firstOrNull()?.variant ?: 0

    val avgBytesOnWire = allPackets.map { it.bytesOnWire.toDouble() }.average()
    val crcBad = allPackets.count { it.crcOk == false }
    val lenBad = allPackets.count { it.lenOk == false }

    if (validPackets.isEmpty()) {
        val dAll = allPackets.map { it.decodeUs.toDouble() }
        return RunTelemetrySummary(
            variant = variant,
            packets = allPackets.size,
            totalSamples = 0L,
            avgBytesOnWire = avgBytesOnWire,
            latencyP50Us = Double.NaN,
            latencyP99Us = Double.NaN,
            latencyJitterUs = Double.NaN,
            decodeP50Us = percentile(dAll, 50.0),
            decodeP99Us = percentile(dAll, 99.0),
            decodeJitterUs = percentile(dAll, 99.0) - percentile(dAll, 50.0),
            throughputSamplesPerSec = 0.0,
            crcBad = crcBad,
            lenBad = lenBad,
            qMax = perRep.map { it.q }.filter { it.isFinite() }.maxOrNull() ?: Double.NaN,
            durationSec = 0.0,
            repetitionCount = perRep.size
        )
    }

    val latencies = validPackets.map(::packetLatencyUs)
    val decodes = validPackets.map { it.decodeUs.toDouble() }
    val totalSamples = validPackets.sumOf { it.writtenSamples.toLong() }

    // R = N_samples / T
    // Untuk run agregat, T juga dipakai dari domain waktu sender (t_send_us),
    // bukan receive-window di receiver.
    val dtSec = durationSecFromSenderTimestamps(validPackets)
    val throughput = if (dtSec > 0.0) totalSamples / dtSec else 0.0

    val p50L = percentile(latencies, 50.0)
    val p99L = percentile(latencies, 99.0)
    val p50D = percentile(decodes, 50.0)
    val p99D = percentile(decodes, 99.0)

    return RunTelemetrySummary(
        variant = variant,
        packets = allPackets.size,
        totalSamples = totalSamples,
        avgBytesOnWire = avgBytesOnWire,
        latencyP50Us = p50L,
        latencyP99Us = p99L,
        latencyJitterUs = p99L - p50L,
        decodeP50Us = p50D,
        decodeP99Us = p99D,
        decodeJitterUs = p99D - p50D,
        throughputSamplesPerSec = throughput,
        crcBad = crcBad,
        lenBad = lenBad,
        qMax = perRep.map { it.q }.filter { it.isFinite() }.maxOrNull() ?: Double.NaN,
        durationSec = dtSec,
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
            "variant,rep_id,seq,t_send_us,t_recv_epoch_us,t_recv_mono_us,esp_minus_pc_offset_us," +
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
                    r.espMinusPcOffsetUs?.toString() ?: "",
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
            "scope,variant,rep_id,packets,total_samples,avg_bytes_on_wire," +
                    "latency_p50_us,latency_p99_us,latency_jitter_us," +
                    "decode_p50_us,decode_p99_us,decode_jitter_us," +
                    "throughput_samples_per_sec,crc_bad,len_bad,q,duration_sec,repetition_count"
        )

        perRep.forEach { r ->
            w.appendLine(
                listOf(
                    "rep",
                    r.variant,
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
                summary.variant,
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