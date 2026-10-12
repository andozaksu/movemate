package com.example.movemate.ui.weight

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movemate.data.local.entity.WeightRecord
import com.example.movemate.databinding.ItemWeightRecordBinding
import java.time.format.DateTimeFormatter

class WeightAdapter(
    private val onDelete: (WeightRecord) -> Unit
) : ListAdapter<WeightRecord, WeightAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WeightRecord>() {
            override fun areItemsTheSame(a: WeightRecord, b: WeightRecord) = a.id == b.id
            override fun areContentsTheSame(a: WeightRecord, b: WeightRecord) = a == b
        }
        private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    }

    inner class ViewHolder(private val binding: ItemWeightRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: WeightRecord) {
            binding.tvWeightDate.text = record.date.format(DATE_FMT)
            binding.tvWeightValue.text = "%.1f kg".format(record.weightKg)
            binding.tvWeightBmi.text = "BMI: %.1f".format(record.bmi)
            binding.btnDeleteWeight.setOnClickListener { onDelete(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemWeightRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
