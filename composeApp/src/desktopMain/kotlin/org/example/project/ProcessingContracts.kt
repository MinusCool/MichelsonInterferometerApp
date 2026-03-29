package org.example.project

interface ProcessorClient {
    fun stop()
    fun process(
        channel: Int,
        seqStart: Long,
        rawTail: IntArray,
        rawChunk: IntArray,
        params: ProcParams
    ): ProcResponse

    fun renderPlot(
        channel: Int,
        paramsVersion: Long,
        startupTrimEnabled: Boolean,
        startupTrimSamples: Int,
        plotKind: PlotKind,
        raw: IntArray,
        filtered: DoubleArray,
        fftFreq: DoubleArray,
        fftSpec: DoubleArray,
        viewStartIndex: Int? = null,
        viewEndExclusive: Int? = null
    ): PlotRenderResult
}

data class ProcParams(
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
    val fftUseAutoBand: Boolean,
    val startupTrimEnabled: Boolean,
    val startupTrimSamples: Int
)

data class ProcResponse(
    val channel: Int,
    val seqStart: Long,
    val paramsVersion: Long,
    val filtered: DoubleArray,
    val peakCount: Int,
    val fftDominantFreq: Double?,
    val fftDominantAmp: Double?,
    val fftFreq: DoubleArray,
    val fftSpec: DoubleArray
)

enum class PlotKind(val wireValue: String) {
    Signal("signal"),
    Fft("fft")
}

data class PlotRenderResult(
    val channel: Int,
    val paramsVersion: Long,
    val plotKind: PlotKind,
    val imageBase64: String
)
