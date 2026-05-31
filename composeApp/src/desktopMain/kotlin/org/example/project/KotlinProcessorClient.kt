package org.example.project

import kotlin.math.*

class KotlinProcessorClient : ProcessorClient {
    private data class KalmanState(var x: Double, var p: Double, var version: Long)

    private val kalmanByChannel = mutableMapOf<Int, KalmanState>()
    private val filteredHistoryByChannel = mutableMapOf<Int, ArrayDeque<Double>>()
    private val historyVersionByChannel = mutableMapOf<Int, Long>()

    override fun stop() {
        synchronized(this) {
            kalmanByChannel.clear()
            filteredHistoryByChannel.clear()
            historyVersionByChannel.clear()
        }
    }

    override fun process(
        channel: Int,
        seqStart: Long,
        rawTail: IntArray,
        rawChunk: IntArray,
        params: ProcParams
    ): ProcResponse {
        if (rawChunk.isEmpty()) {
            return ProcResponse(
                channel = channel,
                seqStart = seqStart,
                paramsVersion = params.version,
                filtered = doubleArrayOf(),
                peakCount = 0,
                fftDominantFreq = null,
                fftDominantAmp = null,
                fftFreq = doubleArrayOf(),
                fftSpec = doubleArrayOf()
            )
        }

        val combinedRaw = DoubleArray(rawTail.size + rawChunk.size)
        for (i in rawTail.indices) combinedRaw[i] = rawTail[i].toDouble()
        for (i in rawChunk.indices) combinedRaw[rawTail.size + i] = rawChunk[i].toDouble()

        val filteredChunk = when (params.type) {
            "SG" -> {
                val fullFiltered = savitzkyGolayLike(
                    x = combinedRaw,
                    window = params.sgWindow,
                    poly = params.sgOrder
                )
                fullFiltered.copyOfRange(rawTail.size, fullFiltered.size)
            }

            "Kalman" -> {
                kalmanProcess(
                    channel = channel,
                    chunk = rawChunk,
                    q = params.kalmanQ,
                    r = params.kalmanR,
                    version = params.version
                )
            }

            else -> {
                DoubleArray(rawChunk.size) { i -> rawChunk[i].toDouble() }
            }
        }

        val peakCount = updateGlobalPeakCount(
            channel = channel,
            version = params.version,
            yChunk = filteredChunk
        )

        val fft = if (params.fftEnabled) {
            computeFftSummary(
                signal = combinedRaw,
                recordDurationSec = params.recordDurationSec,
                fftFmin = params.fftFmin,
                fftFmax = params.fftFmax,
                zeroPadFactor = params.fftZeroPadFactor,
                useAutoBand = params.fftUseAutoBand
            )
        } else {
            FftSummary(null, null, doubleArrayOf(), doubleArrayOf())
        }

        return ProcResponse(
            channel = channel,
            seqStart = seqStart,
            paramsVersion = params.version,
            filtered = filteredChunk,
            peakCount = peakCount,
            fftDominantFreq = fft.dominantFreq,
            fftDominantAmp = fft.dominantAmp,
            fftFreq = fft.freq,
            fftSpec = fft.spec
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
        return PlotRenderResult(
            channel = channel,
            paramsVersion = paramsVersion,
            plotKind = plotKind,
            imageBase64 = ""
        )
    }

    private fun kalmanProcess(
        channel: Int,
        chunk: IntArray,
        q: Double,
        r: Double,
        version: Long
    ): DoubleArray {
        if (chunk.isEmpty()) return doubleArrayOf()

        val state = synchronized(this) {
            val old = kalmanByChannel[channel]
            if (old == null || old.version != version) {
                KalmanState(
                    x = chunk.first().toDouble(),
                    p = 1.0,
                    version = version
                ).also { kalmanByChannel[channel] = it }
            } else {
                old
            }
        }

        val out = DoubleArray(chunk.size)

        var x = state.x
        var p = state.p
        val safeQ = q.coerceAtLeast(1e-12)
        val safeR = r.coerceAtLeast(1e-12)

        for (i in chunk.indices) {
            val z = chunk[i].toDouble()
            val pPred = p + safeQ
            val k = pPred / (pPred + safeR)
            x += k * (z - x)
            p = (1.0 - k) * pPred
            out[i] = x
        }

        synchronized(this) {
            kalmanByChannel[channel] = KalmanState(x, p, version)
        }

        return out
    }

    private fun savitzkyGolayLike(
        x: DoubleArray,
        window: Int,
        poly: Int
    ): DoubleArray {
        if (x.size < 3) return x.copyOf()

        var w = if (window % 2 == 0) window + 1 else window
        w = w.coerceIn(3, 301)
        if (w > x.size) {
            w = if (x.size % 2 == 1) x.size else x.size - 1
            if (w < 3) return x.copyOf()
        }

        val p = poly.coerceIn(1, min(10, w - 1))
        val half = w / 2
        val out = DoubleArray(x.size)

        for (i in x.indices) {
            val left = max(0, i - half)
            val right = min(x.size, i + half + 1)
            val n = right - left
            val degree = min(p, n - 1)

            if (degree <= 0) {
                var sum = 0.0
                for (j in left until right) sum += x[j]
                out[i] = sum / n
                continue
            }

            val ata = Array(degree + 1) { DoubleArray(degree + 1) }
            val aty = DoubleArray(degree + 1)

            for (j in left until right) {
                val t = (j - i).toDouble()
                val powers = DoubleArray(degree * 2 + 1)
                powers[0] = 1.0
                for (k in 1 until powers.size) powers[k] = powers[k - 1] * t

                for (row in 0..degree) {
                    aty[row] += powers[row] * x[j]
                    for (col in 0..degree) {
                        ata[row][col] += powers[row + col]
                    }
                }
            }

            val coeff = solveLinearSystem(ata, aty)
            out[i] = coeff?.getOrNull(0) ?: x[i]
        }

        return out
    }

    private fun solveLinearSystem(
        aInput: Array<DoubleArray>,
        bInput: DoubleArray
    ): DoubleArray? {
        val n = bInput.size
        val a = Array(n) { r -> aInput[r].copyOf() }
        val b = bInput.copyOf()

        for (col in 0 until n) {
            var pivot = col
            var pivotAbs = abs(a[col][col])

            for (r in col + 1 until n) {
                val v = abs(a[r][col])
                if (v > pivotAbs) {
                    pivot = r
                    pivotAbs = v
                }
            }

            if (pivotAbs < 1e-12) return null

            if (pivot != col) {
                val tmpRow = a[col]
                a[col] = a[pivot]
                a[pivot] = tmpRow

                val tmpB = b[col]
                b[col] = b[pivot]
                b[pivot] = tmpB
            }

            val div = a[col][col]
            for (c in col until n) a[col][c] /= div
            b[col] /= div

            for (r in 0 until n) {
                if (r == col) continue
                val factor = a[r][col]
                if (abs(factor) < 1e-18) continue

                for (c in col until n) {
                    a[r][c] -= factor * a[col][c]
                }
                b[r] -= factor * b[col]
            }
        }

        return b
    }

    private fun updateGlobalPeakCount(
        channel: Int,
        version: Long,
        yChunk: DoubleArray,
        maxHistory: Int = 10_000
    ): Int {
        val history = synchronized(this) {
            val oldVersion = historyVersionByChannel[channel]
            if (oldVersion == null || oldVersion != version) {
                filteredHistoryByChannel[channel] = ArrayDeque()
                historyVersionByChannel[channel] = version
            }

            filteredHistoryByChannel.getOrPut(channel) { ArrayDeque() }.also { h ->
                for (v in yChunk) {
                    h.addLast(v)
                    while (h.size > maxHistory) h.removeFirst()
                }
            }.toList()
        }

        return countFilteredPeaksGlobal(history)
    }

    private fun countFilteredPeaksGlobal(
        values: List<Double>,
        minPeakDistance: Int = 3,
        relHeight: Double = 0.15,
        relProminence: Double = 0.08,
        prominenceWindow: Int = 12
    ): Int {
        if (values.size < 3) return 0

        val minV = values.minOrNull() ?: return 0
        val maxV = values.maxOrNull() ?: return 0
        val span = maxV - minV
        if (span <= 1e-12) return 0

        val peakThreshold = minV + relHeight * span
        val prominenceThreshold = max(span * relProminence, 1e-9)
        val distance = max(1, minPeakDistance)
        val window = max(distance + 1, prominenceWindow)

        var count = 0
        var lastPeak = -10_000

        for (i in 1 until values.lastIndex) {
            val prev = values[i - 1]
            val cur = values[i]
            val next = values[i + 1]

            val localMax =
                (cur > prev && cur >= next) ||
                        (cur >= prev && cur > next)

            if (!localMax) continue
            if (cur < peakThreshold) continue
            if (i - lastPeak < distance) continue

            val leftStart = max(0, i - window)
            val rightEnd = min(values.size, i + window + 1)

            var leftMin = cur
            for (j in leftStart until i) leftMin = min(leftMin, values[j])

            var rightMin = cur
            for (j in i + 1 until rightEnd) rightMin = min(rightMin, values[j])

            val prominence = cur - max(leftMin, rightMin)
            if (prominence < prominenceThreshold) continue

            count++
            lastPeak = i
        }

        return count
    }

    private data class FftSummary(
        val dominantFreq: Double?,
        val dominantAmp: Double?,
        val freq: DoubleArray,
        val spec: DoubleArray
    )

    private fun computeFftSummary(
        signal: DoubleArray,
        recordDurationSec: Double,
        fftFmin: Double,
        fftFmax: Double,
        zeroPadFactor: Int,
        useAutoBand: Boolean
    ): FftSummary {
        if (signal.size < 16) return FftSummary(null, null, doubleArrayOf(), doubleArrayOf())

        val duration = if (recordDurationSec > 0.0) recordDurationSec else 1.0
        val fs = if (signal.size >= 2) (signal.size - 1).toDouble() / duration else 0.0
        if (fs <= 0.0) return FftSummary(null, null, doubleArrayOf(), doubleArrayOf())

        val processed = preprocessForFft(signal)

        val fmin = min(fftFmin, fftFmax)
        val fmax = max(fftFmin, fftFmax)

        val firstPass = fftSpectrum(
            y = processed,
            fs = fs,
            fmin = fmin,
            fmax = fmax,
            zeroPadFactor = max(2, zeroPadFactor / 2)
        ) ?: return FftSummary(null, null, doubleArrayOf(), doubleArrayOf())

        val band = if (useAutoBand) {
            estimateAutoBand(
                freq = firstPass.freq,
                spec = firstPass.spec,
                searchMin = fmin,
                searchMax = fmax
            ) ?: Pair(fmin, fmax)
        } else {
            Pair(fmin, fmax)
        }

        val finalPass = fftSpectrum(
            y = processed,
            fs = fs,
            fmin = band.first,
            fmax = band.second,
            zeroPadFactor = zeroPadFactor
        ) ?: return FftSummary(null, null, doubleArrayOf(), doubleArrayOf())

        val selectedIndices = finalPass.freq.indices.filter {
            finalPass.freq[it] >= fmin && finalPass.freq[it] <= fmax
        }

        if (selectedIndices.isEmpty()) {
            return FftSummary(null, null, doubleArrayOf(), doubleArrayOf())
        }

        val outFreq = DoubleArray(selectedIndices.size)
        val outSpec = DoubleArray(selectedIndices.size)

        for (i in selectedIndices.indices) {
            val src = selectedIndices[i]
            outFreq[i] = finalPass.freq[src]
            outSpec[i] = finalPass.spec[src]
        }

        return FftSummary(
            dominantFreq = finalPass.dominantFreq,
            dominantAmp = finalPass.dominantAmp,
            freq = outFreq,
            spec = outSpec
        )
    }

    private fun preprocessForFft(signal: DoubleArray): DoubleArray {
        val median = median(signal)
        return DoubleArray(signal.size) { i -> signal[i] - median }
    }

    private fun median(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val copy = values.copyOf()
        copy.sort()
        val mid = copy.size / 2
        return if (copy.size % 2 == 1) {
            copy[mid]
        } else {
            (copy[mid - 1] + copy[mid]) / 2.0
        }
    }

    private data class SpectrumResult(
        val freq: DoubleArray,
        val spec: DoubleArray,
        val dominantFreq: Double?,
        val dominantAmp: Double?
    )

    private fun fftSpectrum(
        y: DoubleArray,
        fs: Double,
        fmin: Double,
        fmax: Double,
        zeroPadFactor: Int
    ): SpectrumResult? {
        val n = y.size
        if (n < 16 || fs <= 0.0) return null

        var nfft = 1
        val target = n * max(1, zeroPadFactor)
        while (nfft < target) nfft = nfft shl 1

        val real = DoubleArray(nfft)
        val imag = DoubleArray(nfft)

        var windowSum = 0.0
        for (i in 0 until n) {
            val w = 0.5 - 0.5 * cos(2.0 * Math.PI * i / max(1, n - 1))
            real[i] = y[i] * w
            windowSum += w
        }

        fftRadix2(real, imag)

        val bins = nfft / 2 + 1
        val freq = DoubleArray(bins)
        val spec = DoubleArray(bins)

        var bestIdx = -1
        var bestAmp = Double.NEGATIVE_INFINITY

        for (i in 0 until bins) {
            val f = i * fs / nfft
            val amp = if (windowSum > 0.0) {
                (2.0 / windowSum) * hypot(real[i], imag[i])
            } else {
                hypot(real[i], imag[i])
            }

            freq[i] = f
            spec[i] = if (i == 0) 0.0 else amp

            if (f >= fmin && f <= fmax && spec[i] > bestAmp) {
                bestAmp = spec[i]
                bestIdx = i
            }
        }

        val dom = if (bestIdx >= 0) {
            quadraticPeakInterpolation(freq, spec, bestIdx)
        } else {
            null
        }

        return SpectrumResult(
            freq = freq,
            spec = spec,
            dominantFreq = dom?.first,
            dominantAmp = dom?.second
        )
    }

    private fun fftRadix2(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be power of two" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit

            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr

                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)

            var i = 0
            while (i < n) {
                var wr = 1.0
                var wi = 0.0

                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]

                    val vR = real[i + k + len / 2] * wr - imag[i + k + len / 2] * wi
                    val vI = real[i + k + len / 2] * wi + imag[i + k + len / 2] * wr

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI

                    val nextWr = wr * wLenR - wi * wLenI
                    val nextWi = wr * wLenI + wi * wLenR
                    wr = nextWr
                    wi = nextWi
                }

                i += len
            }

            len = len shl 1
        }
    }

    private fun quadraticPeakInterpolation(
        freq: DoubleArray,
        spec: DoubleArray,
        idx: Int
    ): Pair<Double, Double> {
        if (idx <= 0 || idx >= spec.lastIndex) {
            return Pair(freq[idx], spec[idx])
        }

        val y1 = spec[idx - 1]
        val y2 = spec[idx]
        val y3 = spec[idx + 1]
        val denom = y1 - 2.0 * y2 + y3

        if (abs(denom) < 1e-15) {
            return Pair(freq[idx], spec[idx])
        }

        val delta = 0.5 * (y1 - y3) / denom
        val df = freq[1] - freq[0]
        val fPeak = freq[idx] + delta * df
        val aPeak = y2 - 0.25 * (y1 - y3) * delta

        return Pair(fPeak, aPeak)
    }

    private fun estimateAutoBand(
        freq: DoubleArray,
        spec: DoubleArray,
        searchMin: Double,
        searchMax: Double,
        minWidth: Double = 8.0,
        maxWidth: Double = 24.0,
        relHeight: Double = 0.35,
        marginHz: Double = 2.0
    ): Pair<Double, Double>? {
        val indices = freq.indices.filter { freq[it] >= searchMin && freq[it] <= searchMax }
        if (indices.size < 5) return null

        var peakIdx = indices.first()
        var peakAmp = spec[peakIdx]

        for (idx in indices) {
            if (spec[idx] > peakAmp) {
                peakAmp = spec[idx]
                peakIdx = idx
            }
        }

        if (peakAmp <= 0.0) return null

        val threshold = peakAmp * relHeight

        var left = peakIdx
        while (left > indices.first() && spec[left] >= threshold) left--

        var right = peakIdx
        while (right < indices.last() && spec[right] >= threshold) right++

        val peakFreq = freq[peakIdx]

        var low = max(searchMin, freq[left] - marginHz)
        var high = min(searchMax, freq[right] + marginHz)

        if (high - low < minWidth) {
            val half = minWidth / 2.0
            low = max(searchMin, peakFreq - half)
            high = min(searchMax, peakFreq + half)
        }

        if (high - low > maxWidth) {
            val half = maxWidth / 2.0
            low = max(searchMin, peakFreq - half)
            high = min(searchMax, peakFreq + half)
        }

        return if (low < high) Pair(low, high) else null
    }
}