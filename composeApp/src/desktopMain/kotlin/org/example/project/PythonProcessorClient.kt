package org.example.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.net.URI
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

@Serializable
data class PyProcParams(
    val version: Long,
    val type: String,
    val sgWindow: Int,
    val sgOrder: Int,
    val kalmanQ: Double,
    val kalmanR: Double
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
    val error: String? = null,
    val trace: String? = null
)

class PythonProcessorClient(
    private val resourcePath: String = "worker/python_worker.exe", // adjust per OS if needed
) : ProcessorClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var workerExe: File? = null

    private fun ensureStarted() {
        val p = process
        if (p != null && p.isAlive) return

        // kalau ada proses lama tapi zombie
        stop()

        val exe = resolveWorkerExe(resourcePath)
        exe.setExecutable(true)

        // Retry start: menghindari CreateProcess error=32 (file terkunci sementara oleh AV/Defender)
        var lastErr: Throwable? = null
        repeat(10) { attempt ->
            try {
                val pb = ProcessBuilder(exe.absolutePath)
                pb.redirectErrorStream(true)
                val started = pb.start()

                val r = BufferedReader(InputStreamReader(started.inputStream))
                val w = BufferedWriter(OutputStreamWriter(started.outputStream))

                val hello = r.readLine()
                require(hello == "READY") { "Worker did not become ready. Got: $hello" }

                process = started
                reader = r
                writer = w
                return
            } catch (t: Throwable) {
                lastErr = t
                // kalau file sedang locked, tunggu sebentar lalu coba lagi
                Thread.sleep(120L + attempt * 30L)
            }
        }
        throw IllegalStateException("Failed to start worker after retries: ${lastErr?.message}", lastErr)
    }

    /**
     * Kalau resource bisa diakses sebagai file di disk (protocol=file),
     * jalankan langsung tanpa extract -> ini menghindari file lock di %TEMP%.
     * Kalau tidak (mis. sudah dipackage ke JAR), extract ke temp unik.
     */
    private fun resolveWorkerExe(pathInResources: String): File {
        // cache supaya tidak extract berulang
        workerExe?.let { if (it.exists()) return it }

        val cl = this::class.java.classLoader
        val url = cl.getResource(pathInResources) ?: error("Worker resource not found: $pathInResources")

        // 1) Running from IDE/Gradle: resource biasanya file://.../processedResources/.../worker/python_worker.exe
        if (url.protocol.equals("file", ignoreCase = true)) {
            val file = File(URI(url.toString()))
            workerExe = file
            return file
        }

        // 2) Packaged (jar): extract ke temp
        cl.getResourceAsStream(pathInResources)?.use { input ->
            val suffix = if (pathInResources.endsWith(".exe")) ".exe" else ""
            val tmp = Files.createTempFile("python_worker_", suffix).toFile()
            tmp.deleteOnExit()
            input.copyTo(tmp.outputStream())
            workerExe = tmp
            return tmp
        }

        error("Worker resource stream not found: $pathInResources")
    }

    fun stop() {
        val p = process
        try { writer?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}

        if (p != null) {
            try {
                p.destroy()
                p.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {}
            if (p.isAlive) {
                try { p.destroyForcibly() } catch (_: Throwable) {}
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
                kalmanR = params.kalmanR
            ),
            tail = rawTail,
            chunk = rawChunk
        )

        val line = json.encodeToString(PyProcRequest.serializer(), req)

        val w = writer ?: error("worker writer null")
        val r = reader ?: error("worker reader null")

        w.write(line)
        w.newLine()
        w.flush()

        val respLine = r.readLine() ?: error("Worker terminated unexpectedly")
        val resp = json.decodeFromString(PyProcResponse.serializer(), respLine)

        if (!resp.ok) {
            error("Worker error: ${resp.error}\n${resp.trace}")
        }

        return ProcResponse(
            channel = channel,
            seqStart = seqStart,
            paramsVersion = resp.paramsVersion ?: params.version,
            filtered = resp.filtered
        )
    }

//    fun stop() {
//        try {
//            writer?.close()
//            reader?.close()
//        } catch (_: Throwable) {}
//        try {
//            process?.destroy()
//        } catch (_: Throwable) {}
//        writer = null
//        reader = null
//        process = null
//    }

    private fun extractResourceToTempFile(pathInResources: String): File {
        val cls = this::class.java.classLoader
        val input = cls.getResourceAsStream(pathInResources)
            ?: error("Worker resource not found: $pathInResources")

        val suffix = if (pathInResources.endsWith(".exe")) ".exe" else ""
        val tmp = Files.createTempFile("python_worker_", suffix).toFile()
        tmp.deleteOnExit()
        input.use { it.copyTo(tmp.outputStream()) }
        return tmp
    }
}