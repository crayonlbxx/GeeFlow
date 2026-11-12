package app.geeflow.presentation.feature.device.dashboard.main

import app.geeflow.data.brew.model.BrewProgram
import app.geeflow.data.brew.model.PhaseTransition
import app.geeflow.data.device.model.CleaningType
import app.geeflow.data.user.model.FlowChartRange
import app.geeflow.presentation.feature.device.dashboard.model.ChartData

sealed interface DeviceDashboardEvent {
    data class MaintenanceReminderOpened(val type: CleaningType) : DeviceDashboardEvent
    data class ToggleChartVisibility(val type: DeviceDashboardViewState.DashboardChartType) : DeviceDashboardEvent
    data class FlowChartRangeSelected(val range: FlowChartRange) : DeviceDashboardEvent
    data object UserClicked : DeviceDashboardEvent
    data object DeviceClicked : DeviceDashboardEvent
    data object HotWaterClicked : DeviceDashboardEvent
    data object ManualBrewClicked : DeviceDashboardEvent
    data object FlowControlClicked : DeviceDashboardEvent
    data object StopBrewClicked : DeviceDashboardEvent
    data class BrewClicked(val targetWeightGrams: Float? = null) : DeviceDashboardEvent
    data class LiveRecipeSelected(val name: String, val description: String) : DeviceDashboardEvent
    data object TemperatureClicked : DeviceDashboardEvent
    data object SettingsClicked : DeviceDashboardEvent
    data object CleaningClicked : DeviceDashboardEvent
    data object LiveBrewTestClicked : DeviceDashboardEvent
    data object ConnectedDevicesClicked : DeviceDashboardEvent
    data object ConnectionButtonClicked : DeviceDashboardEvent
    data object BrewDescriptionClicked : DeviceDashboardEvent
    data object EditProfileClicked : DeviceDashboardEvent
    data object DialogDismissed : DeviceDashboardEvent
    data object PermissionDialogResumed : DeviceDashboardEvent
    data object OpenSystemSettingsClicked : DeviceDashboardEvent
    data object Resumed : DeviceDashboardEvent
    data object AlarmClicked : DeviceDashboardEvent
    data class ProfileSelected(val id: String?) : DeviceDashboardEvent
    data class HistoryBrewSelected(
        val name: String,
        val durationSeconds: Int,
        val data: Map<Float, ChartData>,
        val targetData: Map<Float, ChartData>,
        val targetWeightGrams: Float? = null,
        val phaseProgram: BrewProgram? = null,
        val phaseTransitions: List<PhaseTransition> = emptyList(),
    ) : DeviceDashboardEvent
}
