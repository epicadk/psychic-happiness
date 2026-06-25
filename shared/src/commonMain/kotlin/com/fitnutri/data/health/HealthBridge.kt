package com.fitnutri.data.health

import com.fitnutri.domain.model.BodyMetric
import com.fitnutri.domain.model.Workout
import kotlinx.datetime.Instant

/**
 * The thin native sync boundary.
 *
 * KMP does NOT wrap the platform health APIs, so this is the one place that needs
 * `expect`/`actual`: the iOS `actual` is implemented over HealthKit (Swift interop),
 * the Android `actual` over Health Connect. Everything above this interface stays
 * shared. Per the plan, the real `actual`s land in Phase 2 — only one platform to
 * start — so the bridge proves the sync -> upsert -> dedup -> recompute path before
 * it is doubled.
 *
 * Reads return domain models already tagged with the right [com.fitnutri.domain.model.Source]
 * and a stable `externalId`, so the data layer's upsert is idempotent against the
 * `unique (user_id, source, external_id)` constraints.
 */
expect class HealthBridge {
    /** True if the platform exposes a health store and the app may request access. */
    fun isAvailable(): Boolean

    /** Request read authorization for the data types we sync. */
    suspend fun requestAuthorization(): Boolean

    /** Workouts recorded in [start, end], tagged with the platform source. */
    suspend fun readWorkouts(start: Instant, end: Instant): List<Workout>

    /** Body metrics (weight, body fat, resting HR, ...) recorded in [start, end]. */
    suspend fun readBodyMetrics(start: Instant, end: Instant): List<BodyMetric>
}
