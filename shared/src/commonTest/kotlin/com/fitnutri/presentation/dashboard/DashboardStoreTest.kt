package com.fitnutri.presentation.dashboard

import com.fitnutri.domain.model.DailySummary
import com.fitnutri.domain.repository.DashboardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private class FakeDashboardRepository(
    private val summary: DailySummary?,
) : DashboardRepository {
    override fun observeSummary(day: LocalDate): Flow<DailySummary?> = flowOf(summary)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardStoreTest {

    private val day = LocalDate(2026, 6, 24)

    @Test
    fun load_marksLoadingThenPublishesSummary() = runTest {
        val summary = DailySummary(
            userId = "u1", day = day,
            caloriesIn = 2100, caloriesOut = 450,
            proteinG = 150, carbsG = 200, fatG = 60,
        )
        val store = DashboardStore(FakeDashboardRepository(summary), this)

        store.dispatch(DashboardIntent.Load(day))
        testScheduler.advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(day, state.day)
        assertEquals(summary, state.summary)
    }

    @Test
    fun load_withNoSummary_yieldsNullSummaryNotLoading() = runTest {
        val store = DashboardStore(FakeDashboardRepository(null), this)

        store.dispatch(DashboardIntent.Load(day))
        testScheduler.advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.loading)
        assertNull(state.summary)
    }
}
