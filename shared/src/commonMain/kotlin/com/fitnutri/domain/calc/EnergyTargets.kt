package com.fitnutri.domain.calc

import com.fitnutri.domain.model.Goal
import com.fitnutri.domain.model.MacroTargets
import com.fitnutri.domain.model.Sex
import kotlin.math.roundToInt

/**
 * Energy and macro target computation.
 *
 * Resolves the spec's open "goal model" decision in favour of *computing a
 * suggestion the user can override*: we estimate TDEE (Mifflin-St Jeor BMR x an
 * activity factor), apply a goal-based calorie delta, then split macros. The UI
 * presents these as editable defaults during onboarding — never as prescriptions.
 * No medical/clinical framing.
 */

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),        // little/no exercise
    LIGHT(1.375),          // 1-3 days/week
    MODERATE(1.55),        // 3-5 days/week
    ACTIVE(1.725),         // 6-7 days/week
    VERY_ACTIVE(1.9),      // hard daily training / physical job
}

object EnergyTargets {

    /** Mifflin-St Jeor basal metabolic rate (kcal/day). */
    fun basalMetabolicRate(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
    ): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
            // No reliable sex-neutral constant; average the two offsets.
            Sex.OTHER -> base - 78
        }
    }

    /** Total daily energy expenditure (kcal/day). */
    fun totalDailyEnergyExpenditure(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        activity: ActivityLevel,
    ): Double = basalMetabolicRate(sex, weightKg, heightCm, ageYears) * activity.factor

    /**
     * Suggested daily targets for a goal. Calorie delta is a moderate, widely-used
     * default (±~500 kcal ≈ ~0.45 kg/week). Macros: protein scaled to bodyweight,
     * fat as a share of calories, carbs as the remainder.
     */
    fun suggestTargets(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        activity: ActivityLevel,
        goal: Goal,
    ): MacroTargets {
        val tdee = totalDailyEnergyExpenditure(sex, weightKg, heightCm, ageYears, activity)

        val calories = when (goal) {
            Goal.LOSE_FAT -> tdee - 500
            Goal.GAIN_MUSCLE -> tdee + 300
            Goal.MAINTAIN -> tdee
        }.coerceAtLeast(1200.0) // floor: don't suggest unsafe deficits

        // Protein g/kg by goal; fat as a fraction of calories; carbs = remainder.
        val proteinPerKg = when (goal) {
            Goal.LOSE_FAT -> 2.0    // higher protein preserves lean mass in a deficit
            Goal.GAIN_MUSCLE -> 1.8
            Goal.MAINTAIN -> 1.6
        }
        val proteinG = proteinPerKg * weightKg
        val fatG = (calories * 0.25) / 9.0          // 25% of energy from fat, 9 kcal/g
        val carbsKcal = calories - proteinG * 4 - fatG * 9
        val carbsG = (carbsKcal / 4.0).coerceAtLeast(0.0)

        return MacroTargets(
            calories = calories.roundToInt(),
            proteinG = proteinG.roundToInt(),
            carbsG = carbsG.roundToInt(),
            fatG = fatG.roundToInt(),
        )
    }
}
