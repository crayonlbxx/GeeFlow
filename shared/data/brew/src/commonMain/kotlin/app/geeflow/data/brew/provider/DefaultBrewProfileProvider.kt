@file:Suppress("MagicNumber", "LongMethod")

package app.geeflow.data.brew.provider

import app.geeflow.data.brew.model.AdaptiveFlowProbe
import app.geeflow.data.brew.model.BrewMetric
import app.geeflow.data.brew.model.BrewPhase
import app.geeflow.data.brew.model.BrewProfile
import app.geeflow.data.brew.model.BrewProgram
import app.geeflow.data.brew.model.Condition
import app.geeflow.data.brew.model.ExitCondition
import app.geeflow.data.brew.model.PhaseControl
import app.geeflow.data.brew.model.PhaseRamp
import app.geeflow.data.brew.model.ProfileStep
import app.geeflow.data.brew.model.RampStart
import app.geeflow.data.brew.model.RampStyle
import app.geeflow.data.brew.model.ThresholdComparison
import org.koin.core.annotation.Factory

@Factory
class DefaultBrewProfileProvider {
    fun getDefaultProfiles(userId: Long): List<BrewProfile> = templates.map { it.copy(userId = userId) }
    fun getScaleFreeStarterProfiles(userId: Long): List<BrewProfile> =
        scaleFreeStarterTemplates.map { it.copy(userId = userId) }

    private val scaleFreeStarterTemplates = listOf(
        BrewProfile(
            userId = 0,
            name = "Classic 9 Bar + Preinfusion (No Scale)",
            description = "Generic timed LITA BA starting point: 3 bar preinfusion for 8 s, then 9 bar for up to 30 s. " +
                "Stops at 40 mL pumped water; that is not cup yield. No scale is needed. Set temperature separately " +
                "and adjust grind and dose to taste. This is a configurable baseline, not a copied source profile.",
            finishCondition = Condition.Volume(40f),
            program = BrewProgram.Phases(
                listOf(
                    timedPhase("preinfusion", "3 bar preinfusion", PhaseControl.Pressure(3f), 8),
                    timedPhase("extraction", "9 bar extraction", PhaseControl.Pressure(9f), 30),
                ),
            ),
            position = 0,
        ),
        BrewProfile(
            userId = 0,
            name = "Adaptive v2 (LITA, No Scale)",
            description = "GeeFlow adaptation of Decent's Adaptive v2: after preinfusion, ramp flow upward for 7 s, " +
                "learn the fastest measured flow while pump pressure stays below 8.8 bar, then hold that rate and " +
                "back off if pressure reaches the ceiling. Decent describes a 9 bar ceiling; 8.8 bar leaves telemetry " +
                "headroom, and LITA pump pressure is not Decent coffee pressure. The linked guide does not specify " +
                "preinfusion settings, so this profile uses GeeFlow's 3.5 mL/s / 4 bar / 15 s baseline. It stops at " +
                "60 mL pumped water, not cup yield. No scale is required. Source: https://decentespresso.com/first_decent_espresso",
            finishCondition = Condition.Volume(60f),
            program = BrewProgram.Phases(
                listOf(
                    BrewPhase(
                        id = "preinfusion",
                        name = "Flow preinfusion",
                        control = PhaseControl.Flow(3.5f),
                        maximumDurationMillis = 15_000,
                        exitConditions = listOf(
                            ExitCondition(BrewMetric.PumpPressure, ThresholdComparison.StrictlyAbove, 4f),
                        ),
                    ),
                    BrewPhase(
                        id = "adaptive-extraction",
                        name = "7 s adaptive flow probe",
                        control = PhaseControl.Flow(12f),
                        maximumDurationMillis = 60_000,
                        ramp = PhaseRamp(RampStyle.Linear, 7_000, RampStart.PreviousTarget),
                        adaptiveFlowProbe = AdaptiveFlowProbe(
                            samplingDurationMillis = 7_000,
                            pressureCeilingBar = 8.8f,
                            flowBackoffMillilitresPerSecond = 0.1f,
                        ),
                    ),
                ),
            ),
            position = 1,
        ),
        BrewProfile(
            userId = 0,
            name = "Damian's LRv2 (LITA, No Scale)",
            description = "Telemetry-driven GeeFlow adaptation of Damian's LRv2: 2 bar fill, 3 bar infusion for up to " +
                "12 s, an 8 s rise to 9 bar, then a gradual pressure decline. If flow exceeds 2.8 mL/s during the " +
                "decline, it moves to 3 bar and then hands control to a 2.5 mL/s flow target. That handoff follows " +
                "LITA pump-flow telemetry instead of a fixed shot time and requires a flow-profiling LITA model. " +
                "It replaces the source's 42 g cup-weight stop with a 60 mL pumped-water stop; per-stage temperature " +
                "changes are not included. LITA pump pressure is not equivalent to Decent coffee pressure. " +
                "No scale is required. Source: https://github.com/decentespresso/de1app/blob/main/de1plus/profiles/Damian%27s%20LRv2.tcl",
            finishCondition = Condition.Volume(60f),
            program = BrewProgram.Phases(
                listOf(
                    timedPhase("fill-start", "2 bar fill", PhaseControl.Pressure(2f), 2),
                    BrewPhase(
                        id = "fill-until-wet",
                        name = "2 bar fill until pressure rises",
                        control = PhaseControl.Pressure(2f),
                        maximumDurationMillis = 25_000,
                        exitConditions = listOf(
                            ExitCondition(BrewMetric.PumpPressure, ThresholdComparison.StrictlyAbove, 1.5f),
                        ),
                    ),
                    BrewPhase(
                        id = "infusion",
                        name = "3 bar infusion",
                        control = PhaseControl.Pressure(3f),
                        maximumDurationMillis = 12_000,
                    ),
                    BrewPhase(
                        id = "pressure-rise",
                        name = "8 s rise to 9 bar",
                        control = PhaseControl.Pressure(9f),
                        maximumDurationMillis = 8_000,
                        ramp = PhaseRamp(RampStyle.Linear, 8_000, RampStart.PreviousTarget),
                    ),
                    BrewPhase(
                        id = "pressure-decline",
                        name = "Gradual 9 to 3 bar decline",
                        control = PhaseControl.Pressure(3f),
                        maximumDurationMillis = 55_000,
                        ramp = PhaseRamp(RampStyle.Linear, 55_000, RampStart.PreviousTarget),
                        exitConditions = listOf(
                            ExitCondition(BrewMetric.PumpFlow, ThresholdComparison.StrictlyAbove, 2.8f),
                        ),
                    ),
                    BrewPhase(
                        id = "pressure-hold",
                        name = "3 bar hold until flow rises",
                        control = PhaseControl.Pressure(3f),
                        maximumDurationMillis = 127_000,
                        exitConditions = listOf(
                            ExitCondition(BrewMetric.PumpFlow, ThresholdComparison.StrictlyAbove, 2.8f),
                        ),
                    ),
                    BrewPhase(
                        id = "flow-limit",
                        name = "2.5 mL/s flow limit",
                        control = PhaseControl.Flow(2.5f),
                        maximumDurationMillis = 127_000,
                    ),
                ),
            ),
            position = 2,
        ),
        BrewProfile(
            userId = 0,
            name = "LRv3-inspired 9 Bar Taper (No Scale)",
            description = "Timed, native LITA BA adaptation based on the source-exact Decent community capture of Damian's LRv3. " +
                "The captured source uses 2 bar fill (up to 25 s), 3 bar infusion (up to 12 s), 9 bar rise/hold, then a " +
                "smooth 9 to 5.5 bar decline over 35 s with pressure/flow exits and a 42 g cup-weight stop. " +
                "This LITA version uses fixed 6 s / 8 s preinfusion, a 4 s rise and 12 s 9 bar hold, then five 7 s " +
                "pressure steps from 9 to 5.5 bar. It omits source exits and temperature changes, replaces cup weight with " +
                "a 65 mL pumped-water stop, and controls pump pressure rather than Decent coffee pressure. These are not " +
                "equivalent measurements; no scale is required. Source capture: https://decentespresso.com/f/0/Damian%27s%20LRv3.tcl",
            finishCondition = Condition.Volume(65f),
            program = BrewProgram.Phases(
                listOf(
                    timedPhase("fill", "2 bar fill", PhaseControl.Pressure(2f), 6),
                    timedPhase("infuse", "3 bar infusion", PhaseControl.Pressure(3f), 8),
                    timedPhase("pressure-rise", "9 bar rise", PhaseControl.Pressure(9f), 4),
                    timedPhase("pressure-hold", "9 bar hold", PhaseControl.Pressure(9f), 12),
                    timedPhase("taper-1", "Pressure taper 1", PhaseControl.Pressure(9f), 7),
                    timedPhase("taper-2", "Pressure taper 2", PhaseControl.Pressure(8f), 7),
                    timedPhase("taper-3", "Pressure taper 3", PhaseControl.Pressure(7f), 7),
                    timedPhase("taper-4", "Pressure taper 4", PhaseControl.Pressure(6f), 7),
                    timedPhase("taper-5", "Pressure taper 5", PhaseControl.Pressure(5.5f), 7),
                ),
            ),
            position = 3,
        ),
        BrewProfile(
            userId = 0,
            name = "Hendon 6 Bar Decline (No Scale)",
            description = "Timed, native LITA BA adaptation based on the source-exact Decent community capture of Hendon's " +
                "6-bar turbo decline. The captured source uses 3.5 bar fill, 5.5 mL/s preinfusion, a 6 bar rise/hold, " +
                "a smooth 6 to 4 bar decline over 15 s, a 4 bar stage, then a 3.3 mL/s flow cap; it also has pressure/flow " +
                "exits and a 53.6 g cup-weight stop. This LITA version uses fixed 5 s / 8 s fill and preinfusion, an 8 s " +
                "6 bar stage, three 5 s pressure steps from 5.3 to 4 bar, then timed 4 bar and 3.3 mL/s stages. It omits " +
                "source exits and temperature changes, replaces cup weight with a 65 mL pumped-water stop, and uses pump " +
                "pressure; it is an adaptation, not an exact reproduction. No scale is required. Source capture: " +
                "https://decentespresso.com/f/0/hendon-turbo-6b-pressure-decline-by-jan-f.tcl",
            finishCondition = Condition.Volume(65f),
            program = BrewProgram.Phases(
                listOf(
                    timedPhase("fill", "Fill", PhaseControl.Pressure(3.5f), 5),
                    timedPhase("preinfusion", "Preinfusion", PhaseControl.Flow(5.5f), 8),
                    timedPhase("pressure-hold", "6 bar extraction", PhaseControl.Pressure(6f), 8),
                    timedPhase("taper-1", "Pressure taper 1", PhaseControl.Pressure(5.3f), 5),
                    timedPhase("taper-2", "Pressure taper 2", PhaseControl.Pressure(4.7f), 5),
                    timedPhase("taper-3", "Pressure taper 3", PhaseControl.Pressure(4f), 5),
                    timedPhase("low-pressure", "Low pressure", PhaseControl.Pressure(4f), 10),
                    timedPhase("flow-cap", "Flow cap", PhaseControl.Flow(3.3f), 10),
                ),
            ),
            position = 4,
        ),
    )

    private val templates = scaleFreeStarterTemplates + listOf(
        BrewProfile(
            userId = 0,
            name = "Cremina",
            description = "Lever-style: gentle preinfusion, 9 bar peak with declining pressure",
            finishCondition = Condition.Weight(38f),
            steps = listOf(
                ProfileStep.Pressure(time = 12, pressure = 1.5f),
                ProfileStep.Pressure(time = 8, pressure = 9f),
                ProfileStep.Pressure(time = 5, pressure = 8f),
                ProfileStep.Pressure(time = 5, pressure = 7f),
                ProfileStep.Pressure(time = 5, pressure = 6f),
                ProfileStep.Pressure(time = 5, pressure = 5f),
                ProfileStep.Pressure(time = 5, pressure = 4f),
                ProfileStep.Pressure(time = 5, pressure = 3f),
            ),
            position = 5,
        ),
        BrewProfile(
            userId = 0,
            name = "Blooming Espresso",
            description = "Flow preinfusion, 30s bloom pause, then 2.2 ml/s extraction",
            finishCondition = Condition.Weight(42f),
            steps = listOf(
                ProfileStep.Flow(time = 10, flow = 4f),
                ProfileStep.Wait(time = 30),
                ProfileStep.Flow(time = 25, flow = 2.2f),
            ),
            position = 6,
        ),
        BrewProfile(
            userId = 0,
            name = "Slayer Style",
            description = "Long low-flow preinfusion (1.5 ml/s) followed by 9 bar extraction",
            finishCondition = Condition.Weight(38f),
            steps = listOf(
                ProfileStep.Flow(time = 25, flow = 1.5f),
                ProfileStep.Pressure(time = 15, pressure = 9f),
                ProfileStep.Pressure(time = 15, pressure = 6f),
            ),
            position = 7,
        ),
        BrewProfile(
            userId = 0,
            name = "Gentle & Sweet",
            description = "Low 6 bar peak declining to 4 bar, forgiving and smooth",
            finishCondition = Condition.Weight(36f),
            steps = listOf(
                ProfileStep.Pressure(time = 8, pressure = 2.5f),
                ProfileStep.Pressure(time = 12, pressure = 6f),
                ProfileStep.Pressure(time = 15, pressure = 4f),
            ),
            position = 8,
        ),
        BrewProfile(
            userId = 0,
            name = "Zuppa",
            description = "SOUP: low-pressure, high-flow extraction for sweetness & clarity, 20g in 60g out",
            finishCondition = Condition.Weight(60f),
            steps = listOf(
                ProfileStep.Flow(time = 5, flow = 6f),
                ProfileStep.Wait(time = 2),
                ProfileStep.Flow(time = 13, flow = 6f),
            ),
            position = 9,
        ),
        BrewProfile(
            userId = 0,
            name = "Turbo Shot",
            description = "Fast 6 bar extraction for coarse grind, 15s shot",
            finishCondition = Condition.Weight(42f),
            steps = listOf(
                ProfileStep.Pressure(time = 3, pressure = 2.5f),
                ProfileStep.Pressure(time = 15, pressure = 6f),
            ),
            position = 10,
        ),
        BrewProfile(
            userId = 0,
            name = "Londinium",
            description = "Spring lever: 3 bar infusion, 9.5 bar peak, declining to 4.5 bar",
            finishCondition = Condition.Weight(38f),
            steps = listOf(
                ProfileStep.Pressure(time = 6, pressure = 3f),
                ProfileStep.Pressure(time = 8, pressure = 9.5f),
                ProfileStep.Pressure(time = 8, pressure = 7.5f),
                ProfileStep.Pressure(time = 8, pressure = 6f),
                ProfileStep.Pressure(time = 8, pressure = 4.5f),
            ),
            position = 11,
        ),
        BrewProfile(
            userId = 0,
            name = "Rao Allongé",
            description = "High flow rate (4.5 ml/s) for light roasts, 90g out",
            finishCondition = Condition.Weight(90f),
            steps = listOf(
                ProfileStep.Flow(time = 6, flow = 3f),
                ProfileStep.Flow(time = 25, flow = 4.5f),
            ),
            position = 12,
        ),
        BrewProfile(
            userId = 0,
            name = "Filter 2.0",
            description = "Batch filter style: constant 3 ml/s flow through paper filter",
            finishCondition = Condition.Weight(100f),
            steps = listOf(
                ProfileStep.Flow(time = 40, flow = 3f),
            ),
            position = 13,
        ),
        BrewProfile(
            userId = 0,
            name = "Zuppa Lungo",
            description = "SOUP Lungo: extended low-pressure extraction for filter-like cup, 20g in 110g out",
            finishCondition = Condition.Weight(110f),
            steps = listOf(
                ProfileStep.Flow(time = 5, flow = 6f),
                ProfileStep.Wait(time = 2),
                ProfileStep.Flow(time = 30, flow = 5f),
            ),
            position = 14,
        ),
        BrewProfile(
            userId = 0,
            name = "Espresso Lungo",
            description = "Long extraction for high yield, 80g out",
            finishCondition = Condition.Weight(80f),
            steps = listOf(
                ProfileStep.Pressure(time = 6, pressure = 3f),
                ProfileStep.Pressure(time = 25, pressure = 9f),
                ProfileStep.Pressure(time = 15, pressure = 6f),
            ),
            position = 15,
        ),
        BrewProfile(
            userId = 0,
            name = "Disco Italiano",
            description = "Traditional 9 bar extraction, 18g in 36g out",
            finishCondition = Condition.Weight(36f),
            steps = listOf(
                ProfileStep.Pressure(time = 30, pressure = 9f),
            ),
            position = 16,
        ),
    )
}

private fun timedPhase(id: String, name: String, control: PhaseControl, seconds: Int) = BrewPhase(
    id = id,
    name = name,
    control = control,
    maximumDurationMillis = seconds * 1000L,
)
