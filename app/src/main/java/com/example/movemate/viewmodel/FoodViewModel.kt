package com.example.movemate.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.movemate.data.ActivityRepository
import com.example.movemate.data.local.entity.FoodRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class FoodViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ActivityRepository(app)

    val todayFoodRecords: LiveData<List<FoodRecord>> = repo.getFoodForDate(LocalDate.now())
    val todayTotalCalories: LiveData<Int> = repo.getTodayCaloriesLive()

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> get() = _message

    // ── Barcode lookup state ───────────────────────────────────────────────────

    private val _barcodeState = MutableLiveData<BarcodeState>(BarcodeState.Idle)
    val barcodeState: LiveData<BarcodeState> get() = _barcodeState

    /**
     * Looks up a barcode using the Open Food Facts public API (no key required).
     * Nutritional values are per 100 g as returned by the API.
     * Emits [BarcodeState] updates observed by FoodFragment.
     */
    fun lookupBarcode(barcode: String) {
        _barcodeState.value = BarcodeState.Loading
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchFromOpenFoodFacts(barcode) }
                _barcodeState.value = result
            } catch (e: Exception) {
                Log.e("FoodViewModel", "Barcode lookup failed", e)
                _barcodeState.value = BarcodeState.Error("Network error — check your connection")
            }
        }
    }

    fun resetBarcodeState() {
        _barcodeState.value = BarcodeState.Idle
    }

    private fun fetchFromOpenFoodFacts(barcode: String): BarcodeState {
        val url = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout    = 6_000
        conn.setRequestProperty("User-Agent", "MoveMate-Android/1.0")
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return BarcodeState.Error("Server error (${conn.responseCode})")
            }
            val json    = JSONObject(conn.inputStream.bufferedReader().readText())
            val status  = json.optInt("status", 0)
            if (status != 1) return BarcodeState.NotFound(barcode)

            val product    = json.getJSONObject("product")
            val nutriments = product.optJSONObject("nutriments")

            val name = product.optString("product_name", "").ifBlank {
                product.optString("generic_name", "Unknown product")
            }
            BarcodeState.Success(
                BarcodeNutrition(
                    name     = name,
                    calories = nutriments?.optDouble("energy-kcal_100g", 0.0)?.toInt() ?: 0,
                    protein  = nutriments?.optDouble("proteins_100g",    0.0)?.toFloat() ?: 0f,
                    carbs    = nutriments?.optDouble("carbohydrates_100g", 0.0)?.toFloat() ?: 0f,
                    fat      = nutriments?.optDouble("fat_100g",          0.0)?.toFloat() ?: 0f
                )
            )
        } finally {
            conn.disconnect()
        }
    }

    // ── Food CRUD ──────────────────────────────────────────────────────────────

    fun addFoodRecord(name: String, calories: Int, protein: Float, carbs: Float, fat: Float) {
        if (name.isBlank() || calories <= 0) {
            _message.value = "Please enter a valid food name and calorie count"
            return
        }
        viewModelScope.launch {
            repo.insertFoodRecord(
                FoodRecord(
                    date      = LocalDate.now(),
                    foodName  = name.trim(),
                    calories  = calories,
                    protein   = protein,
                    carbs     = carbs,
                    fat       = fat
                )
            )
            _message.value = "$name added successfully"
        }
    }

    fun deleteFoodRecord(record: FoodRecord) {
        viewModelScope.launch {
            repo.deleteFoodRecord(record)
            _message.value = "${record.foodName} removed"
        }
    }
}

// ── Barcode state ──────────────────────────────────────────────────────────────

sealed class BarcodeState {
    object Idle                                     : BarcodeState()
    object Loading                                  : BarcodeState()
    data class Success(val nutrition: BarcodeNutrition) : BarcodeState()
    data class NotFound(val barcode: String)        : BarcodeState()
    data class Error(val message: String)           : BarcodeState()
}

data class BarcodeNutrition(
    val name: String,
    /** kcal per 100 g */
    val calories: Int,
    /** grams per 100 g */
    val protein: Float,
    val carbs: Float,
    val fat: Float
)
