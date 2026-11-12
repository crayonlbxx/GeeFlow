@file:Suppress("TooManyFunctions", "LongParameterList")

package app.geeflow.domain.device

import app.geeflow.domain.device.recipe.*
import app.geeflow.data.device.BookooScaleReading
import app.geeflow.data.brew.model.BrewMetric
import app.geeflow.data.brew.model.PhaseControl
import app.geeflow.data.brew.model.PressureLocation
import app.geeflow.data.device.DeviceController
import app.geeflow.data.device.DeviceControllerProvider
import app.geeflow.data.device.LiveBrewSession
import app.geeflow.data.device.MachineDiagnosticEventSource
import app.geeflow.data.device.ScaleReadingSource
import app.geeflow.data.device.model.BrewTelemetry
import app.geeflow.data.device.model.DeviceState
import app.geeflow.data.device.model.withValue
import app.geeflow.domain.device.usecase.requireReady
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeSource

enum class LiveTestMode { PassiveCapture, ShadowControl, LiveControl }

data class LiveTestSample(
    val elapsedMillis: Long,
    val machineTelemetryAt: Instant?,
    val scaleReceivedAt: Instant?,
    val machineSampleAgeMillis: Long?,
    val scaleSampleAgeMillis: Long?,
    val phaseId: String?,
    val pressureBar: Float?,
    val machineFlowMlPerSecond: Float?,
    val weightGrams: Float?,
    val derivedCupFlowGramsPerSecond: Float?,
    val reportedCupFlowGramsPerSecond: Float?,
    val targetPressureBar: Float?,
    val requestedControlValue: Float? = null,
    val requestedControlUnit: String? = null,
    val requestedPressureBar: Float? = null,
    val commandedPressureBar: Float? = null,
    val constraints: String? = null,
)

data class LiveTestCaptureEvent(
    val type: String,
    val recordedAt: Instant,
    val elapsedMillis: Long?,
    val fields: Map<String, String>,
)

data class LiveTestRunState(
    val deviceId: Long? = null,
    val mode: LiveTestMode? = null,
    val active: Boolean = false,
    val stopPending: Boolean = false,
    val phaseId: String? = null,
    val fallbackStatus: String? = null,
    val settings: LiveControlSettings? = null,
    val recipeSnapshot: String? = null,
    val message: String? = null,
    val lastSample: LiveTestSample? = null,
    val samples: List<LiveTestSample> = emptyList(),
    val captureEvents: List<LiveTestCaptureEvent> = emptyList(),
)

/** Owns experimental live pressure shots independently of the test screen lifecycle. */
@Single
class LiveBrewTestCoordinator(
    private val provider: DeviceControllerProvider,
    private val scale: ScaleReadingSource,
    private val execution: ProfileExecutionCoordinator,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(LiveTestRunState())
    private var runJob: Job? = null
    val state: StateFlow<LiveTestRunState> = mutableState.asStateFlow()

    suspend fun startLive(
        deviceId: Long,
        recipe: LiveTestRecipe,
        settings: LiveControlSettings,
        executionScope: CoroutineScope = scope,
    ) = mutex.withLock {
        check(runJob?.isActive != true && !state.value.stopPending) { "A live test is already active" }
        val controller = provider.getController(deviceId)
        controller.requireReady(deviceId)
        execution.beginExternalControl(deviceId)
        try {
            recipe.document?.let { LiveRecipeCompiler.compile(it, controller.profilingCapabilities) }
            val machine = controller.deviceState.value
            check(machine.connectionStatus == DeviceState.ConnectionStatus.Connected) { "Connect to the machine first" }
            check(machine.brewStatus == DeviceState.BrewStatus.Idle) { "Machine must be idle" }
            check(machine.pressure != null && machine.flowRate != null) { "Machine pressure and flow readings are required" }
            check(machine.telemetryTime?.let { (Clock.System.now() - it).inWholeMilliseconds <= TELEMETRY_STALE_MILLIS } == true) {
                "Machine telemetry is stale. Wait for fresh pressure and flow readings."
            }
            val reading = scale.state.value.reading
            check(scale.state.value.connected && reading != null) { "Connect the BOOKOO scale first" }
            check((Clock.System.now() - reading.receivedAt).inWholeMilliseconds <= SCALE_STALE_MILLIS) {
                "Scale data is stale. Wait for a fresh reading."
            }
            check(kotlin.math.abs(reading.frame.weightGrams) <= START_WEIGHT_TOLERANCE_GRAMS) {
                "Tare the scale and place an empty cup before starting."
            }
            check(controller.profilingCapabilities.livePressure.containsKey(PressureLocation.Pump)) {
                "Pump pressure control is unavailable on this model"
            }
            mutableState.value = LiveTestRunState(
                deviceId = deviceId,
                mode = LiveTestMode.LiveControl,
                active = true,
                settings = settings,
                recipeSnapshot = recipe.document?.let(LiveRecipeJson::encode),
                captureEvents = initialCaptureEvents(deviceId, LiveTestMode.LiveControl, recipe, settings),
            )
            runJob = executionScope.launch { run(controller, recipe, settings, LiveTestMode.LiveControl) }
        } catch (error: Exception) {
            execution.endExternalControl(deviceId)
            throw error
        }
    }

    suspend fun startMonitoring(
        deviceId: Long,
        mode: LiveTestMode,
        recipe: LiveTestRecipe,
        settings: LiveControlSettings,
    ) = mutex.withLock {
        require(mode != LiveTestMode.LiveControl) { "Live control must start from the foreground service" }
        check(runJob?.isActive != true && !state.value.stopPending) { "A live test is already active" }
        val controller = provider.getController(deviceId)
        controller.requireReady(deviceId)
        val machine = controller.deviceState.value
        check(machine.connectionStatus == DeviceState.ConnectionStatus.Connected) { "Connect to the machine first" }
        check(machine.pressure != null && machine.flowRate != null) { "Machine pressure and flow readings are required" }
        check(machine.telemetryTime?.let { (Clock.System.now() - it).inWholeMilliseconds <= TELEMETRY_STALE_MILLIS } == true) {
            "Machine telemetry is stale. Wait for fresh pressure and flow readings."
        }
        val scaleState = scale.state.value
        check(scaleState.connected) { "Connect the BOOKOO scale first" }
        if (mode != LiveTestMode.PassiveCapture) {
            val reading = scaleState.reading
            check(reading != null) { "No valid BOOKOO weight packet yet. Passive capture can record unsupported packets." }
            check((Clock.System.now() - reading.receivedAt).inWholeMilliseconds <= SCALE_STALE_MILLIS) {
                "Scale data is stale. Wait for a fresh reading."
            }
            check(kotlin.math.abs(reading.frame.weightGrams) <= START_WEIGHT_TOLERANCE_GRAMS) {
                "Tare the scale and place an empty cup before starting."
            }
        }
        mutableState.value = LiveTestRunState(
            deviceId = deviceId,
            mode = mode,
            active = true,
            settings = settings,
            recipeSnapshot = recipe.document?.let(LiveRecipeJson::encode),
            captureEvents = initialCaptureEvents(deviceId, mode, recipe, settings),
        )
        runJob = scope.launch { run(controller, recipe, settings, mode) }
    }

    suspend fun stop(deviceId: Long): Boolean {
        val job = mutex.withLock {
            if (state.value.deviceId != deviceId || !state.value.active && !state.value.stopPending) return false
            runJob
        }
        job?.cancelAndJoin()
        return true
    }

    fun owns(deviceId: Long) =
        (state.value.active || state.value.stopPending) && state.value.deviceId == deviceId

    fun clearCompleted() {
        if (!state.value.active && !state.value.stopPending) mutableState.value = LiveTestRunState()
    }

    fun reportExportLocation(location: String) {
        mutableState.update { it.copy(message = "Capture exported to $location") }
    }

    private suspend fun run(
        controller: DeviceController,
        recipe: LiveTestRecipe,
        settings: LiveControlSettings,
        mode: LiveTestMode,
    ) {
        var session: LiveBrewSession? = null
        var scaleCaptureJob: Job? = null
        var machineCaptureJob: Job? = null
        var scaleSamplesJob: Job? = null
        var machineSamplesJob: Job? = null
        var reason = "user_stop"
        val startedAt = Clock.System.now()
        val clock = TimeSource.Monotonic.markNow()
        val genericEngine = recipe.document?.let { LiveRecipeEngine(LiveRecipeCompiler.compile(it)) }
        val commandGate = LiveTargetCommandGate(settings.commandIntervalMillis)
        try {
            val captureScope = CoroutineScope(currentCoroutineContext())
            scaleCaptureJob = captureScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                scale.packets.collect { packet ->
                    if (packet.receivedAt < startedAt) return@collect
                    captureEvent(
                        "scale_packet",
                        null,
                        mapOf(
                            "received_at" to packet.receivedAt.toString(),
                            "packet_hex" to packet.packetHex,
                            "decoded" to packet.decoded.toString(),
                        ),
                        packet.receivedAt,
                    )
                }
            }
            machineCaptureJob = (controller as? MachineDiagnosticEventSource)?.let { source ->
                captureScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    source.diagnosticEvents.collect { frame ->
                        captureEvent(
                            "machine_frame_${frame.name}",
                            null,
                            frame.fields + ("received_at" to frame.receivedAt.toString()),
                            frame.receivedAt,
                        )
                    }
                }
            }
            val initial = controller.deviceState.value
            val initialReading = scale.state.value.reading
            val scaleSamples = MutableStateFlow(
                initialReading?.let {
                    DerivedScaleSample(it, atMillis = -it.monotonicMark.elapsedNow().inWholeMilliseconds)
                },
            )
            val machineSamples = MutableStateFlow(initial to -(initial.telemetryTime?.let { (Clock.System.now() - it).inWholeMilliseconds } ?: 0L))
            scaleSamplesJob = captureScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                val estimator = CupFlowEstimator()
                var baseline = initialReading
                var guard = initialReading?.let { LiveScaleSampleGuard(it.frame) }
                var sequence = 0L
                scale.readings.collect { reading ->
                    if (baseline?.let { reading.monotonicMark < it.monotonicMark } == true) return@collect
                    if (baseline == null) {
                        baseline = reading
                        guard = LiveScaleSampleGuard(reading.frame)
                    }
                    val at = clock.elapsedNow().inWholeMilliseconds - reading.monotonicMark.elapsedNow().inWholeMilliseconds
                    val invalid = guard?.accept(reading.frame)
                    val flow = if (invalid == null) estimator.add(at, reading.frame.weightGrams) else { estimator.reset(); null }
                    scaleSamples.value = DerivedScaleSample(reading, flow, at, invalid, ++sequence)
                    captureEvent("scale_measurement", at, mapOf("weight_g" to reading.frame.weightGrams.toString(), "cup_flow_g_per_s" to flow.toString(), "sample_at_ms" to at.toString(), "invalid" to invalid?.name.orEmpty()), reading.receivedAt)
                }
            }
            machineSamplesJob = captureScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                var previous = initial.telemetryTime
                controller.deviceState.collect { sample ->
                    if (sample.telemetryTime != null && sample.telemetryTime != previous) {
                        previous = sample.telemetryTime
                        val at = clock.elapsedNow().inWholeMilliseconds
                        machineSamples.value = sample to at
                        captureEvent("machine_measurement", at, mapOf("pressure_bar" to sample.pressure.toString(), "flow_ml_per_s" to sample.flowRate.toString(), "sample_at_ms" to at.toString()))
                    }
                }
            }
            val oldTelemetry = initial.telemetryTime
            if (mode == LiveTestMode.LiveControl) {
                recipe.document?.settings?.temperatureC?.let { temperature ->
                    check(controller.deviceState.value.brewStatus == DeviceState.BrewStatus.Idle)
                    withTimeout(TELEMETRY_STALE_MILLIS.milliseconds) { controller.settings.setBrewTemperature(temperature) }
                    captureEvent("constant_temperature_set", 0, mapOf("boiler_setpoint_c" to temperature.toString()))
                }
                session = controller.profiles.openLiveSession(initialPressure(recipe))
            }
            val engine = LiveTestProgramEngine(recipe, settings)
            recipe.profile.finishCondition.let { condition ->
                require(condition is app.geeflow.data.brew.model.Condition.Weight)
            }
            var lastScaleSequence = -1L
            var scaleDataValid = true
            var freshScaleMeasurement = false
            var cupFlow: Float? = null
            var fallbackStartedAt: Long? = null
            var lastCapturedPhaseId: String? = null
            var lastSent: PhaseControl? = null
            var lastPressure = 0f
            var lastCommandElapsed = 0L
            var lastTelemetryTimestamp = oldTelemetry
            var telemetryFreshAt = TimeSource.Monotonic.markNow()

            if (mode == LiveTestMode.LiveControl) {
                withTimeout(TELEMETRY_STALE_MILLIS.milliseconds) {
                    controller.deviceState.first { it.telemetryTime != oldTelemetry && controller.profiles.isLiveSessionActive(it) }
                }
            }

            while (currentCoroutineContext().isActive) {
                val machine = controller.deviceState.value
                if (machine.connectionStatus != DeviceState.ConnectionStatus.Connected) error("Machine disconnected")
                controller.requireReady(requireNotNull(state.value.deviceId))
                if (mode == LiveTestMode.LiveControl && !controller.profiles.isLiveSessionActive(machine)) {
                    error("Machine control mode changed")
                }
                if (mode == LiveTestMode.LiveControl && machine.brewStatus == DeviceState.BrewStatus.Idle) {
                    reason = "machine_stop"
                    break
                }
                val elapsed = clock.elapsedNow().inWholeMilliseconds
                val telemetryAt = machine.telemetryTime
                if (telemetryAt != null && telemetryAt != lastTelemetryTimestamp) {
                    lastTelemetryTimestamp = telemetryAt
                    telemetryFreshAt = TimeSource.Monotonic.markNow()
                }
                if (telemetryFreshAt.elapsedNow().inWholeMilliseconds > TELEMETRY_STALE_MILLIS) error("Machine telemetry became stale")

                val derived = scaleSamples.value
                freshScaleMeasurement = derived != null && derived.sequence != lastScaleSequence && derived.flow != null
                if (derived != null) lastScaleSequence = derived.sequence
                val scaleReading = derived?.reading
                val scaleAge = scaleReading?.monotonicMark?.elapsedNow()?.inWholeMilliseconds
                cupFlow = derived?.flow
                if (derived?.invalid != null && scaleDataValid) {
                    scaleDataValid = false
                    mutableState.update { it.copy(fallbackStatus = "scale_invalid:${derived.invalid}") }
                    captureEvent("scale_invalid", elapsed, mapOf("reason" to derived.invalid.name))
                }
                if (scaleReading == null) {
                    if (mode != LiveTestMode.PassiveCapture) {
                        error("No valid BOOKOO weight packet. Passive capture can record unsupported packets.")
                    }
                    recordSample(elapsed, machine, null, null, null, null, null, null, null)
                    if (elapsed >= settings.shotDeadlineMillis) {
                        reason = "shot_deadline"
                        break
                    }
                    delay(EVALUATION_INTERVAL_MILLIS.milliseconds)
                    continue
                }
                val fallbackStart = scaleFallbackStartMillis(
                    fallbackStartedAt,
                    scaleDataValid,
                    scaleAge ?: Long.MAX_VALUE,
                    elapsed,
                    SCALE_STALE_MILLIS,
                ).takeUnless { mode == LiveTestMode.PassiveCapture }
                if (fallbackStart != null) {
                    if (fallbackStartedAt == null) {
                        fallbackStartedAt = fallbackStart
                        engine.resetFeedback(lastPressure)
                        genericEngine?.resetFeedback(lastPressure)
                        if (state.value.fallbackStatus == null) {
                            mutableState.update { current -> current.copy(fallbackStatus = "scale_stale") }
                        }
                        captureEvent(
                            "fallback_started",
                            elapsed,
                            mapOf("reason" to (state.value.fallbackStatus ?: "scale_stale")),
                        )
                    }
                    val fallback = scaleLossFallback(lastPressure, fallbackStart, settings.shotDeadlineMillis, settings.maximumPressureBar)
                    val fallbackUntil = minOf(fallbackStart + SCALE_FALLBACK_MILLIS, fallback.stopAtMillis)
                    if (elapsed >= fallbackUntil) {
                        reason = "scale_stale_fallback_timeout"
                        break
                    }
                    lastPressure = fallback.pressureBar
                    val fallbackTarget = controller.quantizePressureTarget(PhaseControl.Pressure(fallback.pressureBar))
                    val dispatchTarget = commandGate.offer(fallbackTarget, elapsed)
                    if (mode == LiveTestMode.LiveControl && dispatchTarget != null) {
                        val activeSession = requireNotNull(session)
                        val sent = transmitTarget(activeSession, dispatchTarget, elapsed)
                        lastSent = sent
                        if (sent is PhaseControl.Pressure) lastPressure = sent.bar
                        lastCommandElapsed = elapsed
                    }
                    recordSample(
                        elapsed, machine, scaleReading.frame.weightGrams, cupFlow,
                        scaleReading.frame.reportedFlowGramsPerSecond, lastPressure, state.value.phaseId,
                        scaleReading.receivedAt, scaleAge,
                    )
                    delay(EVALUATION_INTERVAL_MILLIS.milliseconds)
                    continue
                }

                val telemetry = BrewTelemetry(
                    buildMap {
                        machine.pressure?.let { put(BrewMetric.PumpPressure, it) }
                        machine.flowRate?.let { put(BrewMetric.PumpFlow, it) }
                        machine.volume?.let { put(BrewMetric.PumpedVolume, it) }
                        put(BrewMetric.CupWeight, scaleReading.frame.weightGrams)
                    },
                )
                val genericOutput = genericEngine?.let { runtime ->
                    val (sample, at) = machineSamples.value
                    runtime.tick(elapsed, RecipeMeasurements(buildMap {
                        sample.pressure?.let { put(RecipeMetric.Pressure, TimedRecipeValue(it, at)) }
                        sample.flowRate?.let { put(RecipeMetric.MachineFlow, TimedRecipeValue(it, at)) }
                        sample.volume?.let { put(RecipeMetric.Volume, TimedRecipeValue(it, at)) }
                        put(RecipeMetric.Weight, TimedRecipeValue(scaleReading.frame.weightGrams, derived.atMillis))
                        derived.flow?.let { put(RecipeMetric.CupFlow, TimedRecipeValue(it, derived.atMillis)) }
                    }))
                }
                genericOutput?.events?.forEach { captureEvent(it.type, elapsed, it.fields) }
                genericOutput?.let { output ->
                    captureEvent("recipe_control", elapsed, mapOf(
                        "phase_id" to output.phaseId.orEmpty(), "requested_target" to output.requestedTarget.toString(),
                        "target_unit" to output.targetUnit.toString(), "requested_pressure_bar" to output.requestedPressureBar.toString(),
                        "bounded_pressure_bar" to output.appliedPressureBar.toString(), "constraints" to output.constraints.joinToString(","),
                    ))
                }
                val evaluated = if (genericOutput != null) LiveTargetOutput(
                    genericOutput.appliedPressureBar?.let { PhaseControl.Pressure(it) }, genericOutput.phaseId, genericOutput.finish,
                ) else engine.tick(elapsed, telemetry, cupFlow, elapsed, freshScaleMeasurement)
                val output = if (mode == LiveTestMode.PassiveCapture) evaluated.copy(target = null) else evaluated
                if (output.phaseId != null && output.phaseId != lastCapturedPhaseId) {
                    lastCapturedPhaseId = output.phaseId
                    captureEvent("phase_transition", elapsed, mapOf("phase_id" to output.phaseId))
                }
                output.finish?.let { reason = it; break }
                val target = output.target?.let { control ->
                    val capped = if (control is PhaseControl.Pressure) {
                        control.copy(bar = control.bar.coerceAtMost(settings.maximumPressureBar))
                    } else control
                    controller.quantizePressureTarget(capped)
                }
                if (mode != LiveTestMode.PassiveCapture && target is PhaseControl.Pressure) {
                    captureEvent(
                        "controller_calculation",
                        elapsed,
                        mapOf(
                            "phase_id" to output.phaseId.orEmpty(),
                            "measured_cup_flow_g_per_s" to cupFlow?.toString().orEmpty(),
                            "proposed_pressure_bar" to target.bar.toString(),
                            "control_mode" to mode.name,
                        ),
                    )
                }
                if (mode == LiveTestMode.LiveControl && target != null) {
                    val activeSession = requireNotNull(session)
                    val writeNeeded = target != lastSent || activeSession.requiresContinuousUpdates
                    val dispatchTarget = if (writeNeeded) commandGate.offer(target, elapsed) else null
                    if (dispatchTarget != null) {
                        val limited = dispatchTarget.withSlewLimit(lastPressure, elapsed - lastCommandElapsed, settings)
                        val sent = transmitTarget(activeSession, limited, elapsed)
                        when (sent) {
                            is PhaseControl.Pressure -> lastPressure = sent.bar
                            PhaseControl.PumpPause -> lastPressure = 0f
                            is PhaseControl.Flow -> error("Live test recipe cannot switch to machine flow mode")
                        }
                        lastSent = sent
                        lastCommandElapsed = elapsed
                    }
                }
                recordSample(
                    elapsed,
                    machine,
                    scaleReading.frame.weightGrams,
                    cupFlow,
                    scaleReading.frame.reportedFlowGramsPerSecond,
                    (target as? PhaseControl.Pressure)?.bar,
                    output.phaseId,
                    requireNotNull(scaleReading).receivedAt,
                    scaleAge,
                    genericOutput?.requestedTarget,
                    genericOutput?.targetUnit?.name,
                    genericOutput?.requestedPressureBar,
                    if (mode == LiveTestMode.LiveControl) lastPressure else null,
                    genericOutput?.constraints?.joinToString(","),
                )
                if (elapsed >= settings.shotDeadlineMillis) {
                    reason = "shot_deadline"
                    break
                }
                delay(EVALUATION_INTERVAL_MILLIS.milliseconds)
            }
        } catch (error: CancellationException) {
            reason = error.message ?: "user_stop"
        } catch (error: Exception) {
            reason = error.message ?: "control_failure"
            Logger.e(error) { "Live brew test stopped" }
        } finally {
            commandGate.stop()
            genericEngine?.stop(reason)
            val stopped = if (mode == LiveTestMode.LiveControl) {
                withContext(NonCancellable) { confirmStop(controller, session, startedAt) }
            } else true
            withContext(NonCancellable) {
                scaleCaptureJob?.cancelAndJoin()
                machineCaptureJob?.cancelAndJoin()
                scaleSamplesJob?.cancelAndJoin()
                machineSamplesJob?.cancelAndJoin()
            }
            captureEvent("stop_confirmation", null, mapOf("confirmed" to stopped.toString()))
            captureEvent("run_ended", null, mapOf("reason" to reason, "stop_confirmed" to stopped.toString()))
            mutableState.update { it.copy(active = false, stopPending = !stopped, message = reason) }
            if (stopped) {
                state.value.deviceId?.let { execution.endExternalControl(it) }
            } else {
                retryStop(controller, session, startedAt)
            }
        }
    }

    private var commandSequence = 0L

    private fun initialCaptureEvents(
        deviceId: Long,
        mode: LiveTestMode,
        recipe: LiveTestRecipe,
        settings: LiveControlSettings,
    ): List<LiveTestCaptureEvent> = listOf(
        LiveTestCaptureEvent(
            type = "configuration",
            recordedAt = Clock.System.now(),
            elapsedMillis = 0,
            fields = mapOf(
                "schema_version" to "1",
                "device_id" to deviceId.toString(),
                "mode" to mode.name,
                "recipe_name" to recipe.profile.name,
                "recipe_snapshot" to (recipe.document?.let(LiveRecipeJson::encode).orEmpty()),
                "finish_weight_g" to (recipe.profile.finishCondition as app.geeflow.data.brew.model.Condition.Weight).target.toString(),
                "targets" to recipe.targets.entries.joinToString(";") { (id, target) -> "$id=$target" },
                "base_pressure_bar" to settings.basePressureBar.toString(),
                "pressure_ceiling_bar" to settings.maximumPressureBar.toString(),
                "slew_bar_per_second" to settings.slewBarPerSecond.toString(),
                "kp_bar_per_g_per_s" to settings.proportionalGainBarPerGramPerSecond.toString(),
                "ki_bar_per_g" to settings.integralGainBarPerGram.toString(),
                "command_interval_ms" to settings.commandIntervalMillis.toString(),
                "shot_deadline_ms" to settings.shotDeadlineMillis.toString(),
                "settings_validated_for_physical_control" to "false",
            ),
        ),
    )

    private fun captureEvent(
        type: String,
        elapsedMillis: Long?,
        fields: Map<String, String>,
        at: Instant = Clock.System.now(),
    ) {
        val event = LiveTestCaptureEvent(type, at, elapsedMillis, fields)
        mutableState.update { current ->
            current.copy(captureEvents = (current.captureEvents + event).takeLast(MAX_CAPTURE_EVENTS))
        }
    }

    private suspend fun transmitTarget(session: LiveBrewSession, target: PhaseControl, elapsedMillis: Long): PhaseControl {
        val id = ++commandSequence
        val enqueuedAt = Clock.System.now()
        captureEvent(
            "command_enqueued",
            elapsedMillis,
            mapOf("command_id" to id.toString(), "target" to target.toCaptureString()),
            enqueuedAt,
        )
        val dispatchedAt = Clock.System.now()
        val ackClock = TimeSource.Monotonic.markNow()
        captureEvent(
            "command_dispatched_to_session",
            elapsedMillis,
            mapOf("command_id" to id.toString(), "target" to target.toCaptureString(), "dispatched_at" to dispatchedAt.toString()),
            dispatchedAt,
        )
        try {
            val sent = withTimeout(TELEMETRY_STALE_MILLIS.milliseconds) { session.applyTarget(target) }
            captureEvent(
                "command_acknowledged",
                elapsedMillis,
                mapOf(
                    "command_id" to id.toString(),
                    "actual_target" to sent.toCaptureString(),
                    "acknowledgement_latency_ms" to ackClock.elapsedNow().inWholeMilliseconds.toString(),
                ),
            )
            return sent
        } catch (error: Exception) {
            captureEvent(
                "command_failed",
                elapsedMillis,
                mapOf("command_id" to id.toString(), "error" to (error.message ?: error::class.simpleName.orEmpty())),
            )
            throw error
        }
    }

    private fun recordSample(
        elapsed: Long,
        machine: DeviceState,
        weight: Float?,
        cupFlow: Float?,
        reportedFlow: Float?,
        target: Float?,
        phaseId: String?,
        scaleReceivedAt: Instant?,
        scaleAgeMillis: Long?,
        requestedControlValue: Float? = null,
        requestedControlUnit: String? = null,
        requestedPressureBar: Float? = null,
        commandedPressureBar: Float? = null,
        constraints: String? = null,
    ) {
        val machineAge = machine.telemetryTime?.let { (Clock.System.now() - it).inWholeMilliseconds }
        val sample = LiveTestSample(
            elapsed, machine.telemetryTime, scaleReceivedAt, machineAge, scaleAgeMillis,
            phaseId, machine.pressure, machine.flowRate, weight, cupFlow, reportedFlow, target,
            requestedControlValue, requestedControlUnit, requestedPressureBar, commandedPressureBar, constraints,
        )
        mutableState.update { current ->
            val fields = mapOf(
                "machine_telemetry_at" to machine.telemetryTime.toString(),
                "scale_received_at" to scaleReceivedAt.toString(),
                "machine_sample_age_ms" to machineAge?.toString().orEmpty(),
                "scale_sample_age_ms" to scaleAgeMillis?.toString().orEmpty(),
                "phase_id" to phaseId.orEmpty(),
                "pressure_bar" to machine.pressure?.toString().orEmpty(),
                "machine_flow_ml_per_s" to machine.flowRate?.toString().orEmpty(),
                "weight_g" to weight?.toString().orEmpty(),
                "derived_cup_flow_g_per_s" to cupFlow?.toString().orEmpty(),
                "reported_cup_flow_g_per_s" to reportedFlow?.toString().orEmpty(),
                "target_pressure_bar" to target?.toString().orEmpty(),
                "requested_control_value" to requestedControlValue?.toString().orEmpty(),
                "requested_control_unit" to requestedControlUnit.orEmpty(),
                "requested_pressure_bar" to requestedPressureBar?.toString().orEmpty(),
                "commanded_pressure_bar" to commandedPressureBar?.toString().orEmpty(),
                "constraints" to constraints.orEmpty(),
            )
            val event = LiveTestCaptureEvent("sample", Clock.System.now(), elapsed, fields)
            current.copy(
                phaseId = phaseId,
                lastSample = sample,
                samples = (current.samples + sample).takeLast(MAX_RETAINED_SAMPLES),
                captureEvents = (current.captureEvents + event).takeLast(MAX_CAPTURE_EVENTS),
            )
        }
    }

    private suspend fun confirmStop(controller: DeviceController, session: LiveBrewSession?, startedAt: Instant): Boolean = try {
        withTimeout(STOP_TIMEOUT_MILLIS.milliseconds) {
            if (controller.deviceState.value.brewStatus != DeviceState.BrewStatus.Idle) {
                session?.stop() ?: controller.profiles.stopLiveSession()
            }
            controller.deviceState.first {
                it.connectionStatus == DeviceState.ConnectionStatus.Connected &&
                    it.brewStatus == DeviceState.BrewStatus.Idle &&
                    it.statusTime?.let { stamp -> stamp >= startedAt } == true
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun retryStop(controller: DeviceController, session: LiveBrewSession?, startedAt: Instant) {
        scope.launch {
            while (currentCoroutineContext().isActive && state.value.stopPending) {
                delay(STOP_RETRY_MILLIS.milliseconds)
                if (confirmStop(controller, session, startedAt)) {
                    mutableState.update { it.copy(stopPending = false) }
                    state.value.deviceId?.let { execution.endExternalControl(it) }
                }
            }
        }
    }

    private fun initialPressure(recipe: LiveTestRecipe): PhaseControl.Pressure {
        if (recipe.document != null) return PhaseControl.Pressure(0f)
        val first = (recipe.profile.program as app.geeflow.data.brew.model.BrewProgram.Phases).phases.first()
        return when (val target = recipe.targets.getValue(first.id)) {
            is LiveControlTarget.Pressure -> PhaseControl.Pressure(target.bar, PressureLocation.Pump)
            is LiveControlTarget.CupFlow -> PhaseControl.Pressure(target.basePressureBar, PressureLocation.Pump)
            LiveControlTarget.Pause -> PhaseControl.Pressure(0f, PressureLocation.Pump)
        }
    }

    private fun DeviceController.quantizePressureTarget(target: PhaseControl): PhaseControl = when (target) {
        is PhaseControl.Pressure -> target.withValue(
            requireNotNull(profilingCapabilities.livePressure[target.location]).quantize(target.bar),
        )
        is PhaseControl.Flow -> error("Live test recipe cannot switch to machine flow mode")
        PhaseControl.PumpPause -> target
    }

    private fun PhaseControl.withSlewLimit(
        currentPressure: Float,
        elapsedSinceCommandMillis: Long,
        settings: LiveControlSettings,
    ): PhaseControl = when (this) {
        is PhaseControl.Pressure -> {
            val maxDelta = settings.slewBarPerSecond * elapsedSinceCommandMillis
                .coerceAtLeast(settings.commandIntervalMillis) / 1_000f
            copy(bar = currentPressure + (bar - currentPressure).coerceIn(-maxDelta, maxDelta))
        }
        is PhaseControl.Flow -> error("Live test recipe cannot switch to machine flow mode")
        PhaseControl.PumpPause -> this
    }

    private fun PhaseControl.toCaptureString() = when (this) {
        is PhaseControl.Pressure -> "pressure:${bar}bar@${location.name}"
        is PhaseControl.Flow -> "flow:${millilitresPerSecond}ml/s"
        PhaseControl.PumpPause -> "pump_pause"
    }

    private companion object {
        const val SCALE_STALE_MILLIS = 1_000L
        const val TELEMETRY_STALE_MILLIS = 2_000L
        const val SCALE_FALLBACK_MILLIS = 5_000L
        const val START_WEIGHT_TOLERANCE_GRAMS = 1f
        const val EVALUATION_INTERVAL_MILLIS = 50L
        const val STOP_TIMEOUT_MILLIS = 3_000L
        const val STOP_RETRY_MILLIS = 500L
        const val MAX_RETAINED_SAMPLES = 2_000
        const val MAX_CAPTURE_EVENTS = 12_000
    }
}

private data class DerivedScaleSample(val reading: BookooScaleReading, val flow: Float? = null, val atMillis: Long = 0, val invalid: ScaleInvalidReason? = null, val sequence: Long = 0)
