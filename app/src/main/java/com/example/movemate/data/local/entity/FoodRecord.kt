package com.example.movemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "food_records")
data class FoodRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val foodName: String,
    val calories: Int,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
