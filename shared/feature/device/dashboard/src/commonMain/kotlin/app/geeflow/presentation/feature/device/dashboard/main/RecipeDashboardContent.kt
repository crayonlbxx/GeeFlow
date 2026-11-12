package app.geeflow.presentation.feature.device.dashboard.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import app.geeflow.data.brew.model.Condition
import app.geeflow.data.brew.model.BrewProgram
import app.geeflow.data.brew.model.PhaseControl
import app.geeflow.data.device.BookooScaleDevice
import app.geeflow.domain.device.recipe.LiveRecipeDocument
import app.geeflow.domain.device.LiveTestSample
import app.geeflow.domain.device.recipe.RecipeControl
import app.geeflow.domain.device.recipe.RecipeValueSource
import app.geeflow.presentation.feature.device.dashboard.components.BrewButtonState
import app.geeflow.presentation.feature.device.dashboard.components.DashboardBrewButton
import app.geeflow.presentation.feature.device.dashboard.components.ExtractionReadout
import app.geeflow.presentation.feature.device.dashboard.components.BrewCharts
import app.geeflow.presentation.feature.device.dashboard.components.FlushOverlay
import app.geeflow.presentation.feature.device.dashboard.components.TopBar
import app.geeflow.presentation.feature.device.dashboard.model.BrewChartModel
import app.geeflow.presentation.feature.device.dashboard.model.ChartData
import app.geeflow.presentation.feature.device.dashboard.model.ChartPhaseBoundary
import app.geeflow.presentation.feature.device.dashboard.model.chartModel
import app.geeflow.data.brew.model.plannedDurationMillis
import app.geeflow.presentation.feature.device.dashboard.profiles.ProfileList
import app.geeflow.presentation.feature.device.dashboard.profiles.ProfileListEvent
import app.geeflow.presentation.feature.device.dashboard.profiles.ProfileListViewState
import app.geeflow.presentation.feature.device.dashboard.profiles.RecipeCurvePreview
import app.geeflow.ui.modifier.geeFlowInsetsPadding
import app.geeflow.ui.theme.ClearScreenTheme
import geeflow.shared.feature.device.dashboard.generated.resources.Res
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_connect_scale
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_connecting_scale
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_dose
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_machine_weight_target
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_use_profile
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_ratio
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_recipe_library
import geeflow.shared.feature.device.dashboard.generated.resources.device_dashboard_stop_brew
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_preview_only
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_review_recipe
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_recipe_unavailable
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_machine_not_ready
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_reference_yield
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_pause
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_done
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_no_scale_found
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_scan
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_decrease
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_increase
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_machine_error
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_scale_required
import geeflow.shared.feature.device.dashboard.generated.resources.dashboard_brewing_state
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RecipeDashboardReviewPreviewContent(initialPage: Int = BrewPage) {
    val state = remember { getMockDeviceDashboardViewState().copy(brewButtonState = BrewButtonState.Brewing) }
    val profiles = remember { getMockProfileListViewState() }
    val pagerState = rememberPagerState(initialPage = initialPage) { 2 }
    RecipeDashboardContent(
        state = state,
        profiles = profiles,
        onEvent = {},
        onProfileListEvent = {},
        pagerState = pagerState,
        snackbarState = remember { SnackbarHostState() },
        selectedLiveRecipe = null,
        localRecipes = emptyList(),
        scaleDevices = emptyList(),
        scaleConnected = false,
        connectedScaleName = null,
        connectingScale = false,
        cupFlowGramsPerSecond = null,
        scaleWeightGrams = null,
        liveSamples = emptyList(),
        liveIssues = emptyList(),
        liveActive = false,
        liveStopPending = false,
        onStopLiveRecipe = {},
        onLiveRecipeSelected = {},
        onLiveRecipeFavoriteChanged = {},
        onConnectScale = {},
        onRescanScales = {},
        onBackToLibrary = {},
        onStartLiveRecipe = {},
    )
}

@Composable
internal fun RecipeDashboardContent(
    state: DeviceDashboardViewState,
    profiles: ProfileListViewState,
    onEvent: (DeviceDashboardEvent) -> Unit,
    onProfileListEvent: (ProfileListEvent) -> Unit,
    pagerState: PagerState,
    snackbarState: SnackbarHostState,
    selectedLiveRecipe: LiveRecipeDocument?,
    localRecipes: List<LiveRecipeDocument>,
    scaleDevices: List<BookooScaleDevice>,
    scaleConnected: Boolean,
    connectedScaleName: String?,
    connectingScale: Boolean,
    cupFlowGramsPerSecond: Float?,
    scaleWeightGrams: Float?,
    liveSamples: List<LiveTestSample>,
    liveIssues: List<String>,
    liveActive: Boolean,
    liveStopPending: Boolean,
    onStopLiveRecipe: () -> Unit,
    onLiveRecipeSelected: (LiveRecipeDocument) -> Unit,
    onLiveRecipeFavoriteChanged: (LiveRecipeDocument) -> Unit,
    onConnectScale: (BookooScaleDevice) -> Unit,
    onRescanScales: () -> Unit,
    onBackToLibrary: () -> Unit,
    onStartLiveRecipe: (ShotRecipeSettings) -> Unit,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val selectedProfile = profiles.profiles.firstOrNull { it.selected }.takeUnless { selectedLiveRecipe != null }
    val shotSettings = remember(selectedProfile?.id, selectedLiveRecipe?.id) {
        val target = when (val finish = selectedProfile?.finishCondition) {
            is Condition.Weight -> finish.target
            else -> selectedLiveRecipe?.settings?.yieldGrams ?: ShotRecipeSettings.DEFAULT_TARGET_GRAMS
        }
        ShotRecipeSettings.forRecipe(selectedLiveRecipe?.settings?.doseGrams ?: ShotRecipeSettings.DEFAULT_DOSE_GRAMS, target)
    }
    var settings by remember(selectedProfile?.id, selectedLiveRecipe?.id) {
        mutableStateOf(shotSettings)
    }
    val active = liveActive || liveStopPending || state.device.isBrewing || state.device.dispensingHotWater
    val showingBrew = pagerState.currentPage == BrewPage
    androidx.compose.runtime.LaunchedEffect(showingBrew) {
        if (showingBrew) focusManager.clearFocus(force = true)
    }
    if (showingBrew) {
        NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) {
            if (!active) onBackToLibrary()
        }
    }
    androidx.compose.runtime.LaunchedEffect(pagerState.settledPage, shotSettings) {
        if (pagerState.settledPage == LibraryPage) settings = shotSettings
    }
    val chart = selectedLiveRecipe?.let { liveRecipeChartModel(it, liveSamples, liveActive) } ?: state.chartModel(profiles)
    val cupFlow = state.brew.data.maxByOrNull { it.key }?.value?.weightPerSecond
    val cupFlowRate = cupFlow.takeIf { state.device.isBrewing }

    ClearScreenTheme {
      BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).geeFlowInsetsPadding(),
      ) {
        val expanded = maxWidth >= 900.dp
        val phoneBottomClearance = if (expanded) 0.dp else PhoneStandClearance
        Column(Modifier.fillMaxSize().padding(bottom = phoneBottomClearance)) {
            TopBar(
                device = state.device,
                photoFileName = state.user.photoFileName,
                externalScaleConnected = scaleConnected,
                compactPhoneTemperatures = true,
                onEvent = onEvent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            HorizontalPager(
                state = pagerState,
                key = { it },
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { pageIndex ->
                val pageOffset = pagerState.getOffsetDistanceInPages(pageIndex).coerceIn(-1f, 1f)
                val scale by animateFloatAsState(1f - kotlin.math.abs(pageOffset) * .06f, label = "recipePageZoom")
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }) {
                    if (pageIndex == LibraryPage) {
                        RecipeLibraryPage(
                            profiles = profiles,
                            localRecipes = localRecipes,
                            onProfileListEvent = onProfileListEvent,
                            onLiveRecipeSelected = onLiveRecipeSelected,
                            onLiveRecipeFavoriteChanged = onLiveRecipeFavoriteChanged,
                            expanded = expanded,
                            liveScaleConnected = scaleConnected,
                        )
                    } else {
                        BrewSetupPage(
                            state = state,
                            profiles = profiles,
                            selectedProfile = selectedProfile,
                            liveRecipe = selectedLiveRecipe,
                            active = active,
                            liveActive = liveActive || liveStopPending,
                            liveStopPending = liveStopPending,
                            liveIssues = liveIssues,
                            onStopLiveRecipe = onStopLiveRecipe,
                            onBackToLibrary = onBackToLibrary,
                            settings = settings,
                            onSettingsChange = { settings = it },
                            chart = chart,
                            cupFlowRate = cupFlowRate,
                            liveCupFlowRate = cupFlowGramsPerSecond,
                            liveScaleWeightGrams = scaleWeightGrams,
                            scaleDevices = scaleDevices,
                            scaleConnected = scaleConnected,
                            connectedScaleName = connectedScaleName,
                            connectingScale = connectingScale,
                            onConnectScale = onConnectScale,
                            onRescanScales = onRescanScales,
                            onEvent = onEvent,
                            onStart = { onStartLiveRecipe(settings) },
                            expanded = expanded,
                        )
                    }
                }
            }
        }
        SnackbarHost(
            snackbarState,
            Modifier.align(Alignment.BottomCenter).padding(
                start = 16.dp,
                end = 16.dp,
                bottom = phoneBottomClearance,
            ),
        )
        FlushOverlay(state, onEvent)
      }
    }
}

@Composable
private fun RecipeLibraryPage(
    profiles: ProfileListViewState,
    localRecipes: List<LiveRecipeDocument>,
    onProfileListEvent: (ProfileListEvent) -> Unit,
    onLiveRecipeSelected: (LiveRecipeDocument) -> Unit,
    onLiveRecipeFavoriteChanged: (LiveRecipeDocument) -> Unit,
    expanded: Boolean,
    liveScaleConnected: Boolean,
) {
    if (expanded) {
        val selectedProfile = profiles.profiles.firstOrNull { it.selected } ?: profiles.profiles.firstOrNull()
        if (profiles.showHistory || selectedProfile == null) {
            ProfileList(
                viewState = profiles,
                onEvent = onProfileListEvent,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                onLiveRecipeSelected = onLiveRecipeSelected,
                liveScaleConnected = liveScaleConnected,
                onLiveRecipeFavoriteChanged = onLiveRecipeFavoriteChanged,
            )
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileList(
                    viewState = profiles,
                    onEvent = onProfileListEvent,
                    modifier = Modifier.weight(.62f).fillMaxHeight(),
                    navigateOnProfileSelect = false,
                    onLiveRecipeSelected = onLiveRecipeSelected,
                    liveScaleConnected = liveScaleConnected,
                    onLiveRecipeFavoriteChanged = onLiveRecipeFavoriteChanged,
                )
                TabletRecipePreview(
                    profile = selectedProfile,
                    onUseProfile = { onProfileListEvent(ProfileListEvent.ProfileSelected(selectedProfile.id)) },
                    modifier = Modifier.weight(.38f).fillMaxHeight(),
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileList(
                viewState = profiles,
                onEvent = onProfileListEvent,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                phone = true,
                onLiveRecipeSelected = onLiveRecipeSelected,
                liveScaleConnected = liveScaleConnected,
                onLiveRecipeFavoriteChanged = onLiveRecipeFavoriteChanged,
            )
        }
    }
}

@Composable
private fun TabletRecipePreview(
    profile: ProfileListViewState.Profile,
    onUseProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            profile.description.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RecipeCurvePreview(profile, Modifier.fillMaxWidth().height(112.dp))
            val phases = (profile.program as? BrewProgram.Phases)?.phases.orEmpty()
            if (phases.isNotEmpty()) {
                HorizontalDivider()
                phases.forEachIndexed { index, phase ->
                    val target = when (val control = phase.control) {
                        is PhaseControl.Pressure -> "${control.bar.toOneDecimal()} bar"
                        is PhaseControl.Flow -> "${control.millilitresPerSecond.toOneDecimal()} ml/s"
                        PhaseControl.PumpPause -> stringResource(Res.string.dashboard_pause)
                    }
                    RecipePhaseDetail(
                        phase.name.ifBlank { (index + 1).toString() },
                        target,
                        phase.plannedDurationMillis(),
                    )
                }
            }
        }
        Button(onClick = onUseProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(Res.string.dashboard_use_profile))
        }
    }
}

@Composable
private fun BrewSetupPage(
    state: DeviceDashboardViewState,
    profiles: ProfileListViewState,
    selectedProfile: ProfileListViewState.Profile?,
    liveRecipe: LiveRecipeDocument?,
    active: Boolean,
    liveActive: Boolean,
    liveStopPending: Boolean,
    liveIssues: List<String>,
    onStopLiveRecipe: () -> Unit,
    onBackToLibrary: () -> Unit,
    settings: ShotRecipeSettings,
    onSettingsChange: (ShotRecipeSettings) -> Unit,
    chart: BrewChartModel,
    cupFlowRate: Float?,
    liveCupFlowRate: Float?,
    liveScaleWeightGrams: Float?,
    scaleDevices: List<BookooScaleDevice>,
    scaleConnected: Boolean,
    connectedScaleName: String?,
    connectingScale: Boolean,
    onConnectScale: (BookooScaleDevice) -> Unit,
    onRescanScales: () -> Unit,
    onEvent: (DeviceDashboardEvent) -> Unit,
    onStart: () -> Unit,
    expanded: Boolean,
) {
    var scalePickerOpen by remember { mutableStateOf(false) }
    val nativeWeightStop = selectedProfile?.finishCondition is Condition.Weight
    val nativeScaleReady = nativeWeightStop && state.device.smartScaleConnected
    // The physical machine's native stop register accepts whole grams.
    val referenceGrams = if (nativeScaleReady && !state.device.isDemo && selectedProfile?.experimental == false) {
        settings.machineStopGrams
    } else settings.targetGrams
    val nativeTargetGrams = referenceGrams.takeIf { nativeScaleReady }
    val visibleCharts = state.visibleCharts - DeviceDashboardViewState.DashboardChartType.WeightRate
    val scaleNeeded = if (liveRecipe != null) !scaleConnected
        else selectedProfile?.brewByWeight == true && !state.device.smartScaleConnected
    val canConnectScale = scaleNeeded && liveIssues.isEmpty() &&
        !state.device.alarm && state.brewButtonState == BrewButtonState.Idle &&
        state.device.connectionStatus == DeviceDashboardViewState.Device.ConnectionStatus.Connected
    val blocker = when {
        active -> null
        liveRecipe != null && liveRecipe.settings.pressureCeilingBar > 9f ->
            stringResource(Res.string.dashboard_preview_only)
        liveIssues.isNotEmpty() -> if (liveRecipe?.mappings?.isNotEmpty() == true && !liveRecipe.mappingsAccepted) {
            stringResource(Res.string.dashboard_review_recipe)
        } else stringResource(Res.string.dashboard_recipe_unavailable)
        state.device.connectionStatus != DeviceDashboardViewState.Device.ConnectionStatus.Connected ->
            stringResource(Res.string.dashboard_machine_not_ready)
        state.device.alarm -> stringResource(Res.string.dashboard_machine_error)
        state.brewButtonState != BrewButtonState.Idle -> null
        liveRecipe == null && selectedProfile == null -> stringResource(Res.string.dashboard_recipe_unavailable)
        scaleNeeded -> stringResource(Res.string.dashboard_scale_required)
        else -> null
    }
    val setup: @Composable () -> Unit = {
        DoseRatioControls(settings, referenceGrams, !active && state.brewButtonState == BrewButtonState.Idle,
            onSettingsChange, Modifier.fillMaxWidth())
        if (nativeTargetGrams != null || liveRecipe != null && scaleConnected) {
            Text(
                stringResource(Res.string.dashboard_machine_weight_target, referenceGrams.toOneDecimal()),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (liveRecipe != null && scaleConnected) {
            Text(connectedScaleName ?: "BOOKOO", style = MaterialTheme.typography.labelMedium)
        }
        RecipeDetails(selectedProfile, liveRecipe, liveIssues)
    }
    val readouts: @Composable () -> Unit = {
        ExtractionReadout(
            state,
            profiles.profiles,
            { onEvent(DeviceDashboardEvent.ToggleChartVisibility(it)) },
            Modifier.fillMaxWidth(),
            compact = !expanded,
            cupFlowGramsPerSecond = if (liveRecipe != null) liveCupFlowRate else cupFlowRate,
            scaleWeightGrams = liveScaleWeightGrams,
            scaleConnected = scaleConnected,
            targetWeightGrams = settings.targetGrams.takeIf { liveRecipe != null },
            nativeTargetWeightGrams = nativeTargetGrams,
            liveSample = liveSamplesLast(chart).takeIf { liveActive },
        )
    }
    val charts: @Composable (Modifier) -> Unit = { modifier ->
        BrewCharts(
            model = chart,
            visibleCharts = visibleCharts,
            modifier = modifier,
            combinedOnPhone = true,
            scrollPanels = false,
        )
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 6.dp else 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!active) {
                TextButton(onClick = onBackToLibrary) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text(stringResource(Res.string.dashboard_recipe_library), modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (active) {
                Text(
                    stringResource(Res.string.dashboard_brewing_state),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (expanded) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.weight(.29f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { setup() }
                BoxWithConstraints(Modifier.weight(.71f).fillMaxHeight()) {
                    val chartHeight = maxOf(maxHeight - 104.dp, (visibleCharts.size.coerceAtLeast(1) * 125 + 24).dp)
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        readouts()
                        charts(Modifier.fillMaxWidth().height(chartHeight))
                    }
                }
            }
        } else if (active) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                readouts()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    charts(Modifier.fillMaxSize())
                }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!active) setup()
                readouts()
                charts(Modifier.fillMaxWidth().height((visibleCharts.size.coerceAtLeast(1) * 170 + 24).dp))
            }
        }
        blocker?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canConnectScale) {
                    TextButton(onClick = {
                        if (liveRecipe == null) onEvent(DeviceDashboardEvent.ConnectedDevicesClicked)
                        else {
                            onRescanScales()
                            scalePickerOpen = true
                        }
                    }) { Text(stringResource(Res.string.dashboard_connect_scale)) }
                }
            }
        }
        if (liveActive) {
            Button(
                onClick = onStopLiveRecipe,
                enabled = !liveStopPending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.device_dashboard_stop_brew)) }
        } else {
            DashboardBrewButton(
                state,
                onEvent,
                Modifier.fillMaxWidth().height(64.dp),
                targetWeightGrams = nativeTargetGrams,
                onStartLiveRecipe = onStart.takeIf { liveRecipe != null },
                startEnabled = blocker == null,
            )
        }
    }

    if (scalePickerOpen && !scaleConnected) {
        AlertDialog(
            onDismissRequest = { scalePickerOpen = false },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (connectingScale) Text(stringResource(Res.string.dashboard_connecting_scale))
                    else if (scaleDevices.isEmpty()) Text(stringResource(Res.string.dashboard_no_scale_found))
                    scaleDevices.forEach { scale ->
                        OutlinedButton(onClick = { onConnectScale(scale) }, enabled = !connectingScale,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(scale.name) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = onRescanScales) { Text(stringResource(Res.string.dashboard_scan)) } },
            confirmButton = { TextButton(onClick = { scalePickerOpen = false }) { Text(stringResource(Res.string.dashboard_done)) } },
        )
    }
    androidx.compose.runtime.LaunchedEffect(scaleConnected) {
        if (scaleConnected) scalePickerOpen = false
    }
}

private fun liveSamplesLast(chart: BrewChartModel) = chart.measurements.maxByOrNull { it.key }

@Composable
private fun RecipeDetails(
    profile: ProfileListViewState.Profile?,
    recipe: LiveRecipeDocument?,
    liveIssues: List<String>,
) {
    if (profile == null && recipe == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        profile?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        val phases = (profile?.program as? BrewProgram.Phases)?.phases.orEmpty()
        phases.forEachIndexed { index, phase ->
            val target = when (val control = phase.control) {
                is PhaseControl.Pressure -> "${control.bar.toOneDecimal()} bar"
                is PhaseControl.Flow -> "${control.millilitresPerSecond.toOneDecimal()} ml/s"
                PhaseControl.PumpPause -> stringResource(Res.string.dashboard_pause)
            }
            RecipePhaseDetail(phase.name.ifBlank { (index + 1).toString() }, target, phase.plannedDurationMillis())
        }
        recipe?.phases?.forEachIndexed { index, phase ->
            val unit = when (phase.control) {
                RecipeControl.Pressure -> "bar"
                RecipeControl.MachineFlow -> "ml/s"
                RecipeControl.CupFlow -> "g/s"
                RecipeControl.Pause -> ""
            }
            val target = when {
                phase.control == RecipeControl.Pause -> stringResource(Res.string.dashboard_pause)
                phase.target.source == RecipeValueSource.Fixed -> "${phase.target.value.toOneDecimal()} $unit"
                else -> "—"
            }
            RecipePhaseDetail(phase.name.ifBlank { (index + 1).toString() }, target, phase.maximumDurationMillis)
        }
        (liveIssues + recipe?.mappings.orEmpty()).distinct().forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RecipePhaseDetail(name: String, target: String, durationMillis: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text("$target · ${(durationMillis / 1_000f).toOneDecimal()} s",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
    }
}

@Composable
private fun DoseRatioControls(
    settings: ShotRecipeSettings,
    referenceGrams: Float,
    editable: Boolean,
    onChange: (ShotRecipeSettings) -> Unit,
    modifier: Modifier,
) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingAdjuster(stringResource(Res.string.dashboard_dose), "${settings.doseGrams.toOneDecimal()} g", editable,
                onMinus = { onChange(settings.changeDose(-0.1f)) }, onPlus = { onChange(settings.changeDose(0.1f)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                listOf(15f, 18f).forEach { dose ->
                    FilterChip(
                        selected = settings.doseGrams == dose,
                        enabled = editable,
                        onClick = { onChange(settings.withDose(dose)) },
                        label = { Text("${dose.toInt()} g") },
                    )
                }
            }
            SettingAdjuster(stringResource(Res.string.dashboard_ratio), "1:${settings.ratio.toOneDecimal()}", editable,
                onMinus = { onChange(settings.changeRatio(-0.1f)) }, onPlus = { onChange(settings.changeRatio(0.1f)) })
            SettingAdjuster(stringResource(Res.string.dashboard_reference_yield),
                "${referenceGrams.toOneDecimal()} g", editable,
                onMinus = { onChange(settings.changeTarget(-1f)) }, onPlus = { onChange(settings.changeTarget(1f)) })
        }
    }
}

@Composable
private fun SettingAdjuster(label: String, value: String, editable: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val decrease = stringResource(Res.string.dashboard_decrease, label)
            val increase = stringResource(Res.string.dashboard_increase, label)
            OutlinedIconButton(onClick = onMinus, enabled = editable, modifier = Modifier.size(48.dp).semantics { contentDescription = decrease }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            OutlinedIconButton(onClick = onPlus, enabled = editable, modifier = Modifier.size(48.dp).semantics { contentDescription = increase }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private fun liveRecipeChartModel(recipe: LiveRecipeDocument, liveSamples: List<LiveTestSample>, active: Boolean): BrewChartModel {
    val measurements = liveSamples.associate { sample ->
        sample.elapsedMillis / 1_000f to ChartData(
            pressure = sample.pressureBar ?: 0f,
            weight = sample.weightGrams ?: 0f,
            weightPerSecond = sample.derivedCupFlowGramsPerSecond ?: sample.reportedCupFlowGramsPerSecond ?: 0f,
            volume = 0f,
            volumePerSecond = sample.machineFlowMlPerSecond ?: 0f,
        )
    }
    var elapsedMillis = 0L
    val targets = linkedMapOf<Float, ChartData>()
    val boundaries = mutableListOf<ChartPhaseBoundary>()
    recipe.phases.forEachIndexed { index, phase ->
        val atSeconds = elapsedMillis / 1_000f
        val fixedTarget = phase.target.takeIf { it.source == RecipeValueSource.Fixed }?.value ?: 0f
        targets[atSeconds] = when (phase.control) {
            RecipeControl.Pressure -> ChartData(fixedTarget, 0f, 0f, 0f, 0f)
            RecipeControl.MachineFlow -> ChartData(0f, 0f, 0f, 0f, fixedTarget)
            RecipeControl.CupFlow -> ChartData(0f, 0f, fixedTarget, 0f, 0f)
            RecipeControl.Pause -> ChartData(0f, 0f, 0f, 0f, 0f)
        }
        boundaries += ChartPhaseBoundary(atSeconds.toDouble(), index + 1, phase.name)
        elapsedMillis += phase.maximumDurationMillis
    }
    targets[elapsedMillis / 1_000f] = targets.values.lastOrNull() ?: ChartData(0f, 0f, 0f, 0f, 0f)
    return BrewChartModel(measurements, targets, boundaries, followMeasurements = active)
}

private fun Float.toOneDecimal() = ((kotlin.math.round(this * 10f) / 10f).let { if (it == 0f) 0f else it }).toString()

private const val LibraryPage = 0
private const val BrewPage = 1
private val PhoneStandClearance = 24.dp
