package com.example.movemate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Singleton row
    val name: String = "",
    val age: Int = 0,
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val gender: String = "Not specified",
    // Daily goals — 0 means "not set yet"
    val stepGoal: Int = 0,
    val calorieGoal: Int = 0,
    val sleepGoalMinutes: Int = 0
)
