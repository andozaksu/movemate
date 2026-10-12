package com.example.movemate.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.example.movemate.data.ActivityRepository
import com.example.movemate.data.local.entity.StepRecord
import kotlinx.coroutines.launch
import java.time.LocalDate

class StepsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ActivityRepository(application)
    private val prefs = application.getSharedPreferences("steps_prefs", Context.MODE_PRIVATE)

    val stepHistory: LiveData<List<StepRecord>> = repository.allStepRecords

    private val _todaySteps = MutableLiveData(0)
    val todaySteps: LiveData<Int> get() = _todaySteps

    /**
     * Called with the raw TYPE_STEP_COUNTER value (total steps since last device boot).
     * A per-day baseline is persisted in SharedPreferences so that:
     *  - Steps survive fragment navigation and app restarts within the same calendar day.
     *  - The counter resets to zero automatically on a new day.
     */
    fun onSensorStepCount(totalSinceBoot: Int) {
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_BASELINE_DATE, "")
        val baseline: Int

        if (savedDate == today) {
            // Same day — use the already-saved baseline
            baseline = prefs.getInt(KEY_BASELINE_STEPS, totalSinceBoot)
        } else {
            // New day or first launch — treat current sensor reading as today's zero point
            baseline = totalSinceBoot
            prefs.edit()
                .putInt(KEY_BASELINE_STEPS, totalSinceBoot)
                .putString(KEY_BASELINE_DATE, today)
                .apply()
        }

        val sessionSteps = (totalSinceBoot - baseline).coerceAtLeast(0)
        _todaySteps.value = sessionSteps
        viewModelScope.launch {
            repository.updateStepsForToday(sessionSteps)
        }
    }

    /**
     * Called from the accelerometer-based fallback step detector (emulator).
     * Receives a running count of detected steps for today directly.
     */
    fun onAccelerometerStep(steps: Int) {
        _todaySteps.value = steps
        viewModelScope.launch {
            repository.updateStepsForToday(steps)
        }
    }

    companion object {
        private const val KEY_BASELINE_STEPS = "baseline_steps"
        private const val KEY_BASELINE_DATE  = "baseline_date"
    }
}
