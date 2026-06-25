package com.fitnutri.domain.dedup

import com.fitnutri.domain.model.Workout
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Cross-source overlap detection (spec §3, dedup layer 2).
 *
 * The DB's `unique (user_id, source, external_id)` constraint handles re-import
 * safety, but a hand-logged run and the same run pulled from Strava have
 * *different* external_ids, so the constraint can't catch them. v1 deliberately
 * does NOT auto-merge: it surfaces a "looks like a duplicate — keep both / merge?"
 * prompt. This is the pure heuristic behind that prompt.
 *
 * Two workouts are candidate duplicates when they:
 *   - come from different sources (same-source overlap is already constraint-deduped),
 *   - have overlapping time windows, and
 *   - have similar durations (within a tolerance).
 */
object WorkoutDedup {

    /** Tunable heuristic thresholds. */
    data class Config(
        val durationTolerance: Duration = 10.minutes,
        /** If either workout has no end time, fall back to start-time proximity. */
        val startProximity: Duration = 15.minutes,
    )

    private fun durationOf(w: Workout): Duration? =
        w.endedAt?.let { it - w.startedAt }

    /** Do the two workouts' [start, end] windows overlap at all? */
    private fun windowsOverlap(a: Workout, b: Workout): Boolean {
        val aEnd = a.endedAt ?: a.startedAt
        val bEnd = b.endedAt ?: b.startedAt
        return a.startedAt <= bEnd && b.startedAt <= aEnd
    }

    /** True if [a] and [b] look like the same activity from two sources. */
    fun isProbableDuplicate(a: Workout, b: Workout, config: Config = Config()): Boolean {
        if (a.id == b.id) return false
        if (a.source == b.source) return false // already handled by the DB constraint

        val da = durationOf(a)
        val db = durationOf(b)
        return if (da != null && db != null) {
            windowsOverlap(a, b) && abs((da - db).inWholeSeconds) <= config.durationTolerance.inWholeSeconds
        } else {
            // Missing an end time: lean on start proximity instead.
            abs((a.startedAt - b.startedAt).inWholeSeconds) <= config.startProximity.inWholeSeconds
        }
    }

    /**
     * Group a user's workouts into clusters of probable duplicates. Singleton
     * clusters (no duplicate found) are omitted — the caller only needs the
     * clusters worth prompting about.
     */
    fun findDuplicateClusters(
        workouts: List<Workout>,
        config: Config = Config(),
    ): List<List<Workout>> {
        val n = workouts.size
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != cur) { val next = parent[cur]; parent[cur] = root; cur = next }
            return root
        }
        fun union(x: Int, y: Int) { parent[find(x)] = find(y) }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (isProbableDuplicate(workouts[i], workouts[j], config)) union(i, j)
            }
        }

        return (0 until n)
            .groupBy { find(it) }
            .values
            .filter { it.size > 1 }
            .map { idxs -> idxs.map { workouts[it] } }
    }
}
