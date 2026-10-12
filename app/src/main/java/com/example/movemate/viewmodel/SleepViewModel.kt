package com.example.movemate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.movemate.data.ActivityRepository
import com.example.movemate.data.local.entity.SleepRecord
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil

class SleepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ActivityRepository(application)

    val sleepHistory: LiveData<List<SleepRecord>> = repository.allSleepRecords

    private var currentSleepStart: LocalDateTime? = null

    fun startSleepSession() {
        if (currentSleepStart == null) {
            currentSleepStart = LocalDateTime.now()
        }
    }

    fun endSleepSessionIfActive() {
        val start = currentSleepStart ?: return
        val end = LocalDateTime.now()

        val durationSeconds = Duration.between(start, end).seconds.coerceAtLeast(0)
        if (durationSeconds < 5) {
            currentSleepStart = null
            return
        }


        val durationMinutes = ceil(durationSeconds / 60.0).toInt().coerceAtLeast(1)

        val record = SleepRecord(
            startTime = start,
            endTime = end,
            durationMinutes = durationMinutes
        )

        viewModelScope.launch {
            repository.insertSleepRecord(record)
        }

        currentSleepStart = null
    }
}
