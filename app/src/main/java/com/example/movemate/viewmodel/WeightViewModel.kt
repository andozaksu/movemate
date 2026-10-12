package com.example.movemate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.movemate.data.ActivityRepository
import com.example.movemate.data.local.entity.WeightRecord
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ActivityRepository(app)

    val allWeightRecords: LiveData<List<WeightRecord>> = repo.allWeightRecords

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> get() = _message

    fun logWeight(weightKg: Float, heightCm: Float) {
        if (weightKg <= 0f || heightCm <= 0f) {
            _message.value = "Please enter valid weight and height"
            return
        }
        val heightM = heightCm / 100f
        val bmi = weightKg / (heightM * heightM)
        viewModelScope.launch {
            repo.insertWeightRecord(
                WeightRecord(
                    date = LocalDate.now(),
                    weightKg = weightKg,
                    bmi = bmi
                )
            )
            val category = bmiCategory(bmi)
            _message.value = "Logged %.1f kg — BMI: %.1f ($category)".format(weightKg, bmi)
        }
    }

    fun deleteRecord(record: WeightRecord) {
        viewModelScope.launch {
            repo.deleteWeightRecord(record)
        }
    }

    private fun bmiCategory(bmi: Float): String = when {
        bmi < 18.5f -> "Underweight"
        bmi < 25f -> "Normal"
        bmi < 30f -> "Overweight"
        else -> "Obese"
    }
}
