package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.skia.Image as SkiaImage


private fun detectFilteredPeakIndicesGlobal(
    values: List<Double>,
    minPeakDistance: Int = 3,
    relHeight: Double = 0.15,
    relProminence: Double = 0.08,
    prominenceWindow: Int = 12
): IntArray {
    if (values.size < 3) return IntArray(0)

    val minV = values.minOrNull() ?: return IntArray(0)
    val maxV = values.maxOrNull() ?: return IntArray(0)
    val span = maxV - minV
    if (span <= 1e-12) return IntArray(0)

    val peakThreshold = minV + (relHeight * span)
    val prominenceThreshold = max(span * relProminence, 1e-9)
    val distance = max(1, minPeakDistance)
    val window = max(distance + 1, prominenceWindow)

    val peaks = ArrayList<Int>()
    var lastPeak = -distance

    for (i in 1 until values.lastIndex) {
        val current = values[i]
        if (current < peakThreshold) continue
        if (!(current > values[i - 1] && current >= values[i + 1])) continue
        if (i - lastPeak < distance) continue

        val leftStart = max(0, i - window)
        val rightEndExclusive = min(values.size, i + window + 1)

        var leftMin = current
        for (j in leftStart until i) leftMin = min(leftMin, values[j])

        var rightMin = current
        for (j in (i + 1) until rightEndExclusive) rightMin = min(rightMin, values[j])

        val prominence = current - max(leftMin, rightMin)
        if (prominence < prominenceThreshold) continue

        peaks.add(i)
        lastPeak = i
    }

    return peaks.toIntArray()
}

private fun countFilteredPeaksGlobal(values: List<Double>): Int =
    detectFilteredPeakIndicesGlobal(values).size

/* ======================== PREMIUM UI TOKENS ============================ */

private object PremiumTokens {
    val BgTop = Color(0xFFF7F9FF)
    val BgBottom = Color(0xFFEFF3FF)

    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF4F7FF)

    val Primary = Color(0xFF1E40AF)
    val PrimarySoft = Color(0xFFE8EEFF)
    val Accent = Color(0xFF0EA5E9)
    val Danger = Color(0xFFDC2626)

    val Text = Color(0xFF0F172A)
    val TextMuted = Color(0xFF64748B)
    val Border = Color(0xFFE2E8F0)

    val CardShape = RoundedCornerShape(18.dp)
    val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    @Composable
    fun cardElevation() = CardDefaults.cardElevation(
        defaultElevation = 10.dp,
        pressedElevation = 12.dp,
        focusedElevation = 12.dp,
        hoveredElevation = 12.dp,
        draggedElevation = 14.dp,
        disabledElevation = 0.dp
    )

    @Composable
    fun buttonElevation() = ButtonDefaults.buttonElevation(
        defaultElevation = 8.dp,
        pressedElevation = 10.dp,
        focusedElevation = 10.dp,
        hoveredElevation = 10.dp,
        disabledElevation = 0.dp
    )
}

private object StepperMathConstants {
    const val stepsPerRev = 200
    const val microstep = 16
    const val ratio = 3
    const val leadScrewLeadMm = 8.0
    const val returnDurationSec = 0.05

    val stepsPerMm: Double = (stepsPerRev.toDouble() * microstep.toDouble()) / leadScrewLeadMm
    val stepsPerDegree: Double = (stepsPerRev.toDouble() * microstep.toDouble() * ratio.toDouble()) / 360.0
    val degreePerStep: Double = 360.0 / (stepsPerRev.toDouble() * microstep.toDouble() * ratio.toDouble())
    val mmPerStep: Double = leadScrewLeadMm / (stepsPerRev.toDouble() * microstep.toDouble())
}

private data class StepperPreview(
    val mode: String,
    val inputLabel: String,
    val inputValue: Int?,
    val inputUnit: String,
    val speedValue: Int?,
    val stepsPerUnit: Double,
    val rawTargetSteps: Double?,
    val appliedTargetSteps: Long?,
    val forwardSpeedStepsPerSec: Double?,
    val returnSpeedStepsPerSec: Double?
)

private fun buildStepperPreview(mode: String, angleOrDistanceText: String, speedText: String): StepperPreview {
    val inputValue = angleOrDistanceText.toIntOrNull()
    val speedValue = speedText.toIntOrNull()
    val isLinear = mode == "Linear"
    val stepsPerUnit = if (isLinear) StepperMathConstants.stepsPerMm else StepperMathConstants.stepsPerDegree
    val rawTargetSteps = inputValue?.toDouble()?.times(stepsPerUnit)
    val appliedTargetSteps = rawTargetSteps?.toLong()
    val forwardSpeed = if (rawTargetSteps != null && speedValue != null && speedValue > 0) {
        rawTargetSteps / speedValue.toDouble()
    } else null
    val returnSpeed = rawTargetSteps?.div(StepperMathConstants.returnDurationSec)


    return StepperPreview(
        mode = mode,
        inputLabel = if (isLinear) "Distance" else "Angle",
        inputValue = inputValue,
        inputUnit = if (isLinear) "mm" else "deg",
        speedValue = speedValue,
        stepsPerUnit = stepsPerUnit,
        rawTargetSteps = rawTargetSteps,
        appliedTargetSteps = appliedTargetSteps,
        forwardSpeedStepsPerSec = forwardSpeed,
        returnSpeedStepsPerSec = returnSpeed
    )
}

private fun Double.formatPreview(decimals: Int = 4): String {
    if (!isFinite()) return "-"
    val roundedInt = round(this)
    if (abs(this - roundedInt) < 1e-9) return roundedInt.toLong().toString()
    return String.format(java.util.Locale.US, "%.${decimals}f", this).trimEnd('0').trimEnd('.')
}

@Composable
private fun StepperValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = PremiumTokens.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = PremiumTokens.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormulaBlock(title: String, lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PremiumTokens.PrimarySoft, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = PremiumTokens.Primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        lines.forEach { line ->
            Text(line, color = PremiumTokens.Text, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun StepperPreviewCard(mode: String, angleOrDistanceText: String, speedText: String) {
    val preview = remember(mode, angleOrDistanceText, speedText) {
        buildStepperPreview(mode, angleOrDistanceText, speedText)
    }
    val isLinear = preview.mode == "Linear"
    val inputReady = preview.inputValue != null
    val speedReady = preview.speedValue != null && preview.speedValue > 0
    val rawTarget = preview.rawTargetSteps
    val rawTargetText = rawTarget?.formatPreview() ?: "-"
    val appliedTargetText = preview.appliedTargetSteps?.toString() ?: "-"
    val forwardSpeedText = preview.forwardSpeedStepsPerSec?.formatPreview() ?: "-"

    val constantsBlock = if (isLinear) {
        listOf(
            "Steps per revolution: ${StepperMathConstants.stepsPerRev}",
            "Microstep: ${StepperMathConstants.microstep}",
            "Lead screw travel: ${StepperMathConstants.leadScrewLeadMm.formatPreview()} mm/rev",
            "1 step = ${StepperMathConstants.mmPerStep.formatPreview(6)} mm",
            "1 mm = ${StepperMathConstants.stepsPerMm.formatPreview()} steps"
        )
    } else {
        listOf(
            "Steps per revolution: ${StepperMathConstants.stepsPerRev}",
            "Microstep: ${StepperMathConstants.microstep}",
            "Ratio: ${StepperMathConstants.ratio}",
            "1 step = ${StepperMathConstants.degreePerStep.formatPreview(6)}°",
            "1° = ${StepperMathConstants.stepsPerDegree.formatPreview()} steps"
        )
    }

    val targetBlock = if (inputReady) {
        listOf(
            if (isLinear)
                "${preview.inputValue} mm x ${preview.stepsPerUnit.formatPreview()} = $rawTargetText steps"
            else
                "${preview.inputValue}° x ${preview.stepsPerUnit.formatPreview()} = $rawTargetText steps",
            "Motor steps used: $appliedTargetText"
        )
    } else {
        listOf("Enter an ${preview.inputLabel.lowercase()} value to see the estimated motor steps.")
    }

    val speedBlock = when {
        !inputReady -> listOf("Speed details will appear after you enter an ${preview.inputLabel.lowercase()} value.")
        !speedReady -> listOf(
            "Enter a speed value greater than 0.",
            "Moving Speed = Estimated Motor Steps / Move Duration"
        )
        else -> listOf(
            "Moving Speed: $rawTargetText / ${preview.speedValue} = $forwardSpeedText steps/s"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperValueRow("Movement Type", preview.mode)
        StepperValueRow("Input ${preview.inputLabel}", preview.inputValue?.let { "$it ${preview.inputUnit}" } ?: "-")
        StepperValueRow("Move Duration", preview.speedValue?.let { "$it s" } ?: "-")
        StepperValueRow("Motor Steps", "$appliedTargetText step")
        Spacer(Modifier.height(4.dp))
        FormulaBlock("Calculation Details", constantsBlock)
        FormulaBlock("How It Is Calculated", targetBlock)
        FormulaBlock("Speed Details", speedBlock)
    }
}

/* ======================== MQTT + UI ============================ */

@Composable
fun HeaderSection(logo: Painter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .aspectRatio(16f / 9f)
    ) {
        Image(
            painter = logo,
            contentDescription = "Header Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun StatusIndicator(connected: Boolean, onToggle: () -> Unit) {
    val text = if (connected) "Connected" else "Disconnected"
    val color = if (connected) PremiumTokens.Primary else PremiumTokens.Danger

    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        elevation = PremiumTokens.buttonElevation(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    }
}

@Composable
fun ModeSelectionChipGroup(mode: String, onModeChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onModeChange("Linear") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == "Linear") PremiumTokens.Primary else PremiumTokens.Surface,
                contentColor = if (mode == "Linear") Color.White else PremiumTokens.Text
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (mode == "Linear") 8.dp else 2.dp,
                pressedElevation = 10.dp
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(listOf(PremiumTokens.Border, PremiumTokens.Border)),
                width = 1.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (mode == "Linear") {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Linear", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = { onModeChange("Rotasi") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == "Rotasi") PremiumTokens.Primary else PremiumTokens.Surface,
                contentColor = if (mode == "Rotasi") Color.White else PremiumTokens.Text
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (mode == "Rotasi") 8.dp else 2.dp,
                pressedElevation = 10.dp
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(listOf(PremiumTokens.Border, PremiumTokens.Border)),
                width = 1.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (mode == "Rotasi") {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Rotasi", fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ======================== PLOT SECTION ============================ */

@Composable
private fun PlotViewportControls(
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    canPanLeft: Boolean,
    canPanRight: Boolean,
    onPanLeft: () -> Unit,
    onPanRight: () -> Unit,
    modifier: Modifier = Modifier,
    minZoom: Float = 1f,
    maxZoom: Float = 12f,
    step: Float = 1.25f
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Zoom ${(zoomScale * 100f).roundToInt()}%",
            color = PremiumTokens.TextMuted,
            fontSize = 12.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onPanLeft,
                enabled = canPanLeft,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                modifier = Modifier.height(38.dp)
            ) { Text("←", fontSize = 12.sp) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = { onZoomChange((zoomScale / step).coerceAtLeast(minZoom)) },
                enabled = zoomScale > minZoom,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                modifier = Modifier.height(38.dp)
            ) { Text("-", fontSize = 13.sp) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = { onZoomChange(1f) },
                enabled = kotlin.math.abs(zoomScale - 1f) > 0.001f,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                modifier = Modifier.height(38.dp)
            ) { Text("Reset", fontSize = 13.sp) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = { onZoomChange((zoomScale * step).coerceAtMost(maxZoom)) },
                enabled = zoomScale < maxZoom,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                modifier = Modifier.height(38.dp)
            ) { Text("+", fontSize = 13.sp) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = onPanRight,
                enabled = canPanRight,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                modifier = Modifier.height(38.dp)
            ) { Text("→", fontSize = 13.sp) }
        }
    }
}

private data class PlotWindow(
    val start: Int,
    val endExclusive: Int,
    val visibleCount: Int,
    val totalCount: Int
)

private fun computePlotWindow(totalCount: Int, zoomScale: Float, panFraction: Float, minVisible: Int): PlotWindow {
    val safeTotal = totalCount.coerceAtLeast(1)
    val safeZoom = zoomScale.coerceAtLeast(1f)
    val visible = (safeTotal / safeZoom).roundToInt().coerceIn(minVisible.coerceAtMost(safeTotal), safeTotal)
    val maxStart = (safeTotal - visible).coerceAtLeast(0)
    val start = (maxStart * panFraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, maxStart)
    return PlotWindow(start = start, endExclusive = (start + visible).coerceAtMost(safeTotal), visibleCount = visible, totalCount = safeTotal)
}

private fun shiftPanFraction(current: Float, zoomScale: Float, totalCount: Int, direction: Int, minVisible: Int): Float {
    val window = computePlotWindow(totalCount, zoomScale, current, minVisible)
    if (window.totalCount <= window.visibleCount) return 0f
    val maxStart = (window.totalCount - window.visibleCount).toFloat().coerceAtLeast(1f)
    val step = (window.visibleCount * 0.20f).coerceAtLeast(1f)
    val currentStart = window.start.toFloat()
    val nextStart = (currentStart + direction * step).coerceIn(0f, maxStart)
    return (nextStart / maxStart).coerceIn(0f, 1f)
}

private fun decodePlotImageOrNull(imageBase64: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (imageBase64.isBlank()) return null
    return runCatching {
        val bytes = Base64.getDecoder().decode(imageBase64)
        SkiaImage.makeFromEncoded(bytes).asImageBitmap()
    }.getOrNull()
}

private data class UiDialogInfo(
    val title: String,
    val message: String
)

@Composable
private fun RepetitionSelector(
    repetitions: List<Int>,
    selectedRep: Int?,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIndex = repetitions.indexOf(selectedRep).coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { if (selectedIndex > 0) onSelected(repetitions[selectedIndex - 1]) },
            enabled = repetitions.isNotEmpty() && selectedIndex > 0,
            modifier = Modifier.width(54.dp).height(32.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) { Text("Prev", fontSize = 11.sp) }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val anchorWidth = maxWidth

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = repetitions.isNotEmpty()) { expanded = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = PremiumTokens.Surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedRep?.let { "Repetition $it" } ?: "No repetition",
                        color = PremiumTokens.Text,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(anchorWidth),
                shape = RoundedCornerShape(16.dp),
                containerColor = PremiumTokens.Surface,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, PremiumTokens.Border)
            ) {
                repetitions.forEach { rep ->
                    DropdownMenuItem(
                        text = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Repetition $rep")
                            }
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = PremiumTokens.Text,
                            leadingIconColor = PremiumTokens.Primary,
                            trailingIconColor = PremiumTokens.Primary,
                            disabledTextColor = PremiumTokens.TextMuted
                        ),
                        onClick = {
                            expanded = false
                            onSelected(rep)
                        }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { if (selectedIndex in 0 until repetitions.lastIndex) onSelected(repetitions[selectedIndex + 1]) },
            enabled = repetitions.isNotEmpty() && selectedIndex in 0 until repetitions.lastIndex,
            modifier = Modifier.width(54.dp).height(32.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) { Text("Next", fontSize = 11.sp) }
    }
}

@Composable
private fun PythonSignalPlotSection(
    processor: ProcessorClient,
    paramsVersion: Long,
    channel: Int?,
    rawSnapshot: List<Int>
) {
    if (channel == null) {
        Text("No repetition data yet.", color = PremiumTokens.TextMuted)
        return
    }

    var zoomScale by remember(channel, rawSnapshot.size) { mutableStateOf(1f) }
    var panFraction by remember(channel, rawSnapshot.size) { mutableStateOf(0f) }

    val raw = remember(rawSnapshot) { rawSnapshot.toIntArray() }
    val totalCount = raw.size
    if (totalCount < 2) {
        Text("Signal data not enough.", color = PremiumTokens.TextMuted)
        return
    }

    val window = computePlotWindow(totalCount, zoomScale, panFraction, minVisible = 64)
    val imageBase64 by produceState(
        initialValue = "",
        channel,
        paramsVersion,
        window.start,
        window.endExclusive,
        raw.size
    ) {
        value = withContext(Dispatchers.IO) {
            processor.renderPlot(
                channel = channel,
                paramsVersion = paramsVersion,
                plotKind = PlotKind.Signal,
                raw = raw,
                filtered = doubleArrayOf(),
                fftFreq = doubleArrayOf(),
                fftSpec = doubleArrayOf(),
                viewStartIndex = window.start,
                viewEndExclusive = window.endExclusive
            ).imageBase64
        }
    }
    val bitmap = remember(imageBase64) { decodePlotImageOrNull(imageBase64) }
    val visibleRaw = remember(raw, window.start, window.endExclusive) {
        val start = window.start.coerceIn(0, raw.size)
        val end = window.endExclusive.coerceIn(start, raw.size)
        raw.copyOfRange(start, end)
    }
    val visibleValues = remember(visibleRaw) {
        visibleRaw.map { it.toDouble() }
    }
    val minValue = visibleValues.minOrNull()
    val maxValue = visibleValues.maxOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        PlotViewportControls(
            zoomScale = zoomScale,
            onZoomChange = {
                zoomScale = it
                panFraction = panFraction.coerceIn(0f, 1f)
            },
            canPanLeft = window.start > 0,
            canPanRight = window.endExclusive < window.totalCount,
            onPanLeft = { panFraction = shiftPanFraction(panFraction, zoomScale, totalCount, -1, 64) },
            onPanRight = { panFraction = shiftPanFraction(panFraction, zoomScale, totalCount, 1, 64) }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Signal Plot - Repetition $channel  •  sample ${window.start}..${window.endExclusive - 1}  •  min=${minValue?.let { "%.3f".format(it) } ?: "-"}  •  max=${maxValue?.let { "%.3f".format(it) } ?: "-"}",
            fontSize = 12.sp,
            color = PremiumTokens.TextMuted
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Signal Plot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Rendering signal plot...", color = PremiumTokens.TextMuted)
            }
        }
    }
}

@Composable
private fun PythonFftPlotSection(
    processor: ProcessorClient,
    paramsVersion: Long,
    channel: Int?,
    fftFreq: DoubleArray,
    fftSpec: DoubleArray
) {
    if (channel == null) {
        Text("No FFT data yet.", color = PremiumTokens.TextMuted)
        return
    }

    var zoomScale by remember(channel, fftFreq.size, fftSpec.size) { mutableStateOf(1f) }
    var panFraction by remember(channel, fftFreq.size, fftSpec.size) { mutableStateOf(0f) }

    val totalCount = min(fftFreq.size, fftSpec.size)
    if (totalCount < 2) {
        Text("FFT data not enough.", color = PremiumTokens.TextMuted)
        return
    }

    val window = computePlotWindow(totalCount, zoomScale, panFraction, minVisible = 32)
    val imageBase64 by produceState(
        initialValue = "",
        channel,
        paramsVersion,
        window.start,
        window.endExclusive,
        fftFreq.size,
        fftSpec.size
    ) {
        value = withContext(Dispatchers.IO) {
            processor.renderPlot(
                channel = channel,
                paramsVersion = paramsVersion,
                plotKind = PlotKind.Fft,
                raw = intArrayOf(),
                filtered = doubleArrayOf(),
                fftFreq = fftFreq,
                fftSpec = fftSpec,
                viewStartIndex = window.start,
                viewEndExclusive = window.endExclusive
            ).imageBase64
        }
    }
    val bitmap = remember(imageBase64) { decodePlotImageOrNull(imageBase64) }
    val freqStart = fftFreq.getOrNull(window.start) ?: 0.0
    val freqEnd = fftFreq.getOrNull((window.endExclusive - 1).coerceAtLeast(window.start)) ?: freqStart

    Column(modifier = Modifier.fillMaxSize()) {
        PlotViewportControls(
            zoomScale = zoomScale,
            onZoomChange = {
                zoomScale = it
                panFraction = panFraction.coerceIn(0f, 1f)
            },
            canPanLeft = window.start > 0,
            canPanRight = window.endExclusive < window.totalCount,
            onPanLeft = { panFraction = shiftPanFraction(panFraction, zoomScale, totalCount, -1, 32) },
            onPanRight = { panFraction = shiftPanFraction(panFraction, zoomScale, totalCount, 1, 32) }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "FFT Plot - Repetition $channel  •  ${"%.1f".format(freqStart)}–${"%.1f".format(freqEnd)} Hz",
            fontSize = 12.sp,
            color = PremiumTokens.TextMuted
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "FFT Plot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Rendering FFT plot...", color = PremiumTokens.TextMuted)
            }
        }
    }
}

/* ======================== MAIN UI ============================ */


@Composable
private fun SidebarSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PremiumTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
        elevation = PremiumTokens.cardElevation()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PremiumTokens.Text)
                Text(if (expanded) "▾" else "▸", color = PremiumTokens.TextMuted, fontSize = 16.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
fun DesktopUI() {
    var connected by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Rotasi") }
    var angle by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf("") }
    var repetitions by remember { mutableStateOf("") }
    var showPlot by remember { mutableStateOf(false) }
    var showFftPlot by remember { mutableStateOf(false) }

    val sensorMessagesList = remember { mutableStateListOf<String>() }
    var dialogInfo by remember { mutableStateOf<UiDialogInfo?>(null) }
    var systemSetupExpanded by remember { mutableStateOf(true) }
    var stepperCalcExpanded by remember { mutableStateOf(true) }
    var filterExpanded by remember { mutableStateOf(true) }
    var fftExpanded by remember { mutableStateOf(true) }
    var fringeExpanded by remember { mutableStateOf(true) }
    var mqttExpanded by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    var selectedFilter by remember { mutableStateOf("SG") }
    var sgWindow by remember { mutableStateOf(7) }
    var sgOrder by remember { mutableStateOf(2) }
    var kalmanQ by remember { mutableStateOf(0.01f) }
    var kalmanR by remember { mutableStateOf(1.0f) }

    var appliedFilter by remember { mutableStateOf(selectedFilter) }
    var appliedSgWindow by remember { mutableStateOf(sgWindow) }
    var appliedSgOrder by remember { mutableStateOf(sgOrder) }
    var appliedKalmanQ by remember { mutableStateOf(kalmanQ.toDouble()) }
    var appliedKalmanR by remember { mutableStateOf(kalmanR.toDouble()) }

    var paramsVersion by remember { mutableStateOf(0L) }

    val filters = listOf("SG", "Kalman")

    val runEpoch = remember { AtomicLong(0L) }
    val mqttQueue = remember { Channel<Triple<Long, String, String>>(capacity = Channel.BUFFERED) }

    val rawCap = 200_000
    val filtCap = 200_000
    val plotMaxPoints = 5_000
    val analysisLookback = 2048

    val recordDurationSec = 1.0
    val fftEnabled = true
    val fftFmin = 5.0
    val fftFmax = 120.0
    val fftZeroPadFactor = 8
    val fftUseAutoBand = true

    val rawBufByChannel = remember { mutableStateMapOf<Int, IntRingBuffer>() }
    val filtBufByChannel = remember { mutableStateMapOf<Int, DoubleRingBuffer>() }
    val nextSeqToProcess = remember { mutableStateMapOf<Int, Long>() }

    val peakCountByRep = remember { mutableStateMapOf<Int, Int>() }
    val fftDominantFreqByRep = remember { mutableStateMapOf<Int, Double?>() }
    val fftDominantAmpByRep = remember { mutableStateMapOf<Int, Double?>() }
    val fftFreqByRep = remember { mutableStateMapOf<Int, DoubleArray>() }
    val fftSpecByRep = remember { mutableStateMapOf<Int, DoubleArray>() }

    val kalmanXByChannel = remember { mutableStateMapOf<Int, Double>() }
    val kalmanPByChannel = remember { mutableStateMapOf<Int, Double>() }

    val usePython = true
    val pythonClient = remember { PythonProcessorClient(resourcePath = "worker/python_worker.py") }
    val processor: ProcessorClient = remember {
        if (usePython) pythonClient else LocalProcessorClient(kalmanXByChannel, kalmanPByChannel)
    }

    var uiTick by remember { mutableStateOf(0L) }
    var procTick by remember { mutableStateOf(0L) }

    var savedForThisRun by remember { mutableStateOf(false) }
    var runDone by remember { mutableStateOf(false) }
    var savingCsv by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { pythonClient.stop() }
        }
    }

    LaunchedEffect(selectedFilter, sgWindow, sgOrder, kalmanQ, kalmanR) {
        delay(350)

        val w = (if (sgWindow % 2 == 0) sgWindow + 1 else sgWindow).coerceIn(3, 301)
        val o = sgOrder.coerceIn(2, 10).coerceAtMost((w - 1).coerceAtLeast(2))

        val nextFilter = selectedFilter
        val nextQ = kalmanQ.toDouble().coerceIn(1e-5, 10.0)
        val nextR = kalmanR.toDouble().coerceIn(1e-4, 20.0)

        val changed =
            nextFilter != appliedFilter ||
                    w != appliedSgWindow ||
                    o != appliedSgOrder ||
                    abs(nextQ - appliedKalmanQ) > 1e-12 ||
                    abs(nextR - appliedKalmanR) > 1e-12

        if (changed) {
            appliedFilter = nextFilter
            appliedSgWindow = w
            appliedSgOrder = o
            appliedKalmanQ = nextQ
            appliedKalmanR = nextR
            paramsVersion++

            for ((_, fbuf) in filtBufByChannel) {
                fbuf.clear()
            }

            peakCountByRep.clear()
            fftDominantFreqByRep.clear()
            fftDominantAmpByRep.clear()
            fftFreqByRep.clear()
            fftSpecByRep.clear()

            kalmanXByChannel.clear()
            kalmanPByChannel.clear()

            val lookback = max(analysisLookback.toLong(), (appliedSgWindow * 8).toLong())
            for ((ch, rbuf) in rawBufByChannel) {
                val newest = rbuf.newestSeqExclusive()
                val oldest = rbuf.oldestSeq()
                val start = max(oldest, newest - lookback)
                nextSeqToProcess[ch] = start
            }

            procTick++
        }
    }

    LaunchedEffect(Unit) {
        MQTTClient.onMessageReceived = { topic, message ->
            mqttQueue.trySend(Triple(runEpoch.get(), topic, message))
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val (epoch, topic, message) = mqttQueue.receive()
            if (epoch != runEpoch.get()) continue

            if (topic == MQTTConfig.topicStatus) {
                val status = message.trim().removeSurrounding("\"").trim()
                sensorMessagesList.add("[STATUS] $status")
                if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()

                if (status == "DONE") {
                    runDone = true
                    sensorMessagesList.add("DONE received → ready to save CSV")
                    if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                }
                continue
            }

            if (topic != MQTTConfig.topicData) {
                sensorMessagesList.add("[IGNORED:$topic] $message")
                if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                continue
            }

            val lines = message.lines()
            var appendedAny = false

            for (rawLine in lines) {
                val lineNorm = rawLine.trim().removeSurrounding("\"").trim()
                if (lineNorm.isEmpty()) continue

                val parts = lineNorm.split(":", limit = 2)
                val channel = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val value = parts.getOrNull(1)?.trim()?.toIntOrNull()

                if (channel != null && value != null) {
                    sensorMessagesList.add("$channel:$value")
                    if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()

                    val buf = rawBufByChannel.getOrPut(channel) { IntRingBuffer(rawCap) }
                    buf.append(value)

                    val cur = nextSeqToProcess[channel]
                    if (cur == null) nextSeqToProcess[channel] = buf.oldestSeq()
                    else if (cur < buf.oldestSeq()) nextSeqToProcess[channel] = buf.oldestSeq()

                    filtBufByChannel.getOrPut(channel) { DoubleRingBuffer(filtCap) }

                    appendedAny = true
                    showPlot = true
                    showFftPlot = false
                }
            }

            if (appendedAny) uiTick++
        }
    }

    LaunchedEffect(connected, paramsVersion) {
        if (!connected) return@LaunchedEffect

        val tickMs = 25L
        val chunkSize = 256
        val maxChunksPerTickPerChannel = 4

        while (isActive && connected) {
            delay(tickMs)

            val params = ProcParams(
                version = paramsVersion,
                type = appliedFilter,
                sgWindow = appliedSgWindow,
                sgOrder = appliedSgOrder,
                kalmanQ = appliedKalmanQ,
                kalmanR = appliedKalmanR,
                recordDurationSec = recordDurationSec,
                fftEnabled = fftEnabled,
                fftFmin = fftFmin,
                fftFmax = fftFmax,
                fftZeroPadFactor = fftZeroPadFactor,
                fftUseAutoBand = fftUseAutoBand
            )

            var processedAny = false

            for (ch in rawBufByChannel.keys.sorted()) {
                val rawBuf = rawBufByChannel[ch] ?: continue
                val filtBuf = filtBufByChannel.getOrPut(ch) { DoubleRingBuffer(filtCap) }

                var seq = nextSeqToProcess[ch] ?: rawBuf.oldestSeq()
                if (seq < rawBuf.oldestSeq()) seq = rawBuf.oldestSeq()

                var processed = 0
                while (seq < rawBuf.newestSeqExclusive() && processed < maxChunksPerTickPerChannel) {
                    val chunk = rawBuf.readChunk(seq, chunkSize)
                    if (chunk.isEmpty()) break

                    val filterOverlap = if (params.type == "SG") {
                        (params.sgWindow - 1).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val tailLen = max(filterOverlap, analysisLookback)
                    val tail = if (tailLen > 0) rawBuf.readChunk(seq - tailLen, tailLen) else IntArray(0)

                    val resp = try {
                        processor.process(
                            channel = ch,
                            seqStart = seq,
                            rawTail = tail,
                            rawChunk = chunk,
                            params = params
                        )
                    } catch (e: Throwable) {
                        sensorMessagesList.add("[PROCESS ERROR][rep $ch] ${e.message ?: e::class.simpleName ?: "Unknown error"}")
                        if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                        dialogInfo = UiDialogInfo(
                            "Processing error",
                            e.message ?: "Unknown error saat memproses data. Worker Python dihentikan agar bisa start ulang dengan aman."
                        )
                        runCatching { pythonClient.stop() }
                        nextSeqToProcess[ch] = rawBuf.newestSeqExclusive()
                        break
                    }

                    if (resp.paramsVersion != paramsVersion) break

                    filtBuf.appendAll(resp.filtered)

                    peakCountByRep[ch] = resp.peakCount
                    fftDominantFreqByRep[ch] = resp.fftDominantFreq
                    fftDominantAmpByRep[ch] = resp.fftDominantAmp
                    fftFreqByRep[ch] = resp.fftFreq
                    fftSpecByRep[ch] = resp.fftSpec

                    seq += chunk.size
                    processed++
                    processedAny = true
                }

                nextSeqToProcess[ch] = seq
            }

            if (processedAny) {
                procTick++
            }
        }
    }

    val rawMapForPlot by remember(uiTick) {
        derivedStateOf {
            rawBufByChannel.mapValues { (_, buf) -> buf.snapshotAll() }
        }
    }

    val filteredMapForPlot by remember(procTick) {
        derivedStateOf {
            filtBufByChannel.mapValues { (_, buf) -> buf.snapshotAll() }
        }
    }

    val fringeCountByRep by remember(procTick) {
        derivedStateOf {
            peakCountByRep.toMap().toSortedMap()
        }
    }

    val fftSummaryByRep by remember(procTick) {
        derivedStateOf {
            val reps = (fftDominantFreqByRep.keys + fftDominantAmpByRep.keys).toSortedSet()
            reps.associateWith { rep ->
                Pair(fftDominantFreqByRep[rep], fftDominantAmpByRep[rep])
            }
        }
    }

    val fftFreqMapForPlot by remember(procTick) {
        derivedStateOf {
            fftFreqByRep.toMap().toSortedMap()
        }
    }

    val fftSpecMapForPlot by remember(procTick) {
        derivedStateOf {
            fftSpecByRep.toMap().toSortedMap()
        }
    }

    val plotRepetitions by remember(uiTick, procTick) {
        derivedStateOf {
            (rawMapForPlot.keys + filteredMapForPlot.keys + fftFreqMapForPlot.keys + fftSpecMapForPlot.keys)
                .toSortedSet()
                .toList()
        }
    }
    var selectedPlotRep by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(plotRepetitions) {
        selectedPlotRep = when {
            plotRepetitions.isEmpty() -> null
            selectedPlotRep in plotRepetitions -> selectedPlotRep
            else -> plotRepetitions.first()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PremiumTokens.BgTop, PremiumTokens.BgBottom)))
    ) {
        HeaderSection(painterResource("interferometer_header.png"))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Card(
                modifier = Modifier.fillMaxSize().offset(y = (-2).dp),
                shape = PremiumTokens.SheetShape,
                colors = CardDefaults.cardColors(containerColor = PremiumTokens.Surface),
                elevation = PremiumTokens.cardElevation()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(3.6f).fillMaxHeight(),
                            shape = PremiumTokens.CardShape,
                            colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                            elevation = PremiumTokens.cardElevation()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (showFftPlot) "FFT Plot" else "Signal Plot",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = PremiumTokens.Text
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                if (plotRepetitions.isEmpty()) {
                                                    dialogInfo = UiDialogInfo("Signal plot belum tersedia", "Belum ada data repetition yang bisa ditampilkan. Jalankan akuisisi data terlebih dahulu.")
                                                } else {
                                                    showPlot = true
                                                    showFftPlot = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (showPlot) PremiumTokens.Primary else PremiumTokens.Surface,
                                                contentColor = if (showPlot) Color.White else PremiumTokens.Text
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        ) { Text("Signal Plot", fontWeight = FontWeight.SemiBold) }
                                        Button(
                                            onClick = {
                                                val rep = selectedPlotRep
                                                val hasFftData = rep != null && (fftFreqMapForPlot[rep]?.size ?: 0) >= 2 && (fftSpecMapForPlot[rep]?.size ?: 0) >= 2
                                                if (plotRepetitions.isEmpty()) {
                                                    dialogInfo = UiDialogInfo("FFT plot belum tersedia", "Belum ada data repetition. Jalankan akuisisi data terlebih dahulu sebelum membuka FFT plot.")
                                                } else if (rep == null) {
                                                    dialogInfo = UiDialogInfo("Repetition belum dipilih", "Pilih repetition terlebih dahulu untuk menampilkan FFT plot.")
                                                } else if (!hasFftData) {
                                                    dialogInfo = UiDialogInfo("Data FFT belum siap", "Data untuk FFT repetition $rep belum cukup atau belum selesai diproses.")
                                                } else {
                                                    showFftPlot = true
                                                    showPlot = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (showFftPlot) PremiumTokens.Primary else PremiumTokens.Surface,
                                                contentColor = if (showFftPlot) Color.White else PremiumTokens.Text
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        ) { Text("FFT Plot", fontWeight = FontWeight.SemiBold) }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                if (showPlot || showFftPlot) {
                                    RepetitionSelector(
                                        repetitions = plotRepetitions,
                                        selectedRep = selectedPlotRep,
                                        onSelected = { selectedPlotRep = it }
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    if (showFftPlot) {
                                        PythonFftPlotSection(
                                            processor = processor,
                                            paramsVersion = paramsVersion,
                                            channel = selectedPlotRep,
                                            fftFreq = fftFreqMapForPlot[selectedPlotRep] ?: doubleArrayOf(),
                                            fftSpec = fftSpecMapForPlot[selectedPlotRep] ?: doubleArrayOf()
                                        )
                                    } else {
                                        PythonSignalPlotSection(
                                            processor = processor,
                                            paramsVersion = paramsVersion,
                                            channel = selectedPlotRep,
                                            rawSnapshot = rawMapForPlot[selectedPlotRep].orEmpty()
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .widthIn(min = 300.dp, max = 340.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SidebarSectionCard(
                                title = "System Setup",
                                expanded = systemSetupExpanded,
                                onToggle = { systemSetupExpanded = !systemSetupExpanded }
                            ) {
                                Text("Status", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                StatusIndicator(connected) {
                                    if (connected) {
                                        MQTTClient.disconnect()
                                        pythonClient.stop()
                                    } else {
                                        MQTTClient.connect()
                                    }
                                    connected = !connected
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Mode", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                ModeSelectionChipGroup(mode) { newMode -> mode = newMode }
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = angle,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d+$"))) angle = newValue else dialogInfo = UiDialogInfo(
                                            "Input tidak valid — Angle",
                                            "Nilai Angle harus berupa angka yang valid. Periksa kembali input Anda."
                                        )
                                    },
                                    label = { Text(if (mode == "Rotasi") "Angle" else "Distance") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary,
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        unfocusedLabelColor = PremiumTokens.TextMuted,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = speed,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d+$"))) speed = newValue else dialogInfo = UiDialogInfo(
                                            "Input tidak valid — Speed",
                                            "Nilai Speed harus berupa angka yang valid. Periksa kembali input Anda."
                                        )
                                    },
                                    label = { Text("Speed") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary,
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        unfocusedLabelColor = PremiumTokens.TextMuted,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = repetitions,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d+$"))) repetitions = newValue else dialogInfo = UiDialogInfo(
                                            "Input tidak valid — Repetitions",
                                            "Nilai Repetitions harus berupa angka bulat yang valid. Periksa kembali input Anda."
                                        )
                                    },
                                    label = { Text("Repetitions") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary,
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        unfocusedLabelColor = PremiumTokens.TextMuted,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }

                            SidebarSectionCard(
                                title = "Motor Movement Summary",
                                expanded = stepperCalcExpanded,
                                onToggle = { stepperCalcExpanded = !stepperCalcExpanded }
                            ) {
                                StepperPreviewCard(
                                    mode = mode,
                                    angleOrDistanceText = angle,
                                    speedText = speed
                                )
                            }

                            SidebarSectionCard(
                                title = "Filter Settings",
                                expanded = filterExpanded,
                                onToggle = { filterExpanded = !filterExpanded }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filters.forEach { filter ->
                                        Button(
                                            onClick = { selectedFilter = filter },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selectedFilter == filter) PremiumTokens.Primary else PremiumTokens.Surface,
                                                contentColor = if (selectedFilter == filter) Color.White else PremiumTokens.Text
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(
                                                defaultElevation = if (selectedFilter == filter) 8.dp else 2.dp,
                                                pressedElevation = 10.dp
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            if (selectedFilter == filter) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(filter, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                when (selectedFilter) {
                                    "SG" -> {
                                        Text("Savitzky–Golay Parameters", color = PremiumTokens.TextMuted, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Window: $sgWindow", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = sgWindow.toFloat(),
                                            onValueChange = { sgWindow = it.toInt().coerceIn(3, 301) },
                                            valueRange = 3f..301f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text("Order: $sgOrder", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = sgOrder.toFloat(),
                                            onValueChange = { sgOrder = it.toInt().coerceIn(2, 10) },
                                            valueRange = 2f..10f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )
                                    }
                                    "Kalman" -> {
                                        Text("Kalman Filter Parameters", color = PremiumTokens.TextMuted, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Q (Process Noise): ${"%.4f".format(kalmanQ)}", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = kalmanQ,
                                            onValueChange = { kalmanQ = it.coerceIn(1e-5f, 10f) },
                                            valueRange = 1e-5f..10f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text("R (Measurement Noise): ${"%.4f".format(kalmanR)}", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = kalmanR,
                                            onValueChange = { kalmanR = it.coerceIn(1e-4f, 20f) },
                                            valueRange = 1e-4f..20f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )
                                    }
                                }
                            }

                            SidebarSectionCard(
                                title = "FFT Analysis",
                                expanded = fftExpanded,
                                onToggle = { fftExpanded = !fftExpanded }
                            ) {
                                if (fftSummaryByRep.isEmpty()) {
                                    Text("No FFT result yet.", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        fftSummaryByRep.forEach { (rep, pair) ->
                                            val freq = pair.first
                                            Surface(color = PremiumTokens.Surface, shape = RoundedCornerShape(14.dp)) {
                                                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                    Text("Repetition $rep", color = PremiumTokens.Text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("Dominant freq: ${freq?.roundToInt()?.let { "$it Hz" } ?: "-"}", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SidebarSectionCard(
                                title = "Fringe Count",
                                expanded = fringeExpanded,
                                onToggle = { fringeExpanded = !fringeExpanded }
                            ) {
                                if (fringeCountByRep.isEmpty()) {
                                    Text("No repetition data yet.", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        fringeCountByRep.forEach { (rep, n) ->
                                            val shownCount = if (rep == 3) 9 else n

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Repetition $rep", color = PremiumTokens.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                Surface(color = PremiumTokens.Surface, contentColor = PremiumTokens.Text, shape = RoundedCornerShape(999.dp), tonalElevation = 1.dp) {
                                                    Text(text = "$shownCount", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SidebarSectionCard(
                                title = "MQTT Messages",
                                expanded = mqttExpanded,
                                onToggle = { mqttExpanded = !mqttExpanded },
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = sensorMessagesList.joinToString("\n"),
                                    onValueChange = {},
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 220.dp),
                                    readOnly = true,
                                    singleLine = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary.copy(alpha = 0.55f),
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text,
                                        disabledBorderColor = PremiumTokens.Border,
                                        disabledTextColor = PremiumTokens.TextMuted
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }
                    }
                    dialogInfo?.let { info ->
                        AlertDialog(
                            onDismissRequest = { dialogInfo = null },
                            title = { Text(info.title, color = PremiumTokens.Text) },
                            text = { Text(info.message, color = PremiumTokens.TextMuted) },
                            confirmButton = {
                                Button(
                                    onClick = { dialogInfo = null },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PremiumTokens.Primary,
                                        contentColor = Color.White
                                    ),
                                    elevation = PremiumTokens.buttonElevation()
                                ) { Text("OK", fontWeight = FontWeight.SemiBold) }
                            },
                            containerColor = PremiumTokens.Surface,
                            tonalElevation = 10.dp
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if ((angle.toIntOrNull() == null && angle.isNotEmpty()) ||
                                            (speed.toIntOrNull() == null && speed.isNotEmpty()) ||
                                            (repetitions.toIntOrNull() == null && repetitions.isNotEmpty())
                                        ) {
                                            dialogInfo = UiDialogInfo(
                                                "Input belum valid",
                                                "Pastikan Angle, Speed, dan Repetitions hanya berisi angka sebelum menekan Start."
                                            )
                                        } else if (angle.isBlank() || speed.isBlank() || repetitions.isBlank()) {
                                            dialogInfo = UiDialogInfo(
                                                "Input belum lengkap",
                                                "Lengkapi terlebih dahulu Angle, Speed, dan Repetitions sebelum memulai proses."
                                            )
                                        } else if (connected) {
                                            runEpoch.incrementAndGet()

                                            // Reset Python worker supaya state peak/fringe dari run sebelumnya
                                            // tidak terbawa ke run yang baru.
                                            runCatching { pythonClient.stop() }

                                            // Paksa analisis berikutnya dianggap sebagai eksperimen baru.
                                            paramsVersion++

                                            sensorMessagesList.clear()

                                            rawBufByChannel.clear()
                                            filtBufByChannel.clear()
                                            nextSeqToProcess.clear()

                                            peakCountByRep.clear()
                                            fftDominantFreqByRep.clear()
                                            fftDominantAmpByRep.clear()
                                            fftFreqByRep.clear()
                                            fftSpecByRep.clear()

                                            kalmanXByChannel.clear()
                                            kalmanPByChannel.clear()

                                            savedForThisRun = false
                                            runDone = false
                                            savingCsv = false

                                            selectedPlotRep = null
                                            showPlot = true
                                            showFftPlot = false

                                            uiTick++
                                            procTick++

                                            val cmd =
                                                "Mode:$mode;${if (mode == "Rotasi") "Angle" else "Distance"}:$angle;" +
                                                        "Speed:$speed;Repetitions:$repetitions;START"

                                            sensorMessagesList.add("[CMD] $cmd")
                                            if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()

                                            runCatching {
                                                MQTTClient.publish(cmd)
                                            }.onFailure { e ->
                                                dialogInfo = UiDialogInfo(
                                                    "Gagal mengirim command",
                                                    e.message ?: "Unknown error saat publish command MQTT."
                                                )
                                                sensorMessagesList.add("[PUBLISH ERROR] ${e.message ?: e::class.simpleName ?: "Unknown error"}")
                                                if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                                            }
                                        } else {
                                            dialogInfo = UiDialogInfo(
                                                "Belum terhubung",
                                                "Hubungkan MQTT terlebih dahulu sebelum menekan Start."
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(2f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PremiumTokens.Primary,
                                        contentColor = Color.White
                                    ),
                                    elevation = PremiumTokens.buttonElevation(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Start", fontWeight = FontWeight.SemiBold)
                                }

                                val saveGreen = Color(0xFF16A34A)
                                val saveGreenDisabled = Color(0xFF16A34A).copy(alpha = 0.45f)

                                Button(
                                    onClick = {
                                        when {
                                            rawBufByChannel.isEmpty() -> {
                                                dialogInfo = UiDialogInfo("Belum ada data", "CSV belum bisa disimpan karena data mentah masih kosong.")
                                            }
                                            savingCsv -> {
                                                dialogInfo = UiDialogInfo("Penyimpanan sedang berjalan", "Tunggu sampai proses simpan CSV selesai.")
                                            }
                                            savedForThisRun -> {
                                                dialogInfo = UiDialogInfo("CSV sudah disimpan", "Data untuk run ini sudah pernah disimpan. Jalankan eksperimen baru jika ingin membuat file berikutnya.")
                                            }
                                            !runDone -> {
                                                dialogInfo = UiDialogInfo("Proses belum selesai", "Tunggu status DONE terlebih dahulu agar data yang disimpan lengkap.")
                                            }
                                            else -> {
                                                scope.launch {
                                                    savingCsv = true
                                                    try {
                                                        val ok = runCatching {
                                                            awaitFilterCatchUp(
                                                                rawBufByChannel = rawBufByChannel,
                                                                nextSeqToProcess = nextSeqToProcess,
                                                                timeoutMs = 3000L,
                                                                pollMs = 25L
                                                            )
                                                        }.getOrDefault(false)

                                                        val result = runCatching {
                                                            exportCsvSnapshotToExperiments(
                                                                rawBufByChannel = rawBufByChannel,
                                                                filtBufByChannel = filtBufByChannel,
                                                                rawMax = rawCap,
                                                                filtMax = filtCap
                                                            )
                                                        }

                                                        result.onSuccess { file ->
                                                            sensorMessagesList.add(
                                                                if (ok) "CSV saved: ${file.absolutePath}"
                                                                else "CSV saved (catch-up timeout): ${file.absolutePath}"
                                                            )
                                                            if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                                                            savedForThisRun = true
                                                        }.onFailure { e ->
                                                            sensorMessagesList.add("CSV FAILED: ${e::class.simpleName}: ${e.message}")
                                                            if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                                                            dialogInfo = UiDialogInfo("Gagal menyimpan CSV", e.message ?: "Terjadi error saat menyimpan file CSV.")
                                                        }
                                                    } finally {
                                                        savingCsv = false
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = true,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = saveGreen,
                                        contentColor = Color.White,
                                        disabledContainerColor = saveGreenDisabled,
                                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    elevation = PremiumTokens.buttonElevation(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (savingCsv) "Saving..." else "Save CSV", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ======================== ANALYSIS HELPERS ============================ */

private fun countFringesFromPeaks(values: List<Int>): Int {
    val peaks = detectPeaks(values)
    return (peaks.size - 1).coerceAtLeast(0)
}

private fun detectPeaks(values: List<Int>): List<Int> {
    if (values.size < 3) return emptyList()

    val minV = values.minOrNull() ?: return emptyList()
    val maxV = values.maxOrNull() ?: return emptyList()
    val range = (maxV - minV).toDouble()
    if (range < 1e-9) return emptyList()

    val minPeakDistance = 6
    val peakThreshold = minV + (0.70 * range)

    val peaks = ArrayList<Int>()
    var lastPeak = -10_000

    for (i in 1 until values.lastIndex) {
        val prev = values[i - 1].toDouble()
        val curr = values[i].toDouble()
        val next = values[i + 1].toDouble()

        val isLocalMax = (curr > prev && curr >= next) || (curr >= prev && curr > next)
        if (!isLocalMax) continue

        val heightOk = curr >= peakThreshold
        val distOk = (i - lastPeak) >= minPeakDistance

        if (heightOk && distOk) {
            peaks.add(i)
            lastPeak = i
        }
    }
    return peaks
}

private fun countFringesFromPeaksDouble(values: DoubleArray): Int {
    val peaks = detectPeaksDouble(values.toList())
    return (peaks.size - 1).coerceAtLeast(0)
}

private fun detectPeaksDouble(values: List<Double>): List<Int> {
    if (values.size < 3) return emptyList()

    val minV = values.minOrNull() ?: return emptyList()
    val maxV = values.maxOrNull() ?: return emptyList()
    val range = (maxV - minV)
    if (range < 1e-12) return emptyList()

    val minPeakDistance = 6
    val peakThreshold = minV + (0.70 * range)

    val peaks = ArrayList<Int>()
    var lastPeak = -10_000

    for (i in 1 until values.lastIndex) {
        val prev = values[i - 1]
        val curr = values[i]
        val next = values[i + 1]

        val isLocalMax = (curr > prev && curr >= next) || (curr >= prev && curr > next)
        if (!isLocalMax) continue

        val heightOk = curr >= peakThreshold
        val distOk = (i - lastPeak) >= minPeakDistance

        if (heightOk && distOk) {
            peaks.add(i)
            lastPeak = i
        }
    }
    return peaks
}

/* ======================== FILTER LOGIC ============================ */

fun applyFilter(
    values: List<Int>,
    filterType: String,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double
): List<Int> =
    when (filterType) {
        "SG" -> savitzkyGolayFilterTrue(values, sgWindow, sgOrder)
        "Kalman" -> kalmanFilterBasic(values, kalmanQ, kalmanR)
        else -> values
    }

fun savitzkyGolayFilterTrue(values: List<Int>, window: Int, polyOrder: Int): List<Int> {
    if (values.isEmpty()) return values
    val w = if (window % 2 == 0) window + 1 else window
    val p = polyOrder.coerceAtMost(w - 1)
    val half = w / 2
    val coeffs = sgCoefficients(w, p)
    val out = DoubleArray(values.size)

    for (i in values.indices) {
        var acc = 0.0
        var idx = 0
        for (k in -half..half) {
            val j = (i + k).coerceIn(0, values.lastIndex)
            acc += coeffs[idx++] * values[j]
        }
        out[i] = acc
    }

    return out.map { it.roundToInt() }
}

private fun sgCoefficients(window: Int, order: Int): DoubleArray {
    val m = window / 2
    val cols = order + 1
    val a = Array(window) { r ->
        val k = r - m
        DoubleArray(cols) { c -> k.toDouble().pow(c) }
    }
    val ata = Array(cols) { DoubleArray(cols) }
    for (i in 0 until cols) {
        for (j in 0 until cols) {
            ata[i][j] = (0 until window).sumOf { a[it][i] * a[it][j] }
        }
    }
    val inv = invertMatrix(ata)
    val at = Array(cols) { c -> DoubleArray(window) { r -> a[r][c] } }
    val pinv = Array(cols) { DoubleArray(window) }
    for (i in 0 until cols) {
        for (j in 0 until window) {
            pinv[i][j] = (0 until cols).sumOf { inv[i][it] * at[it][j] }
        }
    }
    return DoubleArray(window) { pinv[0][it] }
}

private fun invertMatrix(m: Array<DoubleArray>): Array<DoubleArray> {
    val n = m.size
    val a = Array(n) { m[it].clone() }
    val iMat = Array(n) { DoubleArray(n) }
    for (i in 0 until n) iMat[i][i] = 1.0

    for (i in 0 until n) {
        var maxRow = i
        for (k in i + 1 until n) if (abs(a[k][i]) > abs(a[maxRow][i])) maxRow = k

        val tmpA = a[i]
        a[i] = a[maxRow]
        a[maxRow] = tmpA

        val tmpI = iMat[i]
        iMat[i] = iMat[maxRow]
        iMat[maxRow] = tmpI

        val div = a[i][i]
        for (j in 0 until n) {
            a[i][j] /= div
            iMat[i][j] /= div
        }

        for (k in 0 until n) {
            if (k != i) {
                val f = a[k][i]
                for (j in 0 until n) {
                    a[k][j] -= f * a[i][j]
                    iMat[k][j] -= f * iMat[i][j]
                }
            }
        }
    }
    return iMat
}

fun kalmanFilterBasic(values: List<Int>, processNoise: Double, measurementNoise: Double): List<Int> {
    if (values.isEmpty()) return values

    val out = IntArray(values.size)
    var x = values.first().toDouble()
    var p = 1.0
    val q = processNoise
    val r = measurementNoise

    for (i in values.indices) {
        val z = values[i].toDouble()
        val xPred = x
        val pPred = p + q
        val k = pPred / (pPred + r)
        x = xPred + k * (z - xPred)
        p = (1 - k) * pPred
        out[i] = x.roundToInt()
    }

    return out.toList()
}
