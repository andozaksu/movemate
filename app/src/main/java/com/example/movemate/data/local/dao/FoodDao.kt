package com.example.movemate.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.movemate.data.local.entity.FoodRecord
import java.time.LocalDate

@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodRecord(record: FoodRecord)

    @Delete
    suspend fun deleteFoodRecord(record: FoodRecord)

    @Query("SELECT * FROM food_records ORDER BY timestamp DESC")
    fun getAllFoodRecords(): LiveData<List<FoodRecord>>

    @Query("SELECT * FROM food_records WHERE date = :date ORDER BY timestamp DESC")
    fun getFoodRecordsForDate(date: LocalDate): LiveData<List<FoodRecord>>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM food_records WHERE date = :date")
    suspend fun getTotalCaloriesForDate(date: LocalDate): Int

    @Query("SELECT COALESCE(SUM(calories), 0) FROM food_records WHERE date = :date")
    fun getTotalCaloriesForDateLive(date: LocalDate): LiveData<Int>

    @Query("SELECT * FROM food_records ORDER BY timestamp DESC")
    suspend fun getAllFoodRecordsOnce(): List<FoodRecord>

    @Query("DELETE FROM food_records")
    suspend fun deleteAllFoodRecords()
}
