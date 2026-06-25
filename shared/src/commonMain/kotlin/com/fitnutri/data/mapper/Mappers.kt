package com.fitnutri.data.mapper

import com.fitnutri.data.remote.dto.AiEstimatedItemDto
import com.fitnutri.data.remote.dto.BodyMetricDto
import com.fitnutri.data.remote.dto.DailySummaryDto
import com.fitnutri.data.remote.dto.ExerciseSetDto
import com.fitnutri.data.remote.dto.FoodDto
import com.fitnutri.data.remote.dto.FoodEntryDto
import com.fitnutri.data.remote.dto.ProfileDto
import com.fitnutri.data.remote.dto.WorkoutDto
import com.fitnutri.domain.model.BodyMetric
import com.fitnutri.domain.model.DailySummary
import com.fitnutri.domain.model.EstimatedFoodItem
import com.fitnutri.domain.model.ExerciseSet
import com.fitnutri.domain.model.Food
import com.fitnutri.domain.model.FoodEntry
import com.fitnutri.domain.model.FoodEntrySource
import com.fitnutri.domain.model.FoodSource
import com.fitnutri.domain.model.Goal
import com.fitnutri.domain.model.MacroTargets
import com.fitnutri.domain.model.Meal
import com.fitnutri.domain.model.Profile
import com.fitnutri.domain.model.Sex
import com.fitnutri.domain.model.Source
import com.fitnutri.domain.model.UnitSystem
import com.fitnutri.domain.model.Workout
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * DTO <-> domain mapping. Enum<->string conversions match the DB CHECK
 * constraints exactly; unknown/absent strings degrade gracefully to sensible
 * defaults rather than throwing, so a new server-side enum value can't crash an
 * older client.
 */

// ---- enum <-> string -------------------------------------------------------

private fun String.toUnitSystem() =
    if (equals("imperial", true)) UnitSystem.IMPERIAL else UnitSystem.METRIC

private fun UnitSystem.wire() = name.lowercase()

private fun String?.toSex() = when (this?.lowercase()) {
    "male" -> Sex.MALE
    "female" -> Sex.FEMALE
    "other" -> Sex.OTHER
    else -> null
}

private fun Sex.wire() = name.lowercase()

private fun String?.toGoal() = when (this?.lowercase()) {
    "lose_fat" -> Goal.LOSE_FAT
    "gain_muscle" -> Goal.GAIN_MUSCLE
    "maintain" -> Goal.MAINTAIN
    else -> null
}

private fun Goal.wire() = name.lowercase()

private fun String.toSource() = when (lowercase()) {
    "healthkit" -> Source.HEALTHKIT
    "health_connect" -> Source.HEALTH_CONNECT
    "strava" -> Source.STRAVA
    else -> Source.MANUAL
}

private fun Source.wire() = name.lowercase()

private fun String.toFoodSource() = when (lowercase()) {
    "off" -> FoodSource.OFF
    "nutritionix" -> FoodSource.NUTRITIONIX
    "usda" -> FoodSource.USDA
    "ai_estimate" -> FoodSource.AI_ESTIMATE
    else -> FoodSource.USER
}

private fun FoodSource.wire() = name.lowercase()

private fun String.toFoodEntrySource() = when (lowercase()) {
    "barcode" -> FoodEntrySource.BARCODE
    "ai_photo" -> FoodEntrySource.AI_PHOTO
    else -> FoodEntrySource.MANUAL
}

private fun FoodEntrySource.wire() = name.lowercase()

private fun String?.toMeal() = when (this?.lowercase()) {
    "breakfast" -> Meal.BREAKFAST
    "lunch" -> Meal.LUNCH
    "dinner" -> Meal.DINNER
    "snack" -> Meal.SNACK
    else -> null
}

private fun Meal.wire() = name.lowercase()

// ---- profiles --------------------------------------------------------------

fun ProfileDto.toDomain() = Profile(
    id = id,
    displayName = displayName,
    unitSystem = unitSystem.toUnitSystem(),
    sex = sex.toSex(),
    birthDate = birthDate?.let { LocalDate.parse(it) },
    heightCm = heightCm,
    goal = goal.toGoal(),
    targets = MacroTargets(dailyCalorieTarget, proteinTargetG, carbTargetG, fatTargetG),
)

fun Profile.toDto() = ProfileDto(
    id = id,
    displayName = displayName,
    unitSystem = unitSystem.wire(),
    sex = sex?.wire(),
    birthDate = birthDate?.toString(),
    heightCm = heightCm,
    goal = goal?.wire(),
    dailyCalorieTarget = targets.calories,
    proteinTargetG = targets.proteinG,
    carbTargetG = targets.carbsG,
    fatTargetG = targets.fatG,
)

// ---- workouts --------------------------------------------------------------

fun WorkoutDto.toDomain(sets: List<ExerciseSet> = emptyList()) = Workout(
    id = id,
    userId = userId,
    type = type,
    startedAt = Instant.parse(startedAt),
    endedAt = endedAt?.let { Instant.parse(it) },
    calories = calories,
    notes = notes,
    source = source.toSource(),
    externalId = externalId,
    sets = sets,
)

fun Workout.toDto() = WorkoutDto(
    id = id,
    userId = userId,
    type = type,
    startedAt = startedAt.toString(),
    endedAt = endedAt?.toString(),
    calories = calories,
    notes = notes,
    source = source.wire(),
    externalId = externalId,
)

fun ExerciseSetDto.toDomain() = ExerciseSet(
    id = id,
    workoutId = workoutId,
    userId = userId,
    exercise = exercise,
    setIndex = setIndex,
    reps = reps,
    weightKg = weightKg,
    rpe = rpe,
)

fun ExerciseSet.toDto() = ExerciseSetDto(
    id = id,
    workoutId = workoutId,
    userId = userId,
    exercise = exercise,
    setIndex = setIndex,
    reps = reps,
    weightKg = weightKg,
    rpe = rpe,
)

// ---- foods -----------------------------------------------------------------

fun FoodDto.toDomain() = Food(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    servingQty = servingQty,
    servingUnit = servingUnit,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    source = source.toFoodSource(),
    externalId = externalId,
)

// ---- food entries ----------------------------------------------------------

fun FoodEntryDto.toDomain() = FoodEntry(
    id = id,
    userId = userId,
    foodId = foodId,
    loggedAt = Instant.parse(loggedAt),
    meal = meal.toMeal(),
    quantity = quantity,
    source = source.toFoodEntrySource(),
    externalId = externalId,
)

fun FoodEntry.toDto() = FoodEntryDto(
    id = id,
    userId = userId,
    foodId = foodId,
    loggedAt = loggedAt.toString(),
    meal = meal?.wire(),
    quantity = quantity,
    source = source.wire(),
    externalId = externalId,
)

// ---- body metrics ----------------------------------------------------------

fun BodyMetricDto.toDomain() = BodyMetric(
    id = id,
    userId = userId,
    measuredAt = Instant.parse(measuredAt),
    type = type,
    value = value,
    source = source.toSource(),
    externalId = externalId,
)

fun BodyMetric.toDto() = BodyMetricDto(
    id = id,
    userId = userId,
    measuredAt = measuredAt.toString(),
    type = type,
    value = value,
    source = source.wire(),
    externalId = externalId,
)

// ---- daily summaries -------------------------------------------------------

fun DailySummaryDto.toDomain() = DailySummary(
    userId = userId,
    day = LocalDate.parse(day),
    caloriesIn = caloriesIn,
    caloriesOut = caloriesOut,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)

// ---- AI estimates ----------------------------------------------------------

fun AiEstimatedItemDto.toDomain() = EstimatedFoodItem(
    name = name,
    estServing = estServing,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    confidence = confidence.coerceIn(0.0, 1.0),
    foodId = foodId,
)
