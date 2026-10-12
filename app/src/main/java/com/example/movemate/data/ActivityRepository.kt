package com.example.movemate.data

import android.content.Context
import com.example.movemate.data.local.AppDatabase
import com.example.movemate.data.local.entity.FoodRecord
import com.example.movemate.data.local.entity.SleepRecord
import com.example.movemate.data.local.entity.StepRecord
import com.example.movemate.data.local.entity.UserProfile
import com.example.movemate.data.local.entity.WeightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ActivityRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val stepDao = db.stepDao()
    private val sleepDao = db.sleepDao()
    private val foodDao = db.foodDao()
    private val weightDao = db.weightDao()
    private val userProfileDao = db.userProfileDao()

    // --- Step ---
    val allStepRecords = stepDao.getAllStepRecords()

    suspend fun updateStepsForToday(newSteps: Int) = withContext(Dispatchers.IO) {
        upsertStepRecord(LocalDate.now(), newSteps)
    }

    /** Used by cloud restore to re-insert historical records with their original date. */
    suspend fun updateStepsForToday(newSteps: Int, date: LocalDate) = withContext(Dispatchers.IO) {
        upsertStepRecord(date, newSteps)
    }

    private suspend fun upsertStepRecord(date: LocalDate, steps: Int) {
        val existing = stepDao.getStepRecordByDate(date)
        val record = existing?.copy(steps = steps) ?: StepRecord(date = date, steps = steps)
        stepDao.insertStepRecord(record)
    }

    // --- Sleep ---
    val allSleepRecords = sleepDao.getAllSleepRecords()

    suspend fun insertSleepRecord(record: SleepRecord) = withContext(Dispatchers.IO) {
        sleepDao.insertSleepRecord(record)
    }

    // --- Food ---
    val allFoodRecords = foodDao.getAllFoodRecords()

    fun getFoodForDate(date: LocalDate) = foodDao.getFoodRecordsForDate(date)
    fun getTodayCaloriesLive() = foodDao.getTotalCaloriesForDateLive(LocalDate.now())

    suspend fun insertFoodRecord(record: FoodRecord) = withContext(Dispatchers.IO) {
        foodDao.insertFoodRecord(record)
    }

    suspend fun deleteFoodRecord(record: FoodRecord) = withContext(Dispatchers.IO) {
        foodDao.deleteFoodRecord(record)
    }

    suspend fun getTodayCalories(): Int = withContext(Dispatchers.IO) {
        foodDao.getTotalCaloriesForDate(LocalDate.now())
    }

    // --- Weight ---
    val allWeightRecords = weightDao.getAllWeightRecords()

    suspend fun insertWeightRecord(record: WeightRecord) = withContext(Dispatchers.IO) {
        weightDao.insertWeightRecord(record)
    }

    suspend fun deleteWeightRecord(record: WeightRecord) = withContext(Dispatchers.IO) {
        weightDao.deleteWeightRecord(record)
    }

    suspend fun getLatestWeight(): WeightRecord? = withContext(Dispatchers.IO) {
        weightDao.getLatestWeightRecord()
    }

    // --- One-shot queries for cloud backup ---

    suspend fun getAllStepRecordsOnce(): List<StepRecord> = withContext(Dispatchers.IO) {
        stepDao.getAllStepRecordsOnce()
    }
    suspend fun getAllSleepRecordsOnce(): List<SleepRecord> = withContext(Dispatchers.IO) {
        sleepDao.getAllSleepRecordsOnce()
    }
    suspend fun getAllFoodRecordsOnce(): List<FoodRecord> = withContext(Dispatchers.IO) {
        foodDao.getAllFoodRecordsOnce()
    }
    suspend fun getAllWeightRecordsOnce(): List<WeightRecord> = withContext(Dispatchers.IO) {
        weightDao.getAllWeightRecordsOnce()
    }

    suspend fun deleteAllStepRecords() = withContext(Dispatchers.IO) { stepDao.deleteAllStepRecords() }
    suspend fun deleteAllSleepRecords() = withContext(Dispatchers.IO) { sleepDao.deleteAllSleepRecords() }
    suspend fun deleteAllFoodRecords() = withContext(Dispatchers.IO) { foodDao.deleteAllFoodRecords() }
    suspend fun deleteAllWeightRecords() = withContext(Dispatchers.IO) { weightDao.deleteAllWeightRecords() }

    /** Wipes all activity records and resets the profile to defaults. Used on sign-out. */
    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        stepDao.deleteAllStepRecords()
        sleepDao.deleteAllSleepRecords()
        foodDao.deleteAllFoodRecords()
        weightDao.deleteAllWeightRecords()
        userProfileDao.insertOrUpdate(UserProfile()) // reset to blank defaults
    }

    // --- User Profile ---
    val userProfile = userProfileDao.getProfile()

    suspend fun saveUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.insertOrUpdate(profile)
    }

    suspend fun getUserProfileOnce(): UserProfile = withContext(Dispatchers.IO) {
        userProfileDao.getProfileOnce() ?: UserProfile()
    }
}
