package app.geeflow.data.brew.provider

import app.geeflow.data.brew.model.BrewProgram
import app.geeflow.data.brew.model.Condition
import app.geeflow.data.brew.model.PhaseControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultBrewProfileProviderTest {
    private val provider = DefaultBrewProfileProvider()

    @Test
    fun `scale-free starters use source-declared pumped-water goals`() {
        val profiles = provider.getScaleFreeStarterProfiles(userId = 42)

        assertEquals(5, profiles.size)
        assertTrue(profiles.all { it.userId == 42L })
        assertTrue(profiles.all { it.finishCondition is Condition.Volume })
        assertEquals(
            listOf(40f, 60f, 60f, 65f, 65f),
            profiles.map { (it.finishCondition as Condition.Volume).target },
        )
    }

    @Test
    fun `classic 9 bar starter is a timed preinfusion baseline`() {
        val profile = provider.getScaleFreeStarterProfiles(1).first()
        val program = assertIs<BrewProgram.Phases>(profile.program)

        assertEquals("Classic 9 Bar + Preinfusion (No Scale)", profile.name)
        assertEquals(listOf(8_000L, 30_000L), program.phases.map { it.maximumDurationMillis })
        assertEquals(
            listOf(PhaseControl.Pressure(3f), PhaseControl.Pressure(9f)),
            program.phases.map { it.control },
        )
        assertTrue(profile.description.contains("not a copied source profile"))
        assertTrue(profile.description.contains("not cup yield"))
    }

    @Test
    fun `LRv3 adaptation retains a timed stepped 9 bar decline`() {
        val profile = provider.getScaleFreeStarterProfiles(1)[3]
        val program = assertIs<BrewProgram.Phases>(profile.program)
        assertEquals(
            "LRv3-inspired 9 Bar Taper (No Scale)",
            profile.name,
        )
        assertEquals(
            listOf(6_000L, 8_000L, 4_000L, 12_000L, 7_000L, 7_000L, 7_000L, 7_000L, 7_000L),
            program.phases.map { it.maximumDurationMillis },
        )
        assertEquals(
            listOf(9f, 8f, 7f, 6f, 5.5f),
            program.phases.takeLast(5).map { (it.control as PhaseControl.Pressure).bar },
        )
        assertTrue(profile.description.contains("source-exact Decent community capture"))
        assertTrue(profile.description.contains("smooth 9 to 5.5 bar decline over 35 s"))
        assertTrue(profile.description.contains("five 7 s pressure steps"))
        assertTrue(profile.description.contains("not equivalent measurements"))
        assertTrue(profile.description.contains("42 g cup-weight stop"))
    }

    @Test
    fun `Hendon adaptation retains the 6 to 4 bar decline and no-scale volume stop`() {
        val profile = provider.getScaleFreeStarterProfiles(1)[4]
        val program = assertIs<BrewProgram.Phases>(profile.program)

        assertEquals("Hendon 6 Bar Decline (No Scale)", profile.name)
        assertEquals(
            listOf(5_000L, 8_000L, 8_000L, 5_000L, 5_000L, 5_000L, 10_000L, 10_000L),
            program.phases.map { it.maximumDurationMillis },
        )
        assertEquals(PhaseControl.Flow(5.5f), program.phases[1].control)
        assertEquals(PhaseControl.Flow(3.3f), program.phases.last().control)
        assertEquals(
            listOf(5.3f, 4.7f, 4f),
            program.phases.slice(3..5).map { (it.control as PhaseControl.Pressure).bar },
        )
        assertTrue(profile.description.contains("source-exact Decent community capture"))
        assertTrue(profile.description.contains("smooth 6 to 4 bar decline over 15 s"))
        assertTrue(profile.description.contains("65 mL pumped-water stop"))
        assertTrue(profile.description.contains("not an exact reproduction"))
    }

    @Test
    fun `default profile restore includes the scale-free source profiles`() {
        val starters = provider.getScaleFreeStarterProfiles(7)
        val defaults = provider.getDefaultProfiles(7)

        assertTrue(starters.all { starter -> defaults.any { it.name == starter.name && it.userId == 7L } })
    }
}
