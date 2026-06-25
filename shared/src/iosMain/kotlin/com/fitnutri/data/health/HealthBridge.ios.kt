package com.fitnutri.data.health

import com.fitnutri.domain.model.BodyMetric
import com.fitnutri.domain.model.Workout
import kotlinx.datetime.Instant

/**
 * iOS `actual` for the health bridge. The real implementation wraps HealthKit
 * (HKHealthStore queries) via Swift interop and is built in Phase 2. For now it
 * reports unavailable and returns nothing so the shared module compiles and the
 * sync path can be exercised behind a feature flag.
 */
actual class HealthBridge {
    actual fun isAvailable(): Boolean = false

    actual suspend fun requestAuthorization(): Boolean = false

    actual suspend fun readWorkouts(start: Instant, end: Instant): List<Workout> = emptyList()

    actual suspend fun readBodyMetrics(start: Instant, end: Instant): List<BodyMetric> =
        emptyList()
}
