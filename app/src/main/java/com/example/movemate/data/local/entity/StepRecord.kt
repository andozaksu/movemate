package com.example.movemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "step_records")
data class StepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val steps: Int
)
