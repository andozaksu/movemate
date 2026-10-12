package com.example.movemate.ui.food

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movemate.databinding.DialogAddFoodBinding
import com.example.movemate.databinding.DialogBarcodeScannerBinding
import com.example.movemate.databinding.FragmentFoodBinding
import com.example.movemate.viewmodel.BarcodeState
import com.example.movemate.viewmodel.FoodViewModel
import com.example.movemate.viewmodel.ProfileViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

class FoodFragment : Fragment() {

    private var _binding: FragmentFoodBinding? = null
    private val binding get() = _binding!!

    private val foodViewModel: FoodViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()

    private lateinit var adapter: FoodAdapter

    private var scannerDialog: AlertDialog? = null

    // ZXing camera scanner launcher
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        scannerDialog?.dismiss()
        if (result.contents != null) {
            // Camera scan succeeded — look up nutrition via Open Food Facts API
            foodViewModel.lookupBarcode(result.contents)
        }
        // If result.contents == null the user pressed back — nothing to do
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FoodAdapter { record -> foodViewModel.deleteFoodRecord(record) }
        binding.rvFoodList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFoodList.adapter = adapter

        foodViewModel.todayFoodRecords.observe(viewLifecycleOwner) { records ->
            adapter.submitList(records)
            binding.tvEmptyFood.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            binding.rvFoodList.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        }

        foodViewModel.todayTotalCalories.observe(viewLifecycleOwner) { calories ->
            binding.tvTotalCalories.text = "$calories kcal"
            val goal = profileViewModel.userProfile.value?.calorieGoal ?: 0
            if (goal > 0) {
                val pct = ((calories.toFloat() / goal) * 100).toInt().coerceAtMost(100)
                binding.progressCalorieGoal.setProgressCompat(pct, true)
                binding.tvCalorieGoalLabel.text = "of $goal kcal goal"
            } else {
                binding.progressCalorieGoal.setProgressCompat(0, true)
                binding.tvCalorieGoalLabel.text = "No goal set"
            }
        }

        profileViewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            val goal = profile?.calorieGoal ?: 0
            val cal  = foodViewModel.todayTotalCalories.value ?: 0
            if (goal > 0) {
                val pct = ((cal.toFloat() / goal) * 100).toInt().coerceAtMost(100)
                binding.progressCalorieGoal.setProgressCompat(pct, true)
                binding.tvCalorieGoalLabel.text = "of $goal kcal goal"
            } else {
                binding.progressCalorieGoal.setProgressCompat(0, true)
                binding.tvCalorieGoalLabel.text = "No goal set"
            }
        }

        foodViewModel.message.observe(viewLifecycleOwner) { msg ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // ── Barcode lookup result ──────────────────────────────────────────────
        foodViewModel.barcodeState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BarcodeState.Idle    -> { /* nothing */ }
                is BarcodeState.Loading -> {
                    Toast.makeText(requireContext(), "Looking up product…", Toast.LENGTH_SHORT).show()
                }
                is BarcodeState.Success -> {
                    val n = state.nutrition
                    // Values from Open Food Facts are per 100 g — label the entry clearly
                    showAddFoodDialog(
                        prefillName     = "${n.name} (per 100 g)",
                        prefillCalories = n.calories.toString(),
                        prefillProtein  = "%.1f".format(n.protein),
                        prefillCarbs    = "%.1f".format(n.carbs),
                        prefillFat      = "%.1f".format(n.fat)
                    )
                    foodViewModel.resetBarcodeState()
                }
                is BarcodeState.NotFound -> {
                    Toast.makeText(
                        requireContext(),
                        "Product not found — enter details manually",
                        Toast.LENGTH_LONG
                    ).show()
                    showAddFoodDialog()
                    foodViewModel.resetBarcodeState()
                }
                is BarcodeState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    showAddFoodDialog()
                    foodViewModel.resetBarcodeState()
                }
            }
        }

        binding.fabAddFood.setOnClickListener { showAddFoodDialog() }
        binding.btnScanBarcode.setOnClickListener { showBarcodeScannerDialog() }
    }

    // ── Barcode scanner dialog ──────────────────────────────────────────────────

    private fun showBarcodeScannerDialog() {
        val dialogBinding = DialogBarcodeScannerBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Scan Barcode")
            .setView(dialogBinding.root)
            .setPositiveButton("Use Manual Entry") { _, _ ->
                val barcode = dialogBinding.etBarcodeManual.text?.toString()?.trim() ?: ""
                if (barcode.isNotEmpty()) {
                    // Try API lookup for manual barcode entry too
                    foodViewModel.lookupBarcode(barcode)
                } else {
                    showAddFoodDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        scannerDialog = dialog

        dialogBinding.btnOpenCamera.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                setPrompt("Scan barcode — press Back to cancel")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
            }
            barcodeLauncher.launch(options)
        }

        dialog.show()
    }

    // ── Add food dialog ─────────────────────────────────────────────────────────

    private fun showAddFoodDialog(
        prefillName: String     = "",
        prefillCalories: String = "",
        prefillProtein: String  = "",
        prefillCarbs: String    = "",
        prefillFat: String      = ""
    ) {
        val dialogBinding = DialogAddFoodBinding.inflate(layoutInflater)
        if (prefillName.isNotBlank())     dialogBinding.etFoodName.setText(prefillName)
        if (prefillCalories.isNotBlank()) dialogBinding.etCalories.setText(prefillCalories)
        if (prefillProtein.isNotBlank())  dialogBinding.etProtein.setText(prefillProtein)
        if (prefillCarbs.isNotBlank())    dialogBinding.etCarbs.setText(prefillCarbs)
        if (prefillFat.isNotBlank())      dialogBinding.etFat.setText(prefillFat)

        val chipMap = mapOf(
            dialogBinding.chipApple   to Pair("Apple", 95),
            dialogBinding.chipBanana  to Pair("Banana", 105),
            dialogBinding.chipChicken to Pair("Chicken breast (100g)", 165),
            dialogBinding.chipRice    to Pair("Rice (100g)", 130),
            dialogBinding.chipEgg     to Pair("Egg", 78),
            dialogBinding.chipOats    to Pair("Oats (100g)", 389)
        )
        chipMap.forEach { (chip, food) ->
            chip.setOnClickListener {
                dialogBinding.etFoodName.setText(food.first)
                dialogBinding.etCalories.setText(food.second.toString())
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Food")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name     = dialogBinding.etFoodName.text?.toString() ?: ""
                val calories = dialogBinding.etCalories.text?.toString()?.toIntOrNull() ?: 0
                val protein  = dialogBinding.etProtein.text?.toString()?.toFloatOrNull() ?: 0f
                val carbs    = dialogBinding.etCarbs.text?.toString()?.toFloatOrNull() ?: 0f
                val fat      = dialogBinding.etFat.text?.toString()?.toFloatOrNull() ?: 0f
                foodViewModel.addFoodRecord(name, calories, protein, carbs, fat)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        scannerDialog?.dismiss()
        scannerDialog = null
        super.onDestroyView()
        _binding = null
    }
}
