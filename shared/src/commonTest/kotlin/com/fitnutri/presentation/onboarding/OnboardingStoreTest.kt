package com.fitnutri.presentation.onboarding

import com.fitnutri.domain.calc.ActivityLevel
import com.fitnutri.domain.model.Goal
import com.fitnutri.domain.model.Profile
import com.fitnutri.domain.model.Sex
import com.fitnutri.domain.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingProfileRepository(
    private val failWith: String? = null,
) : ProfileRepository {
    var saved: Profile? = null
    override suspend fun getProfile(): Result<Profile?> = Result.success(saved)
    override suspend fun upsertProfile(profile: Profile): Result<Profile> {
        if (failWith != null) return Result.failure(IllegalStateException(failWith))
        saved = profile
        return Result.success(profile)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingStoreTest {

    // A fixed clock so the age-based suggestion is deterministic.
    private val fixedClock = object : kotlinx.datetime.Clock {
        override fun now() = Instant.parse("2026-06-24T12:00:00Z")
    }

    @Test
    fun suggestTargets_populatesFromInputs() = runTest {
        val s = OnboardingStore(RecordingProfileRepository(), this, "u1",
            clock = fixedClock, timeZone = TimeZone.UTC)
        s.dispatch(OnboardingIntent.SetSex(Sex.MALE))
        s.dispatch(OnboardingIntent.SetBirthDate(LocalDate(1996, 1, 1))) // age 30
        s.dispatch(OnboardingIntent.SetHeightCm(180.0))
        s.dispatch(OnboardingIntent.SetWeightKg(80.0))
        s.dispatch(OnboardingIntent.SetGoal(Goal.MAINTAIN))
        s.dispatch(OnboardingIntent.SetActivity(ActivityLevel.MODERATE))
        s.dispatch(OnboardingIntent.SuggestTargets)

        val t = s.state.value.targets
        assertNotNull(t.calories)
        // TDEE = (10*80 + 6.25*180 - 5*30 + 5) * 1.55 = 1780 * 1.55 = 2759
        assertEquals(2759, t.calories)
    }

    @Test
    fun manualEdit_isNotOverwrittenBySuggest() = runTest {
        val s = OnboardingStore(RecordingProfileRepository(), this, "u1",
            clock = fixedClock, timeZone = TimeZone.UTC)
        s.dispatch(OnboardingIntent.SetSex(Sex.MALE))
        s.dispatch(OnboardingIntent.SetBirthDate(LocalDate(1996, 1, 1)))
        s.dispatch(OnboardingIntent.SetHeightCm(180.0))
        s.dispatch(OnboardingIntent.SetWeightKg(80.0))
        s.dispatch(OnboardingIntent.SetGoal(Goal.MAINTAIN))
        s.dispatch(OnboardingIntent.EditTargets(com.fitnutri.domain.model.MacroTargets(calories = 3000)))
        s.dispatch(OnboardingIntent.SuggestTargets)

        assertEquals(3000, s.state.value.targets.calories)
    }

    @Test
    fun save_persistsProfile() = runTest {
        val repo = RecordingProfileRepository()
        val s = OnboardingStore(repo, this, "u1", clock = fixedClock, timeZone = TimeZone.UTC)
        s.dispatch(OnboardingIntent.SetSex(Sex.FEMALE))
        s.dispatch(OnboardingIntent.SetGoal(Goal.LOSE_FAT))
        s.dispatch(OnboardingIntent.Save)
        testScheduler.advanceUntilIdle()

        assertTrue(s.state.value.saved)
        assertNull(s.state.value.error)
        assertEquals("u1", repo.saved?.id)
        assertEquals(Sex.FEMALE, repo.saved?.sex)
    }

    @Test
    fun save_surfacesError() = runTest {
        val s = OnboardingStore(RecordingProfileRepository(failWith = "network down"),
            this, "u1", clock = fixedClock, timeZone = TimeZone.UTC)
        s.dispatch(OnboardingIntent.Save)
        testScheduler.advanceUntilIdle()

        assertTrue(!s.state.value.saved)
        assertEquals("network down", s.state.value.error)
    }
}
