package com.example.movemate.ui.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.movemate.databinding.FragmentStepsBinding
import com.example.movemate.viewmodel.ProfileViewModel
import com.example.movemate.viewmodel.StepsViewModel

class StepsFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentStepsBinding? = null
    private val binding get() = _binding!!

    private val stepsViewModel: StepsViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var usingAccelerometer = false

    // Accelerometer-based step detection (fallback for emulator)
    private var lastMagnitude = 0.0
    private var stepThreshold = 12.0  // magnitude threshold to count a step
    private var lastStepTime = 0L
    private var accelStepCount = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.cardPermissionWarning.visibility = View.GONE
            registerStepSensor()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGrantPermission.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        stepsViewModel.todaySteps.observe(viewLifecycleOwner) { steps ->
            binding.tvTodaySteps.text = "%,d".format(steps)
            updateStats(steps)
        }
    }

    private fun updateStats(steps: Int) {
        val profile  = profileViewModel.userProfile.value
        val goal     = profile?.stepGoal ?: 0
        val weightKg = profile?.weightKg ?: 70f

        val distanceKm    = steps * 0.000762
        val caloriesBurned = (steps * 0.04 * weightKg / 70).toInt()

        binding.tvDistance.text       = "%.2f km".format(distanceKm)
        binding.tvCaloriesBurned.text = "$caloriesBurned kcal"

        if (goal > 0) {
            val pct = ((steps.toFloat() / goal) * 100).toInt().coerceAtMost(100)
            binding.progressStepGoal.setProgressCompat(pct, true)
            binding.tvStepGoalLabel.text = "%,d / %,d steps".format(steps, goal)
        } else {
            binding.progressStepGoal.setProgressCompat(0, true)
            binding.tvStepGoalLabel.text = "%,d steps — no goal set".format(steps)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            binding.cardPermissionWarning.visibility = View.GONE
            registerStepSensor()
        } else {
            binding.cardPermissionWarning.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        // Baseline is now persisted in SharedPreferences inside StepsViewModel —
        // no need to reset it here.
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED

    private fun registerStepSensor() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            usingAccelerometer = false
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            // Fallback: use accelerometer for step detection (emulator support)
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                usingAccelerometer = true
                // Restore accumulated steps from the ViewModel so we don't reset on fragment re-creation
                accelStepCount = stepsViewModel.todaySteps.value ?: 0
                sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            // Hardware step counter — pass raw total to ViewModel
            stepsViewModel.onSensorStepCount(event.values[0].toInt())
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && usingAccelerometer) {
            // Detect steps from accelerometer magnitude changes
            val x = event.values[0].toDouble()
            val y = event.values[1].toDouble()
            val z = event.values[2].toDouble()
            val magnitude = Math.sqrt(x * x + y * y + z * z)

            val now = System.currentTimeMillis()
            // Detect a peak: magnitude crosses threshold upward, with a minimum 300ms gap between steps
            if (magnitude > stepThreshold && lastMagnitude <= stepThreshold
                && (now - lastStepTime) > 300
            ) {
                accelStepCount++
                lastStepTime = now
                stepsViewModel.onAccelerometerStep(accelStepCount)
            }
            lastMagnitude = magnitude
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
