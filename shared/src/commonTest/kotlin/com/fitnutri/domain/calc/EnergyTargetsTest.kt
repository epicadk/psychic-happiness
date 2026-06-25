package com.fitnutri.domain.calc

import com.fitnutri.domain.model.Goal
import com.fitnutri.domain.model.Sex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnergyTargetsTest {

    @Test
    fun bmr_matchesMifflinStJeor_male() {
        // 80kg, 180cm, 30y male: 10*80 + 6.25*180 - 5*30 + 5 = 1780
        val bmr = EnergyTargets.basalMetabolicRate(Sex.MALE, 80.0, 180.0, 30)
        assertEquals(1780.0, bmr, 0.001)
    }

    @Test
    fun bmr_matchesMifflinStJeor_female() {
        // 65kg, 165cm, 30y female: 650 + 1031.25 - 150 - 161 = 1370.25
        val bmr = EnergyTargets.basalMetabolicRate(Sex.FEMALE, 65.0, 165.0, 30)
        assertEquals(1370.25, bmr, 0.001)
    }

    @Test
    fun tdee_appliesActivityFactor() {
        val tdee = EnergyTargets.totalDailyEnergyExpenditure(
            Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE,
        )
        assertEquals(1780.0 * 1.55, tdee, 0.001)
    }

    @Test
    fun loseFat_appliesDeficit_andHigherProtein() {
        val maintain = EnergyTargets.suggestTargets(
            Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE, Goal.MAINTAIN,
        )
        val cut = EnergyTargets.suggestTargets(
            Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE, Goal.LOSE_FAT,
        )
        assertEquals(maintain.calories!! - 500, cut.calories)
        // 2.0 g/kg * 80kg = 160g protein on a cut.
        assertEquals(160, cut.proteinG)
    }

    @Test
    fun macros_roughlyReconcileWithCalories() {
        val t = EnergyTargets.suggestTargets(
            Sex.FEMALE, 65.0, 165.0, 28, ActivityLevel.LIGHT, Goal.MAINTAIN,
        )
        val fromMacros = t.proteinG!! * 4 + t.carbsG!! * 4 + t.fatG!! * 9
        // Allow rounding slack across the three macro roundings.
        assertTrue(abs(fromMacros - t.calories!!) <= 15, "macros $fromMacros vs ${t.calories}")
    }

    @Test
    fun calorieFloor_preventsUnsafeDeficit() {
        val t = EnergyTargets.suggestTargets(
            Sex.FEMALE, 45.0, 150.0, 60, ActivityLevel.SEDENTARY, Goal.LOSE_FAT,
        )
        assertTrue(t.calories!! >= 1200, "calories ${t.calories} below floor")
    }
}
