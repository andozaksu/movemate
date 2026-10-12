package com.example.movemate.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.movemate.data.local.entity.StepRecord
import java.time.LocalDate

@Dao
interface StepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStepRecord(record: StepRecord)

    @Query("SELECT * FROM step_records WHERE date = :date LIMIT 1")
    suspend fun getStepRecordByDate(date: LocalDate): StepRecord?

    @Query("SELECT * FROM step_records ORDER BY date DESC")
    fun getAllStepRecords(): LiveData<List<StepRecord>>

    @Query("SELECT * FROM step_records ORDER BY date DESC")
    suspend fun getAllStepRecordsOnce(): List<StepRecord>

    @Query("DELETE FROM step_records")
    suspend fun deleteAllStepRecords()
}
