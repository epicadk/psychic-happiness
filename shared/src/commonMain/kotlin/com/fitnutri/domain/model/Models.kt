package com.fitnutri.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Domain models — the shared, platform-agnostic representation of the data
 * defined in the Postgres schema. These are pure Kotlin: no Supabase, no UI, no
 * platform types. Mapping to/from the DTOs the data layer (de)serializes lives in
 * the data module.
 */

enum class UnitSystem { METRIC, IMPERIAL }

enum class Sex { MALE, FEMALE, OTHER }

enum class Goal { LOSE_FAT, GAIN_MUSCLE, MAINTAIN }

/** Where a record came from. Pairs with [externalId] for idempotent re-imports. */
enum class Source { MANUAL, HEALTHKIT, HEALTH_CONNECT, STRAVA }

enum class FoodSource { OFF, NUTRITIONIX, USDA, AI_ESTIMATE, USER }

enum class FoodEntrySource { MANUAL, BARCODE, AI_PHOTO }

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

data class MacroTargets(
    val calories: Int? = null,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
)

data class Profile(
    val id: String,
    val displayName: String? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    val goal: Goal? = null,
    val targets: MacroTargets = MacroTargets(),
)

data class Workout(
    val id: String,
    val userId: String,
    val type: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val calories: Int? = null,
    val notes: String? = null,
    val source: Source = Source.MANUAL,
    val externalId: String? = null,
    val sets: List<ExerciseSet> = emptyList(),
)

data class ExerciseSet(
    val id: String,
    val workoutId: String,
    val userId: String,
    val exercise: String,
    val setIndex: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)

/** A cached, globally-shared food item. Macros are per [servingQty]/[servingUnit]. */
data class Food(
    val id: String,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val servingQty: Double? = null,
    val servingUnit: String? = null,
    val calories: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val source: FoodSource,
    val externalId: String? = null,
)

data class FoodEntry(
    val id: String,
    val userId: String,
    val foodId: String?,
    val loggedAt: Instant,
    val meal: Meal? = null,
    /** Multiples of the food's serving. */
    val quantity: Double = 1.0,
    val source: FoodEntrySource = FoodEntrySource.MANUAL,
    val externalId: String? = null,
)

data class BodyMetric(
    val id: String,
    val userId: String,
    val measuredAt: Instant,
    /** 'weight', 'body_fat', 'resting_hr', ... */
    val type: String,
    val value: Double,
    val source: Source = Source.MANUAL,
    val externalId: String? = null,
)

/** Pre-aggregated daily rollup backing the dashboard. */
data class DailySummary(
    val userId: String,
    val day: LocalDate,
    val caloriesIn: Int? = null,
    val caloriesOut: Int? = null,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
)

/** A single AI-estimated food item awaiting user confirmation before save. */
data class EstimatedFoodItem(
    val name: String,
    val estServing: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    /** 0..1 — surfaced to the user; estimates are never auto-trusted. */
    val confidence: Double,
    /** Cached foods row id, if the estimate was persisted to the shared cache. */
    val foodId: String? = null,
)
