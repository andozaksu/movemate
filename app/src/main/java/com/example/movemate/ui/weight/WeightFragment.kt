package com.example.movemate.ui.weight

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movemate.databinding.FragmentWeightBinding
import com.example.movemate.viewmodel.ProfileViewModel
import com.example.movemate.viewmodel.WeightViewModel

class WeightFragment : Fragment() {

    private var _binding: FragmentWeightBinding? = null
    private val binding get() = _binding!!

    private val weightViewModel: WeightViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()

    private lateinit var adapter: WeightAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WeightAdapter { record -> weightViewModel.deleteRecord(record) }
        binding.rvWeightHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWeightHistory.adapter = adapter

        // Pre-fill height from profile
        profileViewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            profile?.let {
                if (binding.etHeight.text.isNullOrEmpty()) {
                    binding.etHeight.setText("%.0f".format(it.heightCm))
                }
            }
        }

        weightViewModel.allWeightRecords.observe(viewLifecycleOwner) { records ->
            adapter.submitList(records)
            binding.tvEmptyWeight.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            binding.rvWeightHistory.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        }

        weightViewModel.message.observe(viewLifecycleOwner) { msg ->
            binding.tvBmiResult.text = msg
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnLogWeight.setOnClickListener {
            val weightStr = binding.etWeight.text?.toString()
            val heightStr = binding.etHeight.text?.toString()
            val weight = weightStr?.toFloatOrNull()
            val height = heightStr?.toFloatOrNull()
            if (weight == null || height == null) {
                Toast.makeText(requireContext(), "Please enter valid weight and height", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            weightViewModel.logWeight(weight, height)
            // Also update profile weight
            profileViewModel.userProfile.value?.let { profile ->
                profileViewModel.saveProfile(profile.copy(weightKg = weight, heightCm = height))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
