package app.geeflow.presentation.feature.device.dashboard.main

import kotlin.math.round

data class ShotRecipeSettings(
    val doseGrams: Float,
    val ratio: Float,
) {
    val targetGrams: Float get() = (doseGrams * ratio).roundToTenth().coerceAtLeast(0.1f)
    val machineStopGrams: Float get() = targetGrams.toInt().coerceAtLeast(1).toFloat()

    fun changeDose(delta: Float): ShotRecipeSettings = withDose(doseGrams + delta)

    fun withDose(dose: Float): ShotRecipeSettings = copy(doseGrams = dose.coerceAtLeast(0.1f).roundToTenth())

    fun changeRatio(delta: Float): ShotRecipeSettings {
        val ratio = (ratio + delta).coerceAtLeast(0.1f)
        return copy(ratio = ratio)
    }

    fun changeTarget(delta: Float): ShotRecipeSettings {
        val target = (targetGrams + delta).coerceAtLeast(0.1f).roundToTenth()
        return copy(ratio = target / doseGrams)
    }

    companion object {
        fun forRecipe(doseGrams: Float, targetGrams: Float): ShotRecipeSettings {
            val dose = doseGrams.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_DOSE_GRAMS
            val target = targetGrams.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_TARGET_GRAMS
            return ShotRecipeSettings(dose, target / dose)
        }

        const val DEFAULT_DOSE_GRAMS = 18f
        const val DEFAULT_TARGET_GRAMS = 36f
    }
}

private fun Float.roundToTenth() = round(this * 10f) / 10f
