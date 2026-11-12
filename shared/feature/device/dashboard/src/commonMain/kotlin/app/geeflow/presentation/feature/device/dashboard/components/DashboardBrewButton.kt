package app.geeflow.presentation.feature.device.dashboard.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardEvent
import app.geeflow.presentation.feature.device.dashboard.main.DeviceDashboardViewState
import app.geeflow.ui.text.ClearText
import app.geeflow.ui.text.clearText
import geeflow.shared.core.ui.generated.resources.Res
import geeflow.shared.core.ui.generated.resources.common_hot_water
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_manual_control
import org.jetbrains.compose.resources.stringResource
import geeflow.shared.feature.device.dashboard.generated.resources.Res as DashboardRes

@Composable
internal fun DashboardBrewButton(
    viewState: DeviceDashboardViewState,
    onEvent: (DeviceDashboardEvent) -> Unit,
    modifier: Modifier = Modifier,
    targetWeightGrams: Float? = null,
    onStartLiveRecipe: (() -> Unit)? = null,
    startEnabled: Boolean = true,
) {
    val flushLabel = if (viewState.flushDurationSeconds > 0) {
        clearText(ClearText.FlushTimed, viewState.flushDurationSeconds)
    } else {
        clearText(ClearText.FlushUntimed)
    }
    val actions = buildList {
        add(
            BrewButtonAction(
                contentDescription = flushLabel,
                onClick = { onEvent(DeviceDashboardEvent.ManualBrewClicked) },
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null)
            },
        )
        add(
            BrewButtonAction(
                contentDescription = stringResource(DashboardRes.string.device_dashboard_manual_control),
                onClick = { onEvent(DeviceDashboardEvent.FlowControlClicked) },
            ) {
                Icon(Icons.Filled.SettingsSuggest, contentDescription = null)
            },
        )
        if (viewState.hotWaterSupported) {
            add(
                BrewButtonAction(
                    contentDescription = stringResource(Res.string.common_hot_water),
                    onClick = { onEvent(DeviceDashboardEvent.HotWaterClicked) },
                ) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null)
                },
            )
        }
    }
    BrewButton(
        state = viewState.brewButtonState,
        startEnabled = startEnabled,
        stopContentDescription = if (viewState.device.brewStatus == DeviceDashboardViewState.Device.BrewStatus.Manual) {
            clearText(ClearText.StopFlush)
        } else {
            clearText(ClearText.StopBrew)
        },
        sideActions = actions,
        onHeroButtonClick = {
            if (onStartLiveRecipe != null) onStartLiveRecipe()
            else onEvent(DeviceDashboardEvent.BrewClicked(targetWeightGrams))
        },
        onStopClick = { onEvent(DeviceDashboardEvent.StopBrewClicked) },
        modifier = modifier.fillMaxWidth(),
        heroIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
        stopIcon = { Icon(Icons.Filled.Stop, contentDescription = null) },
    )
}
