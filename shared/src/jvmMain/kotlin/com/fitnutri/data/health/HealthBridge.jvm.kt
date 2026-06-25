package com.fitnutri.data.health

import com.fitnutri.domain.model.BodyMetric
import com.fitnutri.domain.model.Workout
import kotlinx.datetime.Instant

/**
 * JVM `actual` for the health bridge. The JVM target exists for host-side unit
 * tests, not for shipping, so there is no health store here — it always reports
 * unavailable. (The Android `actual`, over Health Connect, lands with the
 * :androidApp target in Phase 2.)
 */
actual class HealthBridge {
    actual fun isAvailable(): Boolean = false

    actual suspend fun requestAuthorization(): Boolean = false

    actual suspend fun readWorkouts(start: Instant, end: Instant): List<Workout> = emptyList()

    actual suspend fun readBodyMetrics(start: Instant, end: Instant): List<BodyMetric> =
        emptyList()
}
