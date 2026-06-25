package com.fitnutri.presentation.onboarding

import com.fitnutri.domain.calc.ActivityLevel
import com.fitnutri.domain.calc.EnergyTargets
import com.fitnutri.domain.model.Goal
import com.fitnutri.domain.model.MacroTargets
import com.fitnutri.domain.model.Profile
import com.fitnutri.domain.model.Sex
import com.fitnutri.domain.model.UnitSystem
import com.fitnutri.domain.repository.ProfileRepository
import com.fitnutri.presentation.mvi.MviEffect
import com.fitnutri.presentation.mvi.MviIntent
import com.fitnutri.presentation.mvi.MviState
import com.fitnutri.presentation.mvi.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Onboarding flow: collect units + profile + goal, compute suggested macro
 * targets the user can override, then persist the [Profile]. Demonstrates the
 * MVI pattern driving a multi-field form backed by [ProfileRepository] and the
 * pure [EnergyTargets] calculator.
 */

data class OnboardingState(
    val userId: String,
    val displayName: String? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: Goal? = null,
    /** Computed suggestion; the user may overwrite before saving. */
    val targets: MacroTargets = MacroTargets(),
    val targetsEdited: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) : MviState {
    /** Enough info to compute a TDEE-based suggestion. */
    val canSuggest: Boolean
        get() = sex != null && birthDate != null && heightCm != null &&
            weightKg != null && goal != null
}

sealed interface OnboardingIntent : MviIntent {
    data class SetDisplayName(val value: String?) : OnboardingIntent
    data class SetUnitSystem(val value: UnitSystem) : OnboardingIntent
    data class SetSex(val value: Sex) : OnboardingIntent
    data class SetBirthDate(val value: LocalDate) : OnboardingIntent
    data class SetHeightCm(val value: Double) : OnboardingIntent
    data class SetWeightKg(val value: Double) : OnboardingIntent
    data class SetActivity(val value: ActivityLevel) : OnboardingIntent
    data class SetGoal(val value: Goal) : OnboardingIntent
    /** Recompute suggested targets from the current inputs. */
    data object SuggestTargets : OnboardingIntent
    /** User overrode a target manually. */
    data class EditTargets(val value: MacroTargets) : OnboardingIntent
    data object Save : OnboardingIntent
    data class SaveResult(val error: String?) : OnboardingIntent
}

sealed interface OnboardingEffect : MviEffect {
    data object Completed : OnboardingEffect
    data class ShowError(val message: String) : OnboardingEffect
}

class OnboardingStore(
    private val repository: ProfileRepository,
    scope: CoroutineScope,
    userId: String,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<OnboardingState, OnboardingIntent, OnboardingEffect>(
    OnboardingState(userId = userId),
    scope,
) {

    override fun reduce(state: OnboardingState, intent: OnboardingIntent): OnboardingState =
        when (intent) {
            is OnboardingIntent.SetDisplayName -> state.copy(displayName = intent.value)
            is OnboardingIntent.SetUnitSystem -> state.copy(unitSystem = intent.value)
            is OnboardingIntent.SetSex -> state.copy(sex = intent.value)
            is OnboardingIntent.SetBirthDate -> state.copy(birthDate = intent.value)
            is OnboardingIntent.SetHeightCm -> state.copy(heightCm = intent.value)
            is OnboardingIntent.SetWeightKg -> state.copy(weightKg = intent.value)
            is OnboardingIntent.SetActivity -> state.copy(activity = intent.value)
            is OnboardingIntent.SetGoal -> state.copy(goal = intent.value)
            is OnboardingIntent.EditTargets ->
                state.copy(targets = intent.value, targetsEdited = true)
            is OnboardingIntent.SuggestTargets ->
                if (state.canSuggest && !state.targetsEdited) {
                    state.copy(targets = computeTargets(state))
                } else state
            is OnboardingIntent.Save -> state.copy(saving = true, error = null)
            is OnboardingIntent.SaveResult ->
                state.copy(saving = false, saved = intent.error == null, error = intent.error)
        }

    override suspend fun onIntent(intent: OnboardingIntent) {
        if (intent is OnboardingIntent.Save) {
            val s = current
            repository.upsertProfile(s.toProfile())
                .onSuccess {
                    dispatch(OnboardingIntent.SaveResult(null))
                    emitEffect(OnboardingEffect.Completed)
                }
                .onFailure {
                    val msg = it.message ?: "Could not save profile"
                    dispatch(OnboardingIntent.SaveResult(msg))
                    emitEffect(OnboardingEffect.ShowError(msg))
                }
        }
    }

    private fun computeTargets(s: OnboardingState): MacroTargets {
        val birth = s.birthDate ?: return s.targets
        val age = ageYears(birth)
        return EnergyTargets.suggestTargets(
            sex = s.sex!!,
            weightKg = s.weightKg!!,
            heightCm = s.heightCm!!,
            ageYears = age,
            activity = s.activity,
            goal = s.goal!!,
        )
    }

    private fun OnboardingState.toProfile() = Profile(
        id = userId,
        displayName = displayName,
        unitSystem = unitSystem,
        sex = sex,
        birthDate = birthDate,
        heightCm = heightCm,
        goal = goal,
        targets = targets,
    )

    /** Whole years between [birth] and today, accounting for month/day. */
    private fun ageYears(birth: LocalDate): Int {
        val today = clock.todayIn(timeZone)
        var age = today.year - birth.year
        val hadBirthdayThisYear = today.monthNumber > birth.monthNumber ||
            (today.monthNumber == birth.monthNumber && today.dayOfMonth >= birth.dayOfMonth)
        if (!hadBirthdayThisYear) age -= 1
        return age.coerceAtLeast(0)
    }
}
