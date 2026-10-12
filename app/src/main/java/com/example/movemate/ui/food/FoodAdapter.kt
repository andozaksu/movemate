package com.example.movemate.ui.food

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movemate.data.local.entity.FoodRecord
import com.example.movemate.databinding.ItemFoodLogBinding

class FoodAdapter(
    private val onDelete: (FoodRecord) -> Unit
) : ListAdapter<FoodRecord, FoodAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FoodRecord>() {
            override fun areItemsTheSame(a: FoodRecord, b: FoodRecord) = a.id == b.id
            override fun areContentsTheSame(a: FoodRecord, b: FoodRecord) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemFoodLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: FoodRecord) {
            binding.tvFoodName.text = record.foodName
            binding.tvFoodCalories.text = "${record.calories} kcal"
            val macros = buildString {
                if (record.protein > 0) append("P: %.1fg ".format(record.protein))
                if (record.carbs > 0) append("C: %.1fg ".format(record.carbs))
                if (record.fat > 0) append("F: %.1fg".format(record.fat))
            }
            binding.tvFoodMacros.text = macros.ifBlank { "" }
            binding.btnDeleteFood.setOnClickListener { onDelete(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemFoodLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
