package com.fitnutri.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs matching the Postgres columns (snake_case via @SerialName). Kept
 * separate from the domain models so serialization concerns never leak upward.
 * Timestamps/dates are carried as ISO strings and parsed in the mappers — that
 * sidesteps wiring kotlinx-datetime serializers through every DTO and keeps the
 * shape identical to what PostgREST returns.
 */

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("unit_system") val unitSystem: String = "metric",
    val sex: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    val goal: String? = null,
    @SerialName("daily_calorie_target") val dailyCalorieTarget: Int? = null,
    @SerialName("protein_target_g") val proteinTargetG: Int? = null,
    @SerialName("carb_target_g") val carbTargetG: Int? = null,
    @SerialName("fat_target_g") val fatTargetG: Int? = null,
)

@Serializable
data class WorkoutDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val calories: Int? = null,
    val notes: String? = null,
    val source: String = "manual",
    @SerialName("external_id") val externalId: String? = null,
)

@Serializable
data class ExerciseSetDto(
    val id: String,
    @SerialName("workout_id") val workoutId: String,
    @SerialName("user_id") val userId: String,
    val exercise: String,
    @SerialName("set_index") val setIndex: Int,
    val reps: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val rpe: Double? = null,
)

@Serializable
data class FoodDto(
    val id: String,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    @SerialName("serving_qty") val servingQty: Double? = null,
    @SerialName("serving_unit") val servingUnit: String? = null,
    val calories: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    val source: String,
    @SerialName("external_id") val externalId: String? = null,
)

@Serializable
data class FoodEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("food_id") val foodId: String? = null,
    @SerialName("logged_at") val loggedAt: String,
    val meal: String? = null,
    val quantity: Double = 1.0,
    val source: String = "manual",
    @SerialName("external_id") val externalId: String? = null,
)

@Serializable
data class BodyMetricDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("measured_at") val measuredAt: String,
    val type: String,
    val value: Double,
    val source: String = "manual",
    @SerialName("external_id") val externalId: String? = null,
)

@Serializable
data class DailySummaryDto(
    @SerialName("user_id") val userId: String,
    val day: String,
    @SerialName("calories_in") val caloriesIn: Int? = null,
    @SerialName("calories_out") val caloriesOut: Int? = null,
    @SerialName("protein_g") val proteinG: Int? = null,
    @SerialName("carbs_g") val carbsG: Int? = null,
    @SerialName("fat_g") val fatG: Int? = null,
)

/** Response shape from the ai-food-log Edge Function. */
@Serializable
data class AiFoodLogResponse(
    val items: List<AiEstimatedItemDto> = emptyList(),
    val model: String? = null,
    val estimate: Boolean = true,
)

@Serializable
data class AiEstimatedItemDto(
    val name: String,
    @SerialName("est_serving") val estServing: String = "",
    val calories: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    val confidence: Double = 0.0,
    @SerialName("food_id") val foodId: String? = null,
)

/** Request body for the ai-food-log Edge Function. */
@Serializable
data class AiFoodLogRequest(
    @SerialName("image_base64") val imageBase64: String,
    @SerialName("media_type") val mediaType: String = "image/jpeg",
)
