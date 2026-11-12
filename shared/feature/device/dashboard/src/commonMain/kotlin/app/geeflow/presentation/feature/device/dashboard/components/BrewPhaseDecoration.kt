package app.geeflow.presentation.feature.device.dashboard.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.dp
import app.geeflow.presentation.feature.device.dashboard.model.ChartPhaseBoundary
import app.geeflow.ui.text.ClearText
import app.geeflow.ui.text.clearText
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

@Composable
internal fun brewPhaseDecoration(boundaries: List<ChartPhaseBoundary>, showLabels: Boolean = true): Decoration {
    val stepLabel = clearText(ClearText.Step)
    val drawScope = remember { CanvasDrawScope() }
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val label = rememberTextComponent(
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    )

    return object : Decoration {
        override fun drawOverLayers(context: CartesianDrawingContext) = with(context) {
            val direction = layoutDirectionMultiplier
            val start = (if (isLtr) layerBounds.left else layerBounds.right) +
                direction * layerDimensions.startPadding - scroll

            val occupiedLabels = mutableListOf<ClosedFloatingPointRange<Float>>()
            boundaries.sortedBy { it.seconds }.groupBy { it.seconds }.forEach { (seconds, overlappingBoundaries) ->
                val x = start + direction * layerDimensions.xSpacing *
                    ((seconds - ranges.minX) / ranges.xStep).toFloat()
                if (x < layerBounds.left - 1 || x > layerBounds.right + 1) return@forEach

                overlappingBoundaries.forEachIndexed { _, boundary ->
                    val tint = if (boundary.actual) colors.primary else colors.outlineVariant
                    drawScope.draw(density = density, layoutDirection = layoutDirection, canvas = canvas, size = canvasSize) {
                        drawLine(
                            color = tint,
                            start = Offset(x, layerBounds.top),
                            end = Offset(x, layerBounds.bottom),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (boundary.actual) {
                                null
                            } else {
                                PathEffect.dashPathEffect(
                                    floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                                )
                            },
                        )
                    }

                    if (!showLabels) return@forEachIndexed
                    val phaseName = boundary.phaseLabel?.takeUnless { Regex("Step \\d+").matches(it) }
                        ?: boundary.stepNumber?.let { "$stepLabel $it" } ?: return@forEachIndexed
                    val visibleLabel = phaseName.take(14)
                    val labelWidth = (visibleLabel.length * LabelCharacterWidth + LabelPadding).dp.pixels
                    val halfWidth = labelWidth / 2
                    if (layerBounds.width < halfWidth * 2) return@forEachIndexed
                    val gap = LabelBoundaryGap.dp.pixels
                    val left = when {
                        isLtr && x + gap + labelWidth <= layerBounds.right -> x + gap
                        isLtr -> x - gap - labelWidth
                        x - gap - labelWidth >= layerBounds.left -> x - gap - labelWidth
                        else -> x + gap
                    }.coerceIn(layerBounds.left, layerBounds.right - labelWidth)
                    val right = left + labelWidth
                    val centerX = left + halfWidth
                    if (occupiedLabels.any { left < it.endInclusive + LabelGap.dp.pixels && right > it.start }) {
                        return@forEachIndexed
                    }
                    occupiedLabels += left..right
                    val y = layerBounds.top + 8.dp.pixels
                    drawScope.draw(density = density, layoutDirection = layoutDirection, canvas = canvas, size = canvasSize) {
                        drawRoundRect(
                            color = if (boundary.actual) colors.primaryContainer else colors.surfaceContainerHigh,
                            topLeft = Offset(left, y - 6.dp.toPx()),
                            size = Size(right - left, 12.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                        )
                    }
                    label.draw(
                        context = context,
                        text = visibleLabel,
                        x = centerX,
                        y = y,
                        horizontalPosition = Position.Horizontal.Center,
                        verticalPosition = Position.Vertical.Center,
                    )
                }
            }
        }
    }
}

private const val LabelCharacterWidth = 7
private const val LabelPadding = 8
private const val LabelGap = 12
private const val LabelBoundaryGap = 3
