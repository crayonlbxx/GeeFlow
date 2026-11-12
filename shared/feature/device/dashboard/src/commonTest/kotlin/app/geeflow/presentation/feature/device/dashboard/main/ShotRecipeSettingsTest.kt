package app.geeflow.presentation.feature.device.dashboard.main

import kotlin.test.Test
import kotlin.test.assertEquals

class ShotRecipeSettingsTest {
    @Test
    fun yieldFollowsDoseAndRatio() {
        val settings = ShotRecipeSettings.forRecipe(18f, 36f)

        assertEquals(36.2f, settings.changeDose(0.1f).targetGrams)
        assertEquals(37.8f, settings.changeRatio(0.1f).targetGrams)
    }

    @Test
    fun editingYieldRetainsItsPrecisionAndUpdatesTheRatio() {
        val settings = ShotRecipeSettings.forRecipe(18f, 36f).changeTarget(1f)

        assertEquals(37f, settings.targetGrams)
        assertEquals(37f / 18f, settings.ratio)
        assertEquals(30.8f, settings.withDose(15f).targetGrams)
    }
}
