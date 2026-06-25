package com.fitnutri.domain.dedup

import com.fitnutri.domain.model.Source
import com.fitnutri.domain.model.Workout
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutDedupTest {

    private val base = Instant.parse("2026-06-24T07:00:00Z")

    private fun workout(
        id: String,
        source: Source,
        startMin: Int,
        durMin: Int?,
    ) = Workout(
        id = id,
        userId = "u1",
        type = "run",
        startedAt = base + startMin.minutes,
        endedAt = durMin?.let { base + startMin.minutes + it.minutes },
        source = source,
    )

    @Test
    fun sameActivityFromTwoSources_isDuplicate() {
        val manual = workout("a", Source.MANUAL, 0, 30)
        val strava = workout("b", Source.STRAVA, 1, 31) // overlaps, similar duration
        assertTrue(WorkoutDedup.isProbableDuplicate(manual, strava))
    }

    @Test
    fun sameSource_isNeverDuplicate_handledByDbConstraint() {
        val a = workout("a", Source.STRAVA, 0, 30)
        val b = workout("b", Source.STRAVA, 0, 30)
        assertFalse(WorkoutDedup.isProbableDuplicate(a, b))
    }

    @Test
    fun differentTimes_notDuplicate() {
        val morning = workout("a", Source.MANUAL, 0, 30)
        val evening = workout("b", Source.STRAVA, 600, 30) // 10h later
        assertFalse(WorkoutDedup.isProbableDuplicate(morning, evening))
    }

    @Test
    fun veryDifferentDurations_overlapButNotDuplicate() {
        val short = workout("a", Source.MANUAL, 0, 20)
        val long = workout("b", Source.STRAVA, 0, 90) // overlaps but 70min longer
        assertFalse(WorkoutDedup.isProbableDuplicate(short, long))
    }

    @Test
    fun missingEndTime_fallsBackToStartProximity() {
        val a = workout("a", Source.MANUAL, 0, null)
        val near = workout("b", Source.STRAVA, 5, null)   // within 15min
        val far = workout("c", Source.STRAVA, 60, null)   // beyond 15min
        assertTrue(WorkoutDedup.isProbableDuplicate(a, near))
        assertFalse(WorkoutDedup.isProbableDuplicate(a, far))
    }

    @Test
    fun clustering_groupsOnlyRealDuplicates() {
        val ws = listOf(
            workout("a", Source.MANUAL, 0, 30),
            workout("b", Source.STRAVA, 1, 31),    // dup of a
            workout("c", Source.MANUAL, 600, 45),  // unique
        )
        val clusters = WorkoutDedup.findDuplicateClusters(ws)
        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b"), clusters.first().map { it.id }.toSet())
    }
}
