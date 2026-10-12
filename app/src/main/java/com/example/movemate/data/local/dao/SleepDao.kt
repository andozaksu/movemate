package com.example.movemate.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.movemate.data.local.entity.SleepRecord

@Dao
interface SleepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepRecord(record: SleepRecord)

    @Query("SELECT * FROM sleep_records ORDER BY startTime DESC")
    fun getAllSleepRecords(): LiveData<List<SleepRecord>>

    @Query("SELECT * FROM sleep_records ORDER BY startTime DESC")
    suspend fun getAllSleepRecordsOnce(): List<SleepRecord>

    @Query("DELETE FROM sleep_records")
    suspend fun deleteAllSleepRecords()
}
