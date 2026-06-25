package com.fitnutri.presentation.dashboard

import com.fitnutri.domain.model.DailySummary
import com.fitnutri.domain.model.MacroTargets
import com.fitnutri.domain.repository.DashboardRepository
import com.fitnutri.presentation.mvi.MviEffect
import com.fitnutri.presentation.mvi.MviIntent
import com.fitnutri.presentation.mvi.MviState
import com.fitnutri.presentation.mvi.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Example feature store: the dashboard reads the pre-aggregated [DailySummary].
 * It demonstrates the MVI wiring (Intent -> reduce -> State, async via onIntent)
 * the rest of the app's features follow.
 */

data class DashboardState(
    val day: LocalDate? = null,
    val summary: DailySummary? = null,
    val targets: MacroTargets = MacroTargets(),
    val loading: Boolean = false,
    val error: String? = null,
) : MviState

sealed interface DashboardIntent : MviIntent {
    data class Load(val day: LocalDate) : DashboardIntent
    data class SummaryUpdated(val summary: DailySummary?) : DashboardIntent
    data class Failed(val message: String) : DashboardIntent
}

sealed interface DashboardEffect : MviEffect {
    data class ShowError(val message: String) : DashboardEffect
}

class DashboardStore(
    private val repository: DashboardRepository,
    scope: CoroutineScope,
) : Store<DashboardState, DashboardIntent, DashboardEffect>(DashboardState(), scope) {

    override fun reduce(state: DashboardState, intent: DashboardIntent): DashboardState =
        when (intent) {
            is DashboardIntent.Load ->
                state.copy(day = intent.day, loading = true, error = null)
            is DashboardIntent.SummaryUpdated ->
                state.copy(summary = intent.summary, loading = false)
            is DashboardIntent.Failed ->
                state.copy(loading = false, error = intent.message)
        }

    override suspend fun onIntent(intent: DashboardIntent) {
        if (intent is DashboardIntent.Load) {
            scope.launch {
                repository.observeSummary(intent.day)
                    .catch { e ->
                        dispatch(DashboardIntent.Failed(e.message ?: "Failed to load summary"))
                        emitEffect(DashboardEffect.ShowError(e.message ?: "Failed to load"))
                    }
                    .collect { summary -> dispatch(DashboardIntent.SummaryUpdated(summary)) }
            }
        }
    }
}
