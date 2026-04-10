package org.example.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Serializable
data class PyProcParams(
    val version: Long,
    val type: String,
    val sgWindow: Int,
    val sgOrder: Int,
    val kalmanQ: Double,
    val kalmanR: Double,
    val recordDurationSec: Double,
    val fftEnabled: Boolean,
    val fftFmin: Double,
    val fftFmax: Double,
    val fftZeroPadFactor: Int,
    val fftUseAutoBand: Boolean
)

@Serializable
data class PyProcRequest(
    val type: String = "process",
    val channel: Int,
    val seqStart: Long,
    val params: PyProcParams,
    val tail: IntArray = intArrayOf(),
    val chunk: IntArray = intArrayOf()
)

@Serializable
data class PyProcResponse(
    val ok: Boolean,
    val channel: Int? = null,
    val seqStart: Long? = null,
    val paramsVersion: Long? = null,
    val filtered: DoubleArray = doubleArrayOf(),
    val peakCount: Int? = null,
    val fftDominantFreq: Double? = null,
    val fftDominantAmp: Double? = null,
    val fftFreq: DoubleArray = doubleArrayOf(),
    val fftSpec: DoubleArray = doubleArrayOf(),
    val error: String? = null,
    val trace: String? = null
)

@Serializable
data class PyRenderPlotRequest(
    val type: String = "render_plot",
    val channel: Int,
    val paramsVersion: Long,
    val plotKind: String,
    val raw: IntArray = intArrayOf(),
    val filtered: DoubleArray = doubleArrayOf(),
    val fftFreq: DoubleArray = doubleArrayOf(),
    val fftSpec: DoubleArray = doubleArrayOf(),
    val viewStartIndex: Int? = null,
    val viewEndExclusive: Int? = null
)

@Serializable
data class PyRenderPlotResponse(
    val ok: Boolean,
    val channel: Int? = null,
    val paramsVersion: Long? = null,
    val plotKind: String? = null,
    val imageBase64: String? = null,
    val error: String? = null,
    val trace: String? = null
)

class PythonProcessorClient(
    private val resourcePath: String = "worker/python_worker.py",
) : ProcessorClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var workerFile: File? = null

    private fun ensureStarted() {
        val p = process
        if (p != null && p.isAlive) return

        stop()

        val worker = resolveWorkerFile(resourcePath)
        workerFile = worker

        val launchCommands = buildLaunchCommands(worker)
        var lastErr: Throwable? = null

        for (cmd in launchCommands) {
            repeat(2) { attempt ->
                try {
                    val pb = ProcessBuilder(cmd)
                    pb.redirectErrorStream(true)

                    val started = pb.start()
                    val r = BufferedReader(InputStreamReader(started.inputStream))
                    val w = BufferedWriter(OutputStreamWriter(started.outputStream))

                    val hello = r.readLine()
                    require(hello == "READY") {
                        val extra = if (hello.isNullOrBlank()) "<no output>" else hello
                        "Worker did not become ready. Got: $extra"
                    }

                    process = started
                    reader = r
                    writer = w
                    return
                } catch (t: Throwable) {
                    lastErr = t
                    Thread.sleep(120L + attempt * 40L)
                }
            }
        }

        throw IllegalStateException(
            "Failed to start worker for ${worker.absolutePath}: ${lastErr?.message}",
            lastErr
        )
    }

    private fun resolveWorkerFile(pathInResources: String): File {
        workerFile?.let { if (it.exists()) return it }

        val direct = File(pathInResources)
        if (direct.exists()) return direct

        val localCandidates = listOf(
            File("composeApp/src/desktopMain/resources/$pathInResources"),
            File("src/desktopMain/resources/$pathInResources"),
            File("/mnt/data/${File(pathInResources).name}")
        )
        localCandidates.firstOrNull { it.exists() }?.let { return it }

        val cl = this::class.java.classLoader
        val url = cl.getResource(pathInResources)
            ?: error("Worker resource not found: $pathInResources")

        if (url.protocol.equals("file", ignoreCase = true)) {
            return File(URI(url.toString()))
        }

        cl.getResourceAsStream(pathInResources)?.use { input ->
            val suffix = when {
                pathInResources.endsWith(".exe", ignoreCase = true) -> ".exe"
                pathInResources.endsWith(".py", ignoreCase = true) -> ".py"
                else -> ""
            }
            val tmp = Files.createTempFile("python_worker_", suffix).toFile()
            tmp.deleteOnExit()
            input.copyTo(tmp.outputStream())
            return tmp
        }

        error("Worker resource stream not found: $pathInResources")
    }

    private fun buildLaunchCommands(worker: File): List<List<String>> {
        return if (worker.extension.equals("exe", ignoreCase = true)) {
            worker.setExecutable(true)
            listOf(listOf(worker.absolutePath))
        } else {
            val cmds = mutableListOf<List<String>>()
            val envPython = System.getenv("PYTHON_EXECUTABLE")?.trim().orEmpty()
            if (envPython.isNotEmpty()) cmds += listOf(envPython, worker.absolutePath)
            cmds += listOf("python", worker.absolutePath)
            cmds += listOf("python3", worker.absolutePath)
            cmds += listOf("py", "-3", worker.absolutePath)
            cmds
        }
    }

    override fun stop() {
        val p = process

        try {
            writer?.close()
        } catch (_: Throwable) {
        }

        try {
            reader?.close()
        } catch (_: Throwable) {
        }

        if (p != null) {
            try {
                p.destroy()
                p.waitFor(500, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {
            }

            if (p.isAlive) {
                try {
                    p.destroyForcibly()
                } catch (_: Throwable) {
                }
            }
        }

        writer = null
        reader = null
        process = null
    }

    override fun process(
        channel: Int,
        seqStart: Long,
        rawTail: IntArray,
        rawChunk: IntArray,
        params: ProcParams
    ): ProcResponse {
        ensureStarted()

        val req = PyProcRequest(
            channel = channel,
            seqStart = seqStart,
            params = PyProcParams(
                version = params.version,
                type = params.type,
                sgWindow = params.sgWindow,
                sgOrder = params.sgOrder,
                kalmanQ = params.kalmanQ,
                kalmanR = params.kalmanR,
                recordDurationSec = params.recordDurationSec,
                fftEnabled = params.fftEnabled,
                fftFmin = params.fftFmin,
                fftFmax = params.fftFmax,
                fftZeroPadFactor = params.fftZeroPadFactor,
                fftUseAutoBand = params.fftUseAutoBand
            ),
            tail = rawTail,
            chunk = rawChunk
        )

        val respLine = sendAndRead(json.encodeToString(PyProcRequest.serializer(), req))
        val resp = json.decodeFromString(PyProcResponse.serializer(), respLine)

        if (!resp.ok) {
            error("Worker error: ${resp.error}\n${resp.trace}")
        }

        return ProcResponse(
            channel = channel,
            seqStart = seqStart,
            paramsVersion = resp.paramsVersion ?: params.version,
            filtered = resp.filtered,
            peakCount = resp.peakCount ?: 0,
            fftDominantFreq = resp.fftDominantFreq,
            fftDominantAmp = resp.fftDominantAmp,
            fftFreq = resp.fftFreq,
            fftSpec = resp.fftSpec
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
    ): PlotRenderResult {
        ensureStarted()

        val req = PyRenderPlotRequest(
            channel = channel,
            paramsVersion = paramsVersion,
            plotKind = plotKind.wireValue,
            raw = raw,
            filtered = filtered,
            fftFreq = fftFreq,
            fftSpec = fftSpec,
            viewStartIndex = viewStartIndex,
            viewEndExclusive = viewEndExclusive
        )

        val respLine = sendAndRead(json.encodeToString(PyRenderPlotRequest.serializer(), req))
        val resp = json.decodeFromString(PyRenderPlotResponse.serializer(), respLine)

        if (!resp.ok) {
            error("Worker render error: ${resp.error}\n${resp.trace}")
        }

        return PlotRenderResult(
            channel = channel,
            paramsVersion = resp.paramsVersion ?: paramsVersion,
            plotKind = plotKind,
            imageBase64 = resp.imageBase64 ?: ""
        )
    }

    private fun sendAndRead(line: String): String = synchronized(this) {
        val w = writer ?: error("worker writer null")
        val r = reader ?: error("worker reader null")

        w.write(line)
        w.newLine()
        w.flush()

        return r.readLine() ?: error("worker closed output")
    }
}
