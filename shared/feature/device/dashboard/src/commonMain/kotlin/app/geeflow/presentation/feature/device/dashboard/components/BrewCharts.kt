package app.geeflow.presentation.feature.device.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.geeflow.data.user.model.FlowChartRange
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState.DashboardChartType
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState.DashboardChartType.FlowRate
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState.DashboardChartType.Pressure
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState.DashboardChartType.Volume
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState.DashboardChartType.Weight
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState.DashboardChartType.WeightRate
import app.geeflow.presentation.feature.device.dashboard.model.BrewChartModel
import app.geeflow.presentation.feature.device.dashboard.model.ChartPhaseBoundary
import app.geeflow.ui.text.ClearText
import app.geeflow.ui.text.clearText
import app.geeflow.ui.theme.GeeFlowTheme
import app.geeflow.ui.theme.disabled
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.AreaFill
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.LineFill
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.LineProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import geeflow.shared.core.ui.generated.resources.unit_bar
import geeflow.shared.core.ui.generated.resources.unit_grams
import geeflow.shared.core.ui.generated.resources.unit_grams_per_second
import geeflow.shared.core.ui.generated.resources.unit_milliliters
import geeflow.shared.core.ui.generated.resources.unit_milliliters_per_second
import geeflow.shared.feature.device.dashboard.generated.resources.Res
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_actual
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_combined_chart
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_cup_flow_chart
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_flow_range_exceeded
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_pressure_chart
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_select_charts
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_volume_relative_chart
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_weight_relative_chart
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_water_flow_chart
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import geeflow.shared.core.ui.generated.resources.Res as CoreRes

private data class BrewChartSeries(
    val yValues: List<Number>,
    val color: Color,
    val unit: String,
    val markerYValues: List<Number> = yValues,
    val emphasized: Boolean = false,
)

private data class TargetBrewData(
    val tX: List<Double>,
    val tY: List<Double>,
    val color: Color,
    val unit: String,
    val markerYValues: List<Double> = tY,
)

private data class CombinedBrewChartSeries(
    val label: String,
    val actual: BrewChartSeries,
    val target: TargetBrewData?,
)

@Composable
internal fun BrewCharts(
    model: BrewChartModel,
    visibleCharts: Set<DashboardChartType>,
    modifier: Modifier = Modifier,
    combinedOnPhone: Boolean = true,
    scrollPanels: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        BrewChartsContent(
            model = model,
            visibleCharts = visibleCharts,
            phone = combinedOnPhone && maxWidth < 600.dp,
            scrollPanels = scrollPanels,
        )
    }
}

@Composable
private fun BrewChartsContent(
    model: BrewChartModel,
    visibleCharts: Set<DashboardChartType>,
    phone: Boolean,
    scrollPanels: Boolean,
) {
    val chartFlowRange = FlowChartRange.TWELVE
    val measurements = model.measurements.entries.sortedBy { it.key }
    val xValues = measurements.map { it.key.toDouble() }
    val points = measurements.map { it.value }
    val targetEntries = model.targets.entries.sortedBy { it.key }
    val boundaries = model.boundaries
    val maxX = if (xValues.isNotEmpty() || model.followMeasurements) {
        maxOf(xValues.maxOrNull() ?: 0.0, MinChartX)
    } else {
        maxOf(
            targetEntries.maxOfOrNull { it.key.toDouble() } ?: 0.0,
            boundaries.maxOfOrNull { it.seconds } ?: 0.0,
            MinChartX,
        )
    } + ChartXPadding
    val syncState = rememberBrewSyncState()
    val pressureColor = GeeFlowTheme.colors.pressure
    val waterColor = GeeFlowTheme.colors.water
    val waterVariant = GeeFlowTheme.colors.waterVariant
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant

    val showPressure = Pressure in visibleCharts
    val showFlow = FlowRate in visibleCharts
    val showCupFlow = WeightRate in visibleCharts
    val showVolume = Volume in visibleCharts
    val showWeight = Weight in visibleCharts
    val chartCount = listOf(showPressure, showFlow, showCupFlow, showVolume, showWeight).count { it }
    if (chartCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.device_dashboard_select_charts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val unitBar = stringResource(CoreRes.string.unit_bar)
    val unitMlPerSecond = stringResource(CoreRes.string.unit_milliliters_per_second)
    val unitGramsPerSecond = stringResource(CoreRes.string.unit_grams_per_second)
    val unitMl = stringResource(CoreRes.string.unit_milliliters)
    val unitGrams = stringResource(CoreRes.string.unit_grams)
    val pressureTarget = targetEntries.takeIf { entries -> entries.any { it.value.pressure > 0f } }?.let { entries ->
        TargetBrewData(
            tX = entries.map { it.key.toDouble() },
            tY = entries.map { it.value.pressure.toDouble() },
            color = pressureColor,
            unit = unitBar,
        )
    }
    val flowTarget = targetEntries.takeIf { entries -> entries.any { it.value.volumePerSecond > 0f } }?.let { entries ->
        TargetBrewData(
            tX = entries.map { it.key.toDouble() },
            tY = entries.map { it.value.volumePerSecond.toDouble() },
            color = waterColor,
            unit = unitMlPerSecond,
        )
    }
    val maxFlow = maxOf(
        points.maxOfOrNull { it.volumePerSecond.toDouble() } ?: 0.0,
        targetEntries.maxOfOrNull { it.value.volumePerSecond.toDouble() } ?: 0.0,
    )
    val flowOverflow = if (maxFlow > chartFlowRange.maxMillilitersPerSecond) {
        stringResource(
            Res.string.device_dashboard_flow_range_exceeded,
            maxFlow.formatOneDecimal(),
            chartFlowRange.maxMillilitersPerSecond,
        )
    } else {
        null
    }

    val cupTarget = targetEntries.takeIf { entries -> entries.any { it.value.weightPerSecond > 0f } }?.let { entries ->
        TargetBrewData(
            entries.map { it.key.toDouble() },
            entries.map { it.value.weightPerSecond.toDouble() },
            neutralColor,
            unitGramsPerSecond,
        )
    }
    val volumeTarget = targetEntries.takeIf { entries -> entries.any { it.value.volume > 0f } }?.let { entries ->
        TargetBrewData(
            entries.map { it.key.toDouble() },
            entries.map { it.value.volume.toDouble() },
            waterVariant,
            unitMl,
        )
    }
    val weightTarget = targetEntries.takeIf { entries -> entries.any { it.value.weight > 0f } }?.let { entries ->
        TargetBrewData(
            entries.map { it.key.toDouble() },
            entries.map { it.value.weight.toDouble() },
            neutralColor,
            unitGrams,
        )
    }

    // Series are normalized to a shared 0–12 scale; markers retain the raw values and units.
    fun relativeValue(value: Number, maximum: Double) = value.toDouble() / maximum * UnifiedChartMax
    fun relativeTarget(target: TargetBrewData?, maximum: Double) = target?.copy(
        tY = target.tY.map { relativeValue(it, maximum) },
    )
    fun toCombinedSeries(
        label: String,
        values: List<Number>,
        color: Color,
        unit: String,
        maximum: Double,
        target: TargetBrewData?,
        emphasized: Boolean = false,
    ) = CombinedBrewChartSeries(
        label = label,
        actual = BrewChartSeries(
            yValues = values.map { relativeValue(it, maximum) },
            color = color,
            unit = unit,
            markerYValues = values,
            emphasized = emphasized,
        ),
        target = relativeTarget(target, maximum)?.copy(color = color),
    )

    val pressureMaximum = UnifiedChartMax
    val combinedFlowOverflow = if (maxFlow > UnifiedChartMax) {
        stringResource(
            Res.string.device_dashboard_flow_range_exceeded,
            maxFlow.formatOneDecimal(),
            UnifiedChartMax.toInt(),
        )
    } else {
        null
    }
    val cupFlowMaximum = maxOf(
        12.0,
        points.maxOfOrNull { it.weightPerSecond.toDouble() } ?: 0.0,
        targetEntries.maxOfOrNull { it.value.weightPerSecond.toDouble() } ?: 0.0,
    )
    val volumeMaximum = maxOf(
        40.0,
        points.maxOfOrNull { it.volume.toDouble() } ?: 0.0,
        targetEntries.maxOfOrNull { it.value.volume.toDouble() } ?: 0.0,
    )
    val weightMaximum = maxOf(
        40.0,
        points.maxOfOrNull { it.weight.toDouble() } ?: 0.0,
        targetEntries.maxOfOrNull { it.value.weight.toDouble() } ?: 0.0,
    )
    val pressureTitle = stringResource(Res.string.device_dashboard_pressure_chart)
    val flowTitle = stringResource(Res.string.device_dashboard_water_flow_chart)
    val cupFlowTitle = stringResource(Res.string.device_dashboard_cup_flow_chart)
    val relativeVolumeTitle = stringResource(Res.string.device_dashboard_volume_relative_chart)
    val relativeWeightTitle = stringResource(Res.string.device_dashboard_weight_relative_chart)
    val volumeColor = MaterialTheme.colorScheme.secondary
    val weightColor = MaterialTheme.colorScheme.tertiary
    val combinedMetrics = buildList<CombinedBrewChartSeries> {
        if (showPressure) add(
            toCombinedSeries(
                pressureTitle,
                points.map { it.pressure }, pressureColor, unitBar, pressureMaximum, pressureTarget,
                emphasized = true,
            ),
        )
        if (showFlow) add(
            toCombinedSeries(
                flowTitle,
                points.map { it.volumePerSecond }, waterColor, unitMlPerSecond, UnifiedChartMax, flowTarget,
                emphasized = true,
            ),
        )
        if (showCupFlow) add(
            toCombinedSeries(
                cupFlowTitle,
                points.map { it.weightPerSecond }, waterVariant, unitGramsPerSecond, cupFlowMaximum, cupTarget,
            ),
        )
        if (showVolume) add(
            toCombinedSeries(
                relativeVolumeTitle,
                points.map { it.volume }, volumeColor, unitMl, volumeMaximum, volumeTarget,
            ),
        )
        if (showWeight) add(
            toCombinedSeries(
                relativeWeightTitle,
                points.map { it.weight }, weightColor, unitGrams, weightMaximum, weightTarget,
            ),
        )
    }

    @Composable
    fun panel(
        id: Int,
        title: String,
        actual: BrewChartSeries,
        target: TargetBrewData?,
        yMax: Double,
        yStep: Double,
        overflow: String? = null,
        modifier: Modifier,
    ) {
        ChartPanel(
            title = title,
            seriesColor = actual.color,
            overflow = overflow,
            modifier = modifier,
        ) { chartModifier ->
            key(yMax) {
                BrewDataChart(
                    xValues = xValues,
                    chartSeries = listOf(actual),
                    targets = listOfNotNull(target),
                    fixedMaxY = yMax,
                    yStep = yStep,
                    maxX = maxX,
                    phaseBoundaries = boundaries,
                    syncState = syncState,
                    chartId = id,
                    modifier = chartModifier,
                    showTimeAxis = id == listOfNotNull(
                        0.takeIf { showPressure },
                        1.takeIf { showFlow },
                        2.takeIf { showCupFlow },
                        3.takeIf { showVolume },
                        4.takeIf { showWeight }
                    ).first(),
                    showPhaseLabels = id == listOfNotNull(
                        0.takeIf { showPressure },
                        1.takeIf { showFlow },
                        2.takeIf { showCupFlow },
                        3.takeIf { showVolume },
                        4.takeIf { showWeight }
                    ).first(),
                )
            }
        }
    }

    val chartContent: @Composable (Modifier) -> Unit = { itemModifier ->
        Column(
            modifier = if (chartCount > 2 && scrollPanels) itemModifier.verticalScroll(rememberScrollState()) else itemModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val panelModifier = if (chartCount > 2 && scrollPanels) Modifier.height(180.dp).fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
            if (showPressure) {
                panel(
                    id = 0,
                    title = stringResource(Res.string.device_dashboard_pressure_chart),
                    actual = BrewChartSeries(points.map { it.pressure }, pressureColor, unitBar, emphasized = true),
                    target = pressureTarget,
                    yMax = 12.0,
                    yStep = 2.0,
                    modifier = panelModifier,
                )
            }
            if (showFlow) {
                panel(
                    id = 1,
                    title = stringResource(Res.string.device_dashboard_water_flow_chart),
                    actual = BrewChartSeries(points.map { it.volumePerSecond }, waterColor, unitMlPerSecond, emphasized = true),
                    target = flowTarget,
                    yMax = chartFlowRange.maxMillilitersPerSecond.toDouble(),
                    yStep = chartFlowRange.maxMillilitersPerSecond.toDouble() / 4.0,
                    overflow = flowOverflow,
                    modifier = panelModifier,
                )
            }
            if (showCupFlow) {
                panel(
                    id = 2,
                    title = stringResource(Res.string.device_dashboard_cup_flow_chart),
                    actual = BrewChartSeries(points.map { it.weightPerSecond }, neutralColor, unitGramsPerSecond),
                    target = cupTarget,
                    yMax = 12.0,
                    yStep = 2.0,
                    modifier = panelModifier,
                )
            }
            if (showVolume) {
                panel(
                    id = 3,
                    title = clearText(ClearText.WaterOutputChart),
                    actual = BrewChartSeries(points.map { it.volume }, waterVariant, unitMl),
                    target = volumeTarget,
                    yMax = maxOf(
                        40.0,
                        points.maxOfOrNull { it.volume.toDouble() } ?: 0.0,
                        targetEntries.maxOfOrNull { it.value.volume.toDouble() } ?: 0.0
                    ),
                    yStep = 10.0,
                    modifier = panelModifier,
                )
            }
            if (showWeight) {
                panel(
                    id = 4,
                    title = clearText(ClearText.CupWeightChart),
                    actual = BrewChartSeries(points.map { it.weight }, neutralColor, unitGrams),
                    target = weightTarget,
                    yMax = maxOf(
                        40.0,
                        points.maxOfOrNull { it.weight.toDouble() } ?: 0.0,
                        targetEntries.maxOfOrNull { it.value.weight.toDouble() } ?: 0.0
                    ),
                    yStep = 10.0,
                    modifier = panelModifier,
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (phone) {
            ChartPanel(
                title = stringResource(Res.string.device_dashboard_combined_chart),
                seriesColor = MaterialTheme.colorScheme.primary,
                overflow = combinedFlowOverflow,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { chartModifier ->
                BrewDataChart(
                    xValues = xValues,
                    chartSeries = combinedMetrics.map { it.actual },
                    targets = combinedMetrics.mapNotNull { it.target },
                    fixedMaxY = UnifiedChartMax,
                    yStep = 2.0,
                    maxX = maxX,
                    phaseBoundaries = boundaries,
                    syncState = syncState,
                    chartId = CombinedPhoneChartId,
                    modifier = chartModifier,
                    zoomEnabled = true,
                    showAreas = false,
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                combinedMetrics.forEach { series -> LegendItem(series.label, series.actual.color, false) }
                if (targetEntries.isNotEmpty()) LegendItem(clearText(ClearText.Target), neutralColor, true)
            }
        } else {
            chartContent(Modifier.weight(1f).fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(start = 118.dp, end = 8.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(stringResource(Res.string.device_dashboard_actual), neutralColor, false)
                if (targetEntries.isNotEmpty()) LegendItem(clearText(ClearText.Target), neutralColor, true)
            }
        }
    }
}

@Composable
private fun ChartPanel(
    title: String,
    seriesColor: Color,
    modifier: Modifier = Modifier,
    overflow: String? = null,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(6.dp)) {
        if (maxWidth < 500.dp) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), color = seriesColor, fontWeight = FontWeight.SemiBold)
                }
                if (overflow != null) {
                    Text(
                        overflow,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(
                    Modifier.width(106.dp).fillMaxHeight().padding(start = 4.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title.substringBefore(" ("),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = seriesColor
                    )
                    Text(
                        title.substringAfter("(", "").removeSuffix(")"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (overflow != null) {
                        Text(
                            overflow,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.width(22.dp).height(8.dp)) {
            val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
            drawLine(
                color,
                Offset.Zero.copy(y = size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = effect
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BrewDataChart(
    xValues: List<Double>,
    chartSeries: List<BrewChartSeries>,
    targets: List<TargetBrewData>,
    fixedMaxY: Double,
    yStep: Double,
    maxX: Double,
    phaseBoundaries: List<ChartPhaseBoundary>,
    syncState: BrewSyncState,
    chartId: Int,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = false,
    showAreas: Boolean = true,
    showTimeAxis: Boolean = true,
    showPhaseLabels: Boolean = true,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(xValues, chartSeries, targets) {
        producer.runTransaction {
            lineModel {
                chartSeries.forEach { series ->
                    if (xValues.isNotEmpty()) {
                        series(x = xValues, y = series.yValues)
                    } else {
                        series(x = listOf(0.0), y = listOf(0.0))
                    }
                }
                targets.forEach { target ->
                    if (target.tX.isNotEmpty()) {
                        series(x = target.tX, y = target.tY)
                    } else {
                        series(x = listOf(0.0), y = listOf(0.0))
                    }
                }
            }
        }
    }
    val colors = chartSeries.map { it.color }
    val markerSeries = remember(xValues, chartSeries, targets) {
        chartSeries.map { series ->
            BrewMarkerSeries(
                xValues,
                series.yValues.map { it.toDouble() },
                series.color,
                series.unit,
                series.markerYValues.map { it.toDouble() },
            )
        } +
            targets.map { target -> BrewMarkerSeries(target.tX, target.tY, target.color, target.unit, target.markerYValues) }
    }
    val marker = rememberBrewChartMarker(markerSeries)
    val listener = rememberBrewMarkerVisibilityListener(syncState, chartId)
    val syncMarkerX = if (syncState.activeChartId != chartId) syncState.markerX else null
    BrewChart(
        modelProducer = producer,
        colors = colors,
        actualLineThicknesses = chartSeries.map { if (it.emphasized) PrimaryLineThickness else SecondaryLineThickness },
        targetColors = targets.map { it.color },
        maxX = maxX,
        maxY = fixedMaxY,
        yStep = yStep,
        marker = marker,
        markerVisibilityListener = listener,
        syncMarkerX = syncMarkerX,
        phaseBoundaries = phaseBoundaries,
        zoomEnabled = zoomEnabled,
        showAreas = showAreas,
        showTimeAxis = showTimeAxis,
        showPhaseLabels = showPhaseLabels,
        modifier = modifier,
    )
}

@Composable
private fun BrewChart(
    modelProducer: CartesianChartModelProducer,
    colors: List<Color>,
    actualLineThicknesses: List<Float>,
    targetColors: List<Color>,
    maxX: Double,
    maxY: Double,
    yStep: Double,
    marker: BrewChartMarker,
    markerVisibilityListener: CartesianMarkerVisibilityListener,
    syncMarkerX: Double?,
    phaseBoundaries: List<ChartPhaseBoundary>,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = false,
    showAreas: Boolean = true,
    showTimeAxis: Boolean = true,
    showPhaseLabels: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        val tickCount = if (maxWidth < 500.dp) PhoneTimeDivisions else TabletTimeDivisions
        if (colors.isEmpty() && targetColors.isEmpty()) return@BoxWithConstraints
        val scrollState = rememberVicoScrollState(scrollEnabled = zoomEnabled)
        val zoomState = rememberVicoZoomState(
            zoomEnabled = zoomEnabled,
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content,
            maxZoom = Zoom.max(Zoom.fixed(4f), Zoom.Content),
        )
        val phaseDecoration = brewPhaseDecoration(phaseBoundaries, showPhaseLabels)
        val syncDecoration = rememberBrewSyncDecoration(marker, syncMarkerX)
        CartesianChartHost(
            zoomState = zoomState,
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineProvider.series(
                        *colors.mapIndexed { index, color ->
                            LineCartesianLayer.rememberLine(
                                fill = LineFill.single(Fill(color)),
                                stroke = LineCartesianLayer.LineStroke.Continuous(
                                    thickness = actualLineThicknesses.getOrElse(index) { SecondaryLineThickness }.dp,
                                ),
                                interpolator = LineCartesianLayer.Interpolator.cubic(),
                                areaFill = if (showAreas) AreaFill.single(
                                    Fill(Brush.verticalGradient(listOf(color.disabled(ChartAreaAlpha), Color.Transparent)))
                                ) else null,
                            )
                        }.toTypedArray() + targetColors.map { color ->
                            LineCartesianLayer.rememberLine(
                                fill = LineFill.single(Fill(color.copy(alpha = TargetLineAlpha))),
                                stroke = LineCartesianLayer.LineStroke.Dashed(
                                    thickness = TargetLineThickness.dp,
                                    dashLength = 4.dp,
                                    gapLength = 4.dp,
                                ),
                                areaFill = null
                            )
                        }.toTypedArray(),
                    ),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = maxY),
                ),
                getXStep = { _, _, _ -> getXStep(maxX, tickCount) },
                marker = marker,
                markerVisibilityListener = markerVisibilityListener,
                markerController = CartesianMarkerController.rememberShowOnPress(consumeMoveEvents = true),
                decorations = listOf(phaseDecoration, syncDecoration),
                startAxis = VerticalAxis.rememberStart(
                    size = BaseAxis.Size.Fixed(32.dp),
                    tick = null,
                    label = rememberAxisLabelComponent(
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ),
                    line = null,
                    tickLength = 2.dp,
                    itemPlacer = VerticalAxis.ItemPlacer.step(step = { yStep }),
                    valueFormatter = { _, value, _ ->
                        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
                    },
                    guideline = rememberAxisGuidelineComponent(
                        fill = Fill(MaterialTheme.colorScheme.outlineVariant),
                        shape = DashedShape(CircleShape, dashLength = 2.dp, gapLength = 3.dp),
                    ),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    line = null,
                    tick = null,
                    label = if (showTimeAxis) {
                        rememberAxisLabelComponent(
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    } else {
                        null
                    },
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                        spacing = { 1 },
                        shiftExtremeLines = false,
                        addExtremeLabelPadding = false
                    ),
                    valueFormatter = { _, value, _ -> "${value.toLong()}s" },
                    guideline = rememberAxisGuidelineComponent(
                        fill = Fill(MaterialTheme.colorScheme.outlineVariant),
                        shape = DashedShape(CircleShape, dashLength = 2.dp, gapLength = 3.dp),
                    ),
                ),
            ),
            modelProducer = modelProducer,
            animationSpec = null,
            animateIn = false,
            modifier = Modifier.fillMaxSize(),
            scrollState = scrollState,
        )
    }
}

private const val ChartXPadding = 5.0
private const val MinChartX = 20.0
private const val StepNiceLow = 1.5
private const val StepNiceMid = 3.0
private const val StepNiceHigh = 7.0
private const val StepNice5 = 5.0
private const val StepNiceMax = 10.0

private fun getXStep(maxValue: Double, targetStepCount: Int = 10): Double {
    if (maxValue <= 0) return 1.0
    val roughStep = maxValue / targetStepCount
    val magnitude = 10.0.pow(floor(log10(roughStep)))
    val normalizedStep = roughStep / magnitude
    val niceMultiplier = when {
        normalizedStep <= StepNiceLow -> 1.0
        normalizedStep <= StepNiceMid -> 2.0
        normalizedStep <= StepNiceHigh -> StepNice5
        else -> StepNiceMax
    }
    return niceMultiplier * magnitude
}

private fun Double.formatOneDecimal() = ((this * DecimalPrecision).toInt() / DecimalPrecision).toString()

private const val TargetLineAlpha = 0.34f
private const val TargetLineThickness = 1f
private const val SecondaryLineThickness = 1.1f
private const val PrimaryLineThickness = 2.2f
private const val DecimalPrecision = 10.0

private const val ChartAreaAlpha = 0.18f
private const val UnifiedChartMax = 12.0
private const val CombinedPhoneChartId = 5

private const val PhoneTimeDivisions = 4
private const val TabletTimeDivisions = 10
