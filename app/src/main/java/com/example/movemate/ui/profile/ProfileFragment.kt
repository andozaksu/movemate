package com.example.movemate.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.movemate.R
import com.example.movemate.data.local.entity.UserProfile
import com.example.movemate.databinding.FragmentProfileBinding
import com.example.movemate.notification.NotificationHelper
import com.example.movemate.viewmodel.ProfileViewModel

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by activityViewModels()

    private val genderOptions = listOf("Not specified", "Male", "Female", "Other")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Gender dropdown
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genderOptions)
        binding.etGender.setAdapter(genderAdapter)

        // Load saved profile
        profileViewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            profile ?: return@observe
            binding.etName.setText(profile.name)
            binding.etAge.setText(if (profile.age > 0) profile.age.toString() else "")
            binding.etHeight.setText(if (profile.heightCm > 0) "%.0f".format(profile.heightCm) else "")
            binding.etWeight.setText(if (profile.weightKg > 0) "%.1f".format(profile.weightKg) else "")
            binding.etGender.setText(profile.gender, false)
            binding.etStepGoal.setText(if (profile.stepGoal > 0) profile.stepGoal.toString() else "")
            binding.etCalorieGoal.setText(if (profile.calorieGoal > 0) profile.calorieGoal.toString() else "")
            binding.etSleepGoal.setText(if (profile.sleepGoalMinutes > 0) profile.sleepGoalMinutes.toString() else "")
        }

        // Firebase auth state
        profileViewModel.isLoggedIn.observe(viewLifecycleOwner) { loggedIn ->
            if (loggedIn) {
                binding.layoutSignIn.visibility = View.GONE
                binding.layoutLoggedIn.visibility = View.VISIBLE
                binding.tvLoggedInAs.text = "Signed in as: ${profileViewModel.currentUserEmail}"
            } else {
                binding.layoutSignIn.visibility = View.VISIBLE
                binding.layoutLoggedIn.visibility = View.GONE
            }
        }

        profileViewModel.message.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe          // null means already consumed — skip
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            profileViewModel.clearMessage()
        }

        // Notifications - create channel when profile loads
        NotificationHelper.createNotificationChannel(requireContext())

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                NotificationHelper.scheduleStepReminder(requireContext())
                Toast.makeText(requireContext(), "Step reminder set for 8:00 PM", Toast.LENGTH_SHORT).show()
            } else {
                NotificationHelper.cancelStepReminder(requireContext())
                Toast.makeText(requireContext(), "Step reminder cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveProfile.setOnClickListener { saveProfile() }
        binding.btnGoToWeight.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_weight)
        }

        // Firebase buttons
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text?.toString() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            profileViewModel.signIn(email, password)
        }
        binding.btnSignUp.setOnClickListener {
            val email = binding.etEmail.text?.toString() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            profileViewModel.signUp(email, password)
        }
        binding.btnSignOut.setOnClickListener { profileViewModel.signOut() }
        binding.btnBackup.setOnClickListener { profileViewModel.backupToCloud() }
        binding.btnRestore.setOnClickListener { profileViewModel.restoreFromCloud() }
    }

    private fun saveProfile() {
        val name   = binding.etName.text?.toString() ?: ""
        val age    = binding.etAge.text?.toString()?.toIntOrNull() ?: 0
        val height = binding.etHeight.text?.toString()?.toFloatOrNull() ?: 0f
        val weight = binding.etWeight.text?.toString()?.toFloatOrNull() ?: 0f
        val gender = binding.etGender.text?.toString()?.takeIf { it.isNotBlank() } ?: "Not specified"
        val stepGoal    = binding.etStepGoal.text?.toString()?.toIntOrNull() ?: 0
        val calorieGoal = binding.etCalorieGoal.text?.toString()?.toIntOrNull() ?: 0
        val sleepGoal   = binding.etSleepGoal.text?.toString()?.toIntOrNull() ?: 0

        val profile = UserProfile(
            id               = 1,
            name             = name,
            age              = age,
            heightCm         = height,
            weightKg         = weight,
            gender           = gender,
            stepGoal         = stepGoal,
            calorieGoal      = calorieGoal,
            sleepGoalMinutes = sleepGoal
        )
        profileViewModel.saveProfile(profile)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
