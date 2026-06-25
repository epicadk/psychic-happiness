package com.fitnutri.domain.repository

import com.fitnutri.domain.model.BodyMetric
import com.fitnutri.domain.model.DailySummary
import com.fitnutri.domain.model.EstimatedFoodItem
import com.fitnutri.domain.model.Food
import com.fitnutri.domain.model.FoodEntry
import com.fitnutri.domain.model.Profile
import com.fitnutri.domain.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Repository contracts. The data module implements these against the Supabase
 * client; the presentation layer depends only on these interfaces. Realtime-backed
 * reads are exposed as [Flow] so the UI updates across devices automatically.
 */

interface ProfileRepository {
    suspend fun getProfile(): Result<Profile?>
    suspend fun upsertProfile(profile: Profile): Result<Profile>
}

interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<Workout>>
    suspend fun getWorkout(id: String): Result<Workout?>
    suspend fun upsertWorkout(workout: Workout): Result<Workout>
    suspend fun deleteWorkout(id: String): Result<Unit>
}

interface FoodRepository {
    /** Resolve a barcode via the food-lookup Edge Function (cache-first). */
    suspend fun lookupBarcode(barcode: String): Result<List<Food>>
    /** Free-text search via the food-lookup Edge Function. */
    suspend fun searchFoods(query: String): Result<List<Food>>
}

interface FoodEntryRepository {
    fun observeEntries(day: LocalDate): Flow<List<FoodEntry>>
    suspend fun addEntry(entry: FoodEntry): Result<FoodEntry>
    suspend fun deleteEntry(id: String): Result<Unit>
}

interface BodyMetricRepository {
    fun observeMetrics(type: String): Flow<List<BodyMetric>>
    suspend fun addMetric(metric: BodyMetric): Result<BodyMetric>
}

interface DashboardRepository {
    /** Reads the pre-aggregated [DailySummary] backing the dashboard. */
    fun observeSummary(day: LocalDate): Flow<DailySummary?>
}

interface AiFoodLogRepository {
    /**
     * Sends a photo to the ai-food-log Edge Function and returns estimates for
     * the user to confirm/edit. Does NOT write food_entries — the caller persists
     * only after the user confirms quantities.
     */
    suspend fun estimateFromPhoto(
        imageBase64: String,
        mediaType: String = "image/jpeg",
    ): Result<List<EstimatedFoodItem>>
}
