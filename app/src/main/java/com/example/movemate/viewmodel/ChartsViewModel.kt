package com.example.movemate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.movemate.data.ActivityRepository
import com.example.movemate.data.DailySummary
import com.example.movemate.data.local.entity.FoodRecord
import com.example.movemate.data.local.entity.SleepRecord
import com.example.movemate.data.local.entity.StepRecord
import com.example.movemate.data.local.entity.WeightRecord
import java.time.LocalDate

class ChartsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ActivityRepository(application)

    val stepHistory: LiveData<List<StepRecord>> = repo.allStepRecords
    val sleepHistory: LiveData<List<SleepRecord>> = repo.allSleepRecords
    val foodHistory: LiveData<List<FoodRecord>> = repo.allFoodRecords
    val weightHistory: LiveData<List<WeightRecord>> = repo.allWeightRecords
    val userProfile = repo.userProfile

    // ── Daily summaries (all data merged by date) ──────────────────────────────

    private val _dailySummaries = MediatorLiveData<List<DailySummary>>()
    val dailySummaries: LiveData<List<DailySummary>> get() = _dailySummaries

    init {
        _dailySummaries.addSource(stepHistory)  { combine() }
        _dailySummaries.addSource(sleepHistory) { combine() }
        _dailySummaries.addSource(foodHistory)  { combine() }
    }

    private fun combine() {
        val steps  = stepHistory.value.orEmpty()
        val sleeps = sleepHistory.value.orEmpty()
        val foods  = foodHistory.value.orEmpty()

        val map = mutableMapOf<LocalDate, DailySummary>()

        steps.forEach { s ->
            map[s.date] = (map[s.date] ?: DailySummary(s.date, 0, 0, 0))
                .copy(totalSteps = s.steps)
        }
        sleeps.forEach { r ->
            val date     = r.startTime.toLocalDate()
            val existing = map[date] ?: DailySummary(date, 0, 0, 0)
            map[date]    = existing.copy(totalSleepMinutes = existing.totalSleepMinutes + r.durationMinutes)
        }
        foods.forEach { f ->
            val existing = map[f.date] ?: DailySummary(f.date, 0, 0, 0)
            map[f.date]  = existing.copy(totalCalories = existing.totalCalories + f.calories)
        }

        _dailySummaries.value = map.values.sortedByDescending { it.date }
    }

    // ── Analytics ──────────────────────────────────────────────────────────────

    /**
     * Current step-goal streak: number of consecutive days (going backwards from today
     * or yesterday) on which the user met their step goal.
     */
    val stepStreak: MediatorLiveData<Int> = MediatorLiveData<Int>().also { streak ->
        val recompute = { _: Any? ->
            val summaries = _dailySummaries.value.orEmpty()
            val goal      = userProfile.value?.stepGoal ?: 10000
            streak.value  = computeStreak(summaries, goal)
        }
        streak.addSource(_dailySummaries) { recompute(it) }
        streak.addSource(userProfile)     { recompute(it) }
    }

    /** 7-day average steps (only days that have a record). */
    val weeklyAvgSteps: LiveData<Int> = _dailySummaries.map { summaries ->
        avgStepsForLastN(summaries, 7)
    }

    /** 7-day average sleep in minutes. */
    val weeklyAvgSleepMinutes: LiveData<Int> = _dailySummaries.map { summaries ->
        avgSleepForLastN(summaries, 7)
    }

    /** 7-day average calories. */
    val weeklyAvgCalories: LiveData<Int> = _dailySummaries.map { summaries ->
        avgCaloriesForLastN(summaries, 7)
    }

    // ── Helper computations ────────────────────────────────────────────────────

    private fun computeStreak(summaries: List<DailySummary>, goal: Int): Int {
        if (summaries.isEmpty()) return 0
        val recordMap = summaries.associateBy { it.date }
        val today = LocalDate.now()

        // If today has no record yet, start checking from yesterday
        var date  = if (recordMap.containsKey(today)) today else today.minusDays(1)
        var count = 0
        while (true) {
            val rec = recordMap[date] ?: break
            if (rec.totalSteps < goal) break
            count++
            date = date.minusDays(1)
        }
        return count
    }

    private fun avgStepsForLastN(summaries: List<DailySummary>, n: Int): Int {
        val today = LocalDate.now()
        val window = summaries.filter { it.date >= today.minusDays(n.toLong() - 1) && it.date <= today }
        return if (window.isEmpty()) 0 else window.sumOf { it.totalSteps } / window.size
    }

    private fun avgSleepForLastN(summaries: List<DailySummary>, n: Int): Int {
        val today = LocalDate.now()
        val window = summaries.filter {
            it.date >= today.minusDays(n.toLong() - 1) && it.date <= today && it.totalSleepMinutes > 0
        }
        return if (window.isEmpty()) 0 else window.sumOf { it.totalSleepMinutes } / window.size
    }

    private fun avgCaloriesForLastN(summaries: List<DailySummary>, n: Int): Int {
        val today = LocalDate.now()
        val window = summaries.filter {
            it.date >= today.minusDays(n.toLong() - 1) && it.date <= today && it.totalCalories > 0
        }
        return if (window.isEmpty()) 0 else window.sumOf { it.totalCalories } / window.size
    }
}
