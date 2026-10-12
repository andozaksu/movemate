package com.example.movemate.data

import java.time.LocalDate

data class DailySummary(
    val date: LocalDate,
    val totalSteps: Int,
    val totalSleepMinutes: Int,
    val totalCalories: Int = 0
)
