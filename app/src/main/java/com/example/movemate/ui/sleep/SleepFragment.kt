package com.example.movemate.ui.sleep

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.movemate.databinding.FragmentSleepBinding
import com.example.movemate.viewmodel.ProfileViewModel
import com.example.movemate.viewmodel.SleepViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sqrt

class SleepFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentSleepBinding? = null
    private val binding get() = _binding!!

    private val sleepViewModel: SleepViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var lastMovementTime = 0L
    private var isSleeping = false

    companion object {
        private const val MOTION_THRESHOLD = 1.5f
        private const val SLEEP_START_INACTIVITY_MS = 10_000L
        private const val GRAVITY_EARTH = 9.81f

        /** Sleep is only auto-detected between 20:00 and 12:00 (next day). */
        private const val SLEEP_HOUR_START = 20
        private const val SLEEP_HOUR_END   = 12

        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm, dd MMM")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSleepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sleepViewModel.sleepHistory.observe(viewLifecycleOwner) { records ->
            val latest = records.firstOrNull()
            if (latest != null) {
                val h = latest.durationMinutes / 60
                val m = latest.durationMinutes % 60
                binding.tvLastSleep.text = "Start: ${latest.startTime.format(TIME_FMT)}\n" +
                        "End:   ${latest.endTime.format(TIME_FMT)}\n" +
                        "Duration: ${h}h ${m}m"

                val goalMin = profileViewModel.userProfile.value?.sleepGoalMinutes ?: 0
                if (goalMin > 0) {
                    val pct = ((latest.durationMinutes.toFloat() / goalMin) * 100).toInt().coerceAtMost(100)
                    binding.progressSleepGoal.setProgressCompat(pct, true)
                    binding.tvSleepGoalLabel.text = "${latest.durationMinutes} / $goalMin min goal"
                } else {
                    binding.progressSleepGoal.setProgressCompat(0, true)
                    binding.tvSleepGoalLabel.text = "No goal set"
                }
            } else {
                binding.tvLastSleep.text = "No sleep recorded yet"
                binding.progressSleepGoal.setProgressCompat(0, true)
                binding.tvSleepGoalLabel.text = ""
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lastMovementTime = System.currentTimeMillis()
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnTouchListener { _, _ -> onUserInteraction(); false }
        binding.root.setOnKeyListener { _, _, _ -> onUserInteraction(); false }

        // Show time-of-day context so the user knows whether auto-detection is active
        updateTimeContextHint()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        if (isSleeping) {
            sleepViewModel.endSleepSessionIfActive()
            isSleeping = false
        }
    }

    private fun onUserInteraction() {
        lastMovementTime = System.currentTimeMillis()
        if (isSleeping) {
            isSleeping = false
            sleepViewModel.endSleepSessionIfActive()
            binding.tvSleepInfo.text = "Movement detected — sleep session saved"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
        )
        val motion = abs(magnitude - GRAVITY_EARTH)

        if (motion > MOTION_THRESHOLD) {
            lastMovementTime = now
            if (isSleeping) {
                isSleeping = false
                sleepViewModel.endSleepSessionIfActive()
                binding.tvSleepInfo.text = "Movement detected — sleep session saved"
                binding.tvSleepSessionTime.text = ""
            } else {
                binding.tvSleepInfo.text = "Active — monitoring for inactivity"
            }
        } else {
            val inactiveFor = now - lastMovementTime
            if (!isSleeping && inactiveFor >= SLEEP_START_INACTIVITY_MS) {
                if (isAppropriateTimeForSleep()) {
                    isSleeping = true
                    sleepViewModel.startSleepSession()
                    val chargingNote = if (isDeviceCharging()) " · device charging" else ""
                    binding.tvSleepInfo.text = "Sleep detected$chargingNote"
                    binding.tvSleepSessionTime.text = "Tracking sleep duration…"
                } else {
                    // Inactivity detected but outside the expected sleep window — do not start session
                    binding.tvSleepInfo.text = "Inactivity detected (outside sleep hours 8 PM – 12 PM)"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── Richer signal helpers ──────────────────────────────────────────────────

    /**
     * Returns true if the current hour falls within the expected sleep window:
     * 20:00 – 23:59 or 00:00 – 11:59.
     */
    private fun isAppropriateTimeForSleep(): Boolean {
        val hour = LocalTime.now().hour
        return hour >= SLEEP_HOUR_START || hour < SLEEP_HOUR_END
    }

    /**
     * Returns true if the device is currently charging or fully charged.
     * Many users charge overnight, making this a useful sleep-likelihood signal.
     */
    private fun isDeviceCharging(): Boolean {
        val batteryStatus = requireContext().registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return false
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** Shows a persistent hint about whether auto-detection is currently active. */
    private fun updateTimeContextHint() {
        if (isAppropriateTimeForSleep()) {
            val chargingNote = if (isDeviceCharging()) " · charging" else ""
            binding.tvSleepInfo.text = "Monitoring for sleep (sleep window active$chargingNote)"
        } else {
            binding.tvSleepInfo.text = "Auto-detection paused — outside sleep hours (8 PM – 12 PM)"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
