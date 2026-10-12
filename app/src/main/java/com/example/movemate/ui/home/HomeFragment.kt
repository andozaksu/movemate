package com.example.movemate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.movemate.R
import com.example.movemate.databinding.FragmentHomeBinding
import com.example.movemate.viewmodel.ChartsViewModel
import com.example.movemate.viewmodel.FoodViewModel
import com.example.movemate.viewmodel.ProfileViewModel
import com.example.movemate.viewmodel.StepsViewModel
import com.example.movemate.viewmodel.SleepViewModel
import com.example.movemate.viewmodel.WeightViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalTime

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val stepsViewModel: StepsViewModel by activityViewModels()
    private val sleepViewModel: SleepViewModel by activityViewModels()
    private val foodViewModel: FoodViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val weightViewModel: WeightViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGreeting()
        observeSteps()
        observeCalories()
        observeSleep()
        observeWeight()
        observeProfile()

        binding.btnViewCharts.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_charts)
        }
    }

    private fun setupGreeting() {
        binding.tvDate.text = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
        updateGreeting(profileViewModel.userProfile.value?.name)
    }

    private fun updateGreeting(name: String?) {
        val hour = LocalTime.now().hour
        val base = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else      -> "Good evening"
        }
        binding.tvGreeting.text = if (!name.isNullOrBlank()) "$base, $name!" else "$base!"
    }

    private fun observeSteps() {
        stepsViewModel.todaySteps.observe(viewLifecycleOwner) { steps ->
            binding.tvStepsCount.text = "%,d".format(steps)
            val goal = profileViewModel.userProfile.value?.stepGoal ?: 0
            if (goal > 0) {
                val pct = ((steps.toFloat() / goal) * 100).toInt().coerceAtMost(100)
                binding.progressSteps.setProgressCompat(pct, true)
                binding.tvStepsGoal.text = "of %,d goal".format(goal)
            } else {
                binding.progressSteps.setProgressCompat(0, true)
                binding.tvStepsGoal.text = "No goal set"
            }
        }
    }

    private fun observeCalories() {
        foodViewModel.todayTotalCalories.observe(viewLifecycleOwner) { calories ->
            binding.tvCaloriesCount.text = "$calories kcal"
            val goal = profileViewModel.userProfile.value?.calorieGoal ?: 0
            if (goal > 0) {
                val pct = ((calories.toFloat() / goal) * 100).toInt().coerceAtMost(100)
                binding.progressCalories.setProgressCompat(pct, true)
                binding.tvCaloriesGoal.text = "of $goal kcal goal"
            } else {
                binding.progressCalories.setProgressCompat(0, true)
                binding.tvCaloriesGoal.text = "No goal set"
            }
        }
    }

    private fun observeSleep() {
        sleepViewModel.sleepHistory.observe(viewLifecycleOwner) { records ->
            val latest = records.firstOrNull()
            if (latest != null) {
                val h = latest.durationMinutes / 60
                val m = latest.durationMinutes % 60
                binding.tvSleepDuration.text = "${h}h ${m}m"
                val goalMin = profileViewModel.userProfile.value?.sleepGoalMinutes ?: 0
                if (goalMin > 0) {
                    val pct = ((latest.durationMinutes.toFloat() / goalMin) * 100).toInt().coerceAtMost(100)
                    binding.progressSleep.setProgressCompat(pct, true)
                    binding.tvSleepGoal.text = "Goal: ${goalMin / 60}h ${goalMin % 60}m"
                } else {
                    binding.progressSleep.setProgressCompat(0, true)
                    binding.tvSleepGoal.text = "No goal set"
                }
            } else {
                binding.tvSleepDuration.text = "No data"
            }
        }
    }

    private fun observeWeight() {
        weightViewModel.allWeightRecords.observe(viewLifecycleOwner) { records ->
            val latest = records.firstOrNull()
            if (latest != null) {
                binding.tvWeightBmi.text =
                    "%.1f kg  |  BMI: %.1f".format(latest.weightKg, latest.bmi)
            } else {
                binding.tvWeightBmi.text = "No weight logged yet"
            }
        }
    }

    private fun observeProfile() {
        // Re-trigger steps/calories/sleep to refresh goal percentages when profile loads
        profileViewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            updateGreeting(profile?.name)
            stepsViewModel.todaySteps.value?.let { steps ->
                val goal = profile?.stepGoal ?: 0
                if (goal > 0) {
                    val pct = ((steps.toFloat() / goal) * 100).toInt().coerceAtMost(100)
                    binding.progressSteps.setProgressCompat(pct, true)
                    binding.tvStepsGoal.text = "of %,d goal".format(goal)
                } else {
                    binding.progressSteps.setProgressCompat(0, true)
                    binding.tvStepsGoal.text = "No goal set"
                }
            }
            foodViewModel.todayTotalCalories.value?.let { calories ->
                val goal = profile?.calorieGoal ?: 0
                if (goal > 0) {
                    val pct = ((calories.toFloat() / goal) * 100).toInt().coerceAtMost(100)
                    binding.progressCalories.setProgressCompat(pct, true)
                    binding.tvCaloriesGoal.text = "of $goal kcal goal"
                } else {
                    binding.progressCalories.setProgressCompat(0, true)
                    binding.tvCaloriesGoal.text = "No goal set"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
