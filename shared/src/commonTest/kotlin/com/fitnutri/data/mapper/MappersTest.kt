package com.fitnutri.data.mapper

import com.fitnutri.data.remote.dto.WorkoutDto
import com.fitnutri.domain.model.Goal
import com.fitnutri.domain.model.MacroTargets
import com.fitnutri.domain.model.Profile
import com.fitnutri.domain.model.Sex
import com.fitnutri.domain.model.Source
import com.fitnutri.domain.model.UnitSystem
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappersTest {

    @Test
    fun profile_roundTrips() {
        val original = Profile(
            id = "u1",
            displayName = "Sam",
            unitSystem = UnitSystem.IMPERIAL,
            sex = Sex.OTHER,
            birthDate = LocalDate(1990, 5, 14),
            heightCm = 175.0,
            goal = Goal.GAIN_MUSCLE,
            targets = MacroTargets(2500, 180, 250, 70),
        )
        val back = original.toDto().toDomain()
        assertEquals(original, back)
    }

    @Test
    fun workout_parsesTimestampsAndSource() {
        val dto = WorkoutDto(
            id = "w1",
            userId = "u1",
            type = "run",
            startedAt = "2026-06-24T07:00:00Z",
            endedAt = "2026-06-24T07:30:00Z",
            calories = 300,
            source = "strava",
            externalId = "12345",
        )
        val domain = dto.toDomain()
        assertEquals(Instant.parse("2026-06-24T07:00:00Z"), domain.startedAt)
        assertEquals(Instant.parse("2026-06-24T07:30:00Z"), domain.endedAt)
        assertEquals(Source.STRAVA, domain.source)
    }

    @Test
    fun unknownEnumStrings_degradeGracefully() {
        // A source value an older client doesn't know about must not throw.
        val dto = WorkoutDto(
            id = "w1", userId = "u1", type = "run",
            startedAt = "2026-06-24T07:00:00Z", source = "garmin_future",
        )
        assertEquals(Source.MANUAL, dto.toDomain().source)
    }

    @Test
    fun nullableProfileFields_mapToNull() {
        val domain = Profile(id = "u1").toDto().toDomain()
        assertNull(domain.sex)
        assertNull(domain.goal)
        assertNull(domain.birthDate)
        assertEquals(UnitSystem.METRIC, domain.unitSystem)
    }
}
