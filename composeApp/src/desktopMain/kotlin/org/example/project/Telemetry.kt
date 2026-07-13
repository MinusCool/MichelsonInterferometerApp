package org.example.project

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

data class PacketTelemetryRecord(
    val variant: Int,              // 1=string, 2=binary, 3=binary+zc
    val runId: Long? = null,       // BARU: identitas run untuk cegah kontaminasi lintas-run
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

    // Tetap dipakai untuk validator C2:
    // BODY decode + store saja (fair untuk BIN vs BIN+ZC)
    val decodeUs: Double,

    // Baru: parse + body decode + store
    // dipakai untuk perbandingan adil STRING vs BIN vs BIN+ZC
    val decodeCompareUs: Double? = null,

    // Total: parse + validate + crc + body decode + store + bookkeeping minimum
    val decodeTotalUs: Double? = null,

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

    val decodeCompareP50Us: Double,
    val decodeCompareP99Us: Double,
    val decodeCompareJitterUs: Double,

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

    val decodeCompareP50Us: Double,
    val decodeCompareP99Us: Double,
    val decodeCompareJitterUs: Double,

    val throughputSamplesPerSec: Double,
    val crcBad: Int,
    val lenBad: Int,
    val qMax: Double,
    val durationSec: Double,
    val repetitionCount: Int
)

data class SystemTestTelemetryRecord(
    val runId: Long?,
    val variant: Int,
    val speed: Int,
    val repetitions: Int,
    val ringBufferCapacity: Int,
    val snapshotIntervalMs: Long,

    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,

    val totalWrittenSamples: Long,
    val circularOverwriteSamples: Long,
    val circularOverwriteEvents: Long,
    val circularOverwriteDetected: Boolean,

    val processingDroppedSamples: Long,
    val processingOverrunEvents: Long,
    val processingOverrunDetected: Boolean,
    val processingBacklogMaxSamples: Long,

    val snapshotCount: Long,
    val snapshotLateEvents: Long,
    val snapshotDroppedSamples: Long,
    val snapshotOverrunEvents: Long,
    val snapshotLateDetected: Boolean,
    val snapshotMaxDurationMs: Double,
    val snapshotAvgDurationMs: Double,

    val decodeQueueMaxDepth: Int,
    val decodeQueueWaitP50Us: Double,
    val decodeQueueWaitP99Us: Double,
    val decodeQueueWaitMaxUs: Long,

    val systemSafe: Boolean,
    val note: String
)

data class BufferSnapshotConfigRecord(
    val runId: Long?,
    val isReference: Boolean,
    val variant: Int,
    val speed: Int,
    val repetitions: Int,
    val ringBufferCapacity: Int,
    val snapshotIntervalMs: Long,

    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,

    val packets: Int,
    val totalSamples: Long,
    val avgBytesOnWire: Double,
    val lossRateQ: Double,
    val throughputSamplesPerSec: Double,
    val crcBad: Int,
    val lenBad: Int,

    val totalWrittenSamples: Long,
    val circularOverwriteSamples: Long,
    val circularOverwriteEvents: Long,
    val circularOverwriteDetected: Boolean,

    val processingDroppedSamples: Long,
    val processingOverrunEvents: Long,
    val processingOverrunDetected: Boolean,
    val processingBacklogMaxSamples: Long,

    val snapshotCount: Long,
    val snapshotLateEvents: Long,
    val snapshotDroppedSamples: Long,
    val snapshotOverrunEvents: Long,
    val snapshotLateDetected: Boolean,
    val snapshotMaxDurationMs: Double,
    val snapshotAvgDurationMs: Double,

    val decodeQueueMaxDepth: Int,
    val decodeQueueWaitP50Us: Double,
    val decodeQueueWaitP99Us: Double,
    val decodeQueueWaitMaxUs: Long,

    val qReference: Double?,
    val throughputReferenceSamplesPerSec: Double?,
    val qNotIncreased: Boolean,
    val throughputNotDecreased: Boolean,

    val systemSafe: Boolean,
    val proposalCriteriaPassed: Boolean,
    val note: String
)
private data class RunRepKey(
    val runId: Long?,
    val repId: Int
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

private fun durationSecFromValidPackets(validPackets: List<PacketTelemetryRecord>): Double {
    if (validPackets.isEmpty()) return 0.0
    if (validPackets.size == 1) return 1e-6

    val recvSorted = validPackets.sortedBy { it.tRecvMonoUs }
    val first = recvSorted.first().tRecvMonoUs.toDouble()
    val last = recvSorted.last().tRecvMonoUs.toDouble()

    val dtUs = last - first
    return maxOf(1e-6, dtUs / 1_000_000.0)
}

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
        .groupBy { RunRepKey(runId = it.runId, repId = it.repId) }
        .toList()
        .sortedWith(
            compareBy<Pair<RunRepKey, List<PacketTelemetryRecord>>>(
                { it.first.runId ?: Long.MIN_VALUE },
                { it.first.repId }
            )
        )
        .map { (key, repRecords) ->
            val repId = key.repId
            val packetList = repRecords.sortedBy { it.tRecvMonoUs }
            val variant = packetList.firstOrNull()?.variant ?: 0
            val validPackets = packetList.filter { it.accepted && it.writtenSamples > 0 }

            val crcBad = packetList.count { it.crcOk == false }
            val lenBad = packetList.count { it.lenOk == false }
            val q = gapRateFromReceiveOrder(packetList)

            if (validPackets.isEmpty()) {
                val dAll = packetList.map { it.decodeUs.toDouble() }
                val dCompareAll = packetList.mapNotNull { it.decodeCompareUs }

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

                    decodeCompareP50Us = percentile(dCompareAll, 50.0),
                    decodeCompareP99Us = percentile(dCompareAll, 99.0),
                    decodeCompareJitterUs = percentile(dCompareAll, 99.0) - percentile(dCompareAll, 50.0),

                    throughputSamplesPerSec = 0.0,
                    crcBad = crcBad,
                    lenBad = lenBad,
                    q = q,
                    durationSec = 0.0
                )
            }

            val latencies = validPackets.map(::packetLatencyUs)
            val decodes = validPackets.map { it.decodeUs.toDouble() }
            val decodeCompare = validPackets.mapNotNull { it.decodeCompareUs }
            val bytesAll = packetList.map { it.bytesOnWire.toDouble() }

            val p50L = percentile(latencies, 50.0)
            val p99L = percentile(latencies, 99.0)
            val p50D = percentile(decodes, 50.0)
            val p99D = percentile(decodes, 99.0)

            val p50DC = percentile(decodeCompare, 50.0)
            val p99DC = percentile(decodeCompare, 99.0)

            val totalSamples = validPackets.sumOf { it.writtenSamples.toLong() }

            val dtSec = durationSecFromValidPackets(validPackets)
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

                decodeCompareP50Us = p50DC,
                decodeCompareP99Us = p99DC,
                decodeCompareJitterUs = p99DC - p50DC,

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

            decodeCompareP50Us = Double.NaN,
            decodeCompareP99Us = Double.NaN,
            decodeCompareJitterUs = Double.NaN,

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
        val dCompareAll = allPackets.mapNotNull { it.decodeCompareUs }

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

            decodeCompareP50Us = percentile(dCompareAll, 50.0),
            decodeCompareP99Us = percentile(dCompareAll, 99.0),
            decodeCompareJitterUs = percentile(dCompareAll, 99.0) - percentile(dCompareAll, 50.0),

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
    val decodeCompare = validPackets.mapNotNull { it.decodeCompareUs }
    val totalSamples = validPackets.sumOf { it.writtenSamples.toLong() }

    val dtSec = durationSecFromValidPackets(validPackets)
    val throughput = if (dtSec > 0.0) totalSamples / dtSec else 0.0

    val p50L = percentile(latencies, 50.0)
    val p99L = percentile(latencies, 99.0)
    val p50D = percentile(decodes, 50.0)
    val p99D = percentile(decodes, 99.0)

    val p50DC = percentile(decodeCompare, 50.0)
    val p99DC = percentile(decodeCompare, 99.0)

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

        decodeCompareP50Us = p50DC,
        decodeCompareP99Us = p99DC,
        decodeCompareJitterUs = p99DC - p50DC,

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
            "variant,run_id,rep_id,seq,t_send_us,t_recv_epoch_us,t_recv_mono_us,esp_minus_pc_offset_us," +
                    "sample_count,channel_count,sample_fmt,bytes_on_wire,decode_us,decode_compare_us,decode_total_us,crc_ok,len_ok,accepted,written_samples"
        )
        records.forEach { r ->
            w.appendLine(
                listOf(
                    r.variant,
                    r.runId?.toString() ?: "",
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
                    r.decodeCompareUs?.toString() ?: "",
                    r.decodeTotalUs?.toString() ?: "",
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
                    "decode_compare_p50_us,decode_compare_p99_us,decode_compare_jitter_us," +
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
                    r.decodeCompareP50Us,
                    r.decodeCompareP99Us,
                    r.decodeCompareJitterUs,
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
                summary.decodeCompareP50Us,
                summary.decodeCompareP99Us,
                summary.decodeCompareJitterUs,
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

fun exportSystemTestTelemetryCsv(record: SystemTestTelemetryRecord): File {
    val file = File(experimentsDir(), "system_test_telemetry_${timestampLabel()}.csv")

    file.bufferedWriter().use { w ->
        w.appendLine(
            "run_id,variant,speed,repetitions,ring_buffer_capacity,snapshot_interval_ms," +
                    "started_at_epoch_ms,ended_at_epoch_ms,duration_ms," +
                    "total_written_samples,circular_overwrite_samples,circular_overwrite_events,circular_overwrite_detected," +
                    "processing_dropped_samples,processing_overrun_events,processing_overrun_detected,processing_backlog_max_samples," +
                    "snapshot_count,snapshot_late_events,snapshot_dropped_samples,snapshot_overrun_events,snapshot_late_detected,snapshot_max_duration_ms,snapshot_avg_duration_ms," +
                    "decode_queue_max_depth,decode_queue_wait_p50_us,decode_queue_wait_p99_us,decode_queue_wait_max_us," +
                    "system_safe,note"
        )

        w.appendLine(
            listOf(
                record.runId?.toString() ?: "",
                record.variant,
                record.speed,
                record.repetitions,
                record.ringBufferCapacity,
                record.snapshotIntervalMs,
                record.startedAtEpochMs,
                record.endedAtEpochMs,
                record.durationMs,
                record.totalWrittenSamples,
                record.circularOverwriteSamples,
                record.circularOverwriteEvents,
                record.circularOverwriteDetected,
                record.processingDroppedSamples,
                record.processingOverrunEvents,
                record.processingOverrunDetected,
                record.processingBacklogMaxSamples,
                record.snapshotCount,
                record.snapshotLateEvents,
                record.snapshotDroppedSamples,
                record.snapshotOverrunEvents,
                record.snapshotLateDetected,
                record.snapshotMaxDurationMs,
                record.snapshotAvgDurationMs,
                record.decodeQueueMaxDepth,
                record.decodeQueueWaitP50Us,
                record.decodeQueueWaitP99Us,
                record.decodeQueueWaitMaxUs,
                record.systemSafe,
                record.note.replace(",", ";")
            ).joinToString(",")
        )
    }

    return file
}

fun exportBufferSnapshotConfigCsv(record: BufferSnapshotConfigRecord): File {
    val file = File(experimentsDir(), "buffer_snapshot_config_test.csv")
    val writeHeader = !file.exists() || file.length() == 0L

    java.io.FileWriter(file, true).buffered().use { w ->
        if (writeHeader) {
            w.appendLine(
                "run_id,is_reference,variant,speed,repetitions,ring_buffer_capacity,snapshot_interval_ms," +
                        "started_at_epoch_ms,ended_at_epoch_ms,duration_ms," +
                        "packets,total_samples,avg_bytes_on_wire,loss_rate_q,throughput_samples_per_sec,crc_bad,len_bad," +
                        "total_written_samples,circular_overwrite_samples,circular_overwrite_events,circular_overwrite_detected," +
                        "processing_dropped_samples,processing_overrun_events,processing_overrun_detected,processing_backlog_max_samples," +
                        "snapshot_count,snapshot_late_events,snapshot_dropped_samples,snapshot_overrun_events,snapshot_late_detected," +
                        "snapshot_max_duration_ms,snapshot_avg_duration_ms," +
                        "decode_queue_max_depth,decode_queue_wait_p50_us,decode_queue_wait_p99_us,decode_queue_wait_max_us," +
                        "q_reference,throughput_reference_samples_per_sec,q_not_increased,throughput_not_decreased," +
                        "system_safe,proposal_criteria_passed,note"
            )
        }

        w.appendLine(
            listOf(
                record.runId?.toString() ?: "",
                record.isReference,
                record.variant,
                record.speed,
                record.repetitions,
                record.ringBufferCapacity,
                record.snapshotIntervalMs,

                record.startedAtEpochMs,
                record.endedAtEpochMs,
                record.durationMs,

                record.packets,
                record.totalSamples,
                record.avgBytesOnWire,
                record.lossRateQ,
                record.throughputSamplesPerSec,
                record.crcBad,
                record.lenBad,

                record.totalWrittenSamples,
                record.circularOverwriteSamples,
                record.circularOverwriteEvents,
                record.circularOverwriteDetected,

                record.processingDroppedSamples,
                record.processingOverrunEvents,
                record.processingOverrunDetected,
                record.processingBacklogMaxSamples,

                record.snapshotCount,
                record.snapshotLateEvents,
                record.snapshotDroppedSamples,
                record.snapshotOverrunEvents,
                record.snapshotLateDetected,
                record.snapshotMaxDurationMs,
                record.snapshotAvgDurationMs,

                record.decodeQueueMaxDepth,
                record.decodeQueueWaitP50Us,
                record.decodeQueueWaitP99Us,
                record.decodeQueueWaitMaxUs,

                record.qReference?.toString() ?: "",
                record.throughputReferenceSamplesPerSec?.toString() ?: "",
                record.qNotIncreased,
                record.throughputNotDecreased,

                record.systemSafe,
                record.proposalCriteriaPassed,
                record.note.replace(",", ";")
            ).joinToString(",")
        )
    }

    return file
}