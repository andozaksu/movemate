package com.example.movemate.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.movemate.data.local.entity.WeightRecord

@Dao
interface WeightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightRecord(record: WeightRecord)

    @Delete
    suspend fun deleteWeightRecord(record: WeightRecord)

    @Query("SELECT * FROM weight_records ORDER BY date DESC")
    fun getAllWeightRecords(): LiveData<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY date DESC LIMIT 1")
    suspend fun getLatestWeightRecord(): WeightRecord?

    @Query("SELECT * FROM weight_records ORDER BY date DESC")
    suspend fun getAllWeightRecordsOnce(): List<WeightRecord>

    @Query("DELETE FROM weight_records")
    suspend fun deleteAllWeightRecords()
}
