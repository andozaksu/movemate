package com.example.movemate.ui.charts

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.movemate.R
import com.example.movemate.data.DailySummary
import com.example.movemate.databinding.FragmentChartsBinding
import com.example.movemate.viewmodel.ChartsViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ChartsFragment : Fragment() {

    private var _binding: FragmentChartsBinding? = null
    private val binding get() = _binding!!

    private val chartsViewModel: ChartsViewModel by activityViewModels()

    private val dateFormatter     = DateTimeFormatter.ofPattern("dd/MM")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    /** Currently selected chart window — toggled by the period chips. */
    private var currentPeriodDays = 7

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChartDefaults(binding.chartSteps)
        setupChartDefaults(binding.chartCalories)
        setupChartDefaults(binding.chartSleep)
        setupLineChartDefaults(binding.chartWeight)

        // ── Period toggle ──────────────────────────────────────────────────────
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            currentPeriodDays = if (R.id.chip30Days in checkedIds) 30 else 7
            // Replot with currently cached summaries
            chartsViewModel.dailySummaries.value?.let { refreshBarCharts(it) }
        }

        // ── Daily summaries (bar charts + summary table) ───────────────────────
        chartsViewModel.dailySummaries.observe(viewLifecycleOwner) { summaries ->
            refreshBarCharts(summaries)
            showDailySummaryTable(summaries)
        }

        // ── Weight line chart ──────────────────────────────────────────────────
        chartsViewModel.weightHistory.observe(viewLifecycleOwner) { records ->
            if (records.isNotEmpty()) {
                val entries = records.take(30).reversed().mapIndexed { i, r ->
                    Entry(i.toFloat(), r.weightKg)
                }
                val labels = records.take(30).reversed().map { r ->
                    r.date.format(dateFormatter)
                }
                val dataSet = LineDataSet(entries, "Weight (kg)").apply {
                    color = Color.WHITE
                    setCircleColor(Color.WHITE)
                    lineWidth = 2f
                    circleRadius = 4f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                binding.chartWeight.apply {
                    data = LineData(dataSet)
                    xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    invalidate()
                }
            } else {
                binding.chartWeight.setNoDataText("No weight records yet")
                binding.chartWeight.invalidate()
            }
        }

        // ── Insights: streak ──────────────────────────────────────────────────
        chartsViewModel.stepStreak.observe(viewLifecycleOwner) { streak ->
            binding.tvStreakValue.text = if (streak == 0) "No streak yet" else "$streak day${if (streak == 1) "" else "s"}"
            // Highlight streaks ≥ 3 with the accent colour
            binding.tvStreakValue.setTextColor(
                if (streak >= 3) Color.parseColor("#D4F53C") else Color.WHITE
            )
        }

        // ── Insights: weekly averages ─────────────────────────────────────────
        chartsViewModel.weeklyAvgSteps.observe(viewLifecycleOwner) { avg ->
            binding.tvAvgSteps.text = if (avg == 0) "—" else "%,d steps".format(avg)
        }
        chartsViewModel.weeklyAvgSleepMinutes.observe(viewLifecycleOwner) { avgMin ->
            if (avgMin == 0) {
                binding.tvAvgSleep.text = "—"
            } else {
                val h = avgMin / 60
                val m = avgMin % 60
                binding.tvAvgSleep.text = "${h}h ${m}m"
            }
        }
        chartsViewModel.weeklyAvgCalories.observe(viewLifecycleOwner) { avg ->
            binding.tvAvgCalories.text = if (avg == 0) "—" else "%,d kcal".format(avg)
        }
    }

    // ── Bar chart helpers ──────────────────────────────────────────────────────

    private fun refreshBarCharts(summaries: List<DailySummary>) {
        val window = lastNDays(summaries, currentPeriodDays)
        val label  = if (currentPeriodDays == 7) "7 Days" else "30 Days"
        binding.tvStepsChartTitle.text   = "Steps — Last $label"
        binding.tvCaloriesChartTitle.text = "Calories Consumed — Last $label"
        binding.tvSleepChartTitle.text   = "Sleep — Last $label (minutes)"
        plotStepsChart(window)
        plotCaloriesChart(window)
        plotSleepChart(window)
    }

    private fun lastNDays(summaries: List<DailySummary>, n: Int): List<DailySummary> {
        val today = LocalDate.now()
        val days  = ((n - 1) downTo 0).map { today.minusDays(it.toLong()) }
        return days.map { date ->
            summaries.find { it.date == date } ?: DailySummary(date, 0, 0, 0)
        }
    }

    private fun plotStepsChart(days: List<DailySummary>) {
        val entries = days.mapIndexed { i, d -> BarEntry(i.toFloat(), d.totalSteps.toFloat()) }
        val labels  = days.map { it.date.format(dateFormatter) }
        val dataSet = BarDataSet(entries, "Steps").apply {
            color = Color.WHITE
            setDrawValues(false)
        }
        binding.chartSteps.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            invalidate()
        }
    }

    private fun plotCaloriesChart(days: List<DailySummary>) {
        val entries = days.mapIndexed { i, d -> BarEntry(i.toFloat(), d.totalCalories.toFloat()) }
        val labels  = days.map { it.date.format(dateFormatter) }
        val dataSet = BarDataSet(entries, "Calories (kcal)").apply {
            color = Color.parseColor("#D4F53C")
            setDrawValues(false)
        }
        binding.chartCalories.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            invalidate()
        }
    }

    private fun plotSleepChart(days: List<DailySummary>) {
        val entries = days.mapIndexed { i, d -> BarEntry(i.toFloat(), d.totalSleepMinutes.toFloat()) }
        val labels  = days.map { it.date.format(dateFormatter) }
        val dataSet = BarDataSet(entries, "Sleep (min)").apply {
            color = Color.parseColor("#888888")
            setDrawValues(false)
        }
        binding.chartSleep.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            invalidate()
        }
    }

    // ── Chart styling ──────────────────────────────────────────────────────────

    private fun setupChartDefaults(chart: BarChart) {
        val gridGray  = Color.parseColor("#333333")
        val labelGray = Color.parseColor("#777777")
        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.parseColor("#252525"))
            setTouchEnabled(false)
            xAxis.apply {
                position          = XAxis.XAxisPosition.BOTTOM
                granularity       = 1f
                setDrawGridLines(false)
                textSize          = 10f
                textColor         = labelGray
                axisLineColor     = gridGray
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor     = gridGray
                textColor     = labelGray
                axisLineColor = gridGray
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupLineChartDefaults(chart: LineChart) {
        val gridGray  = Color.parseColor("#333333")
        val labelGray = Color.parseColor("#777777")
        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = false
            setBackgroundColor(Color.parseColor("#252525"))
            setTouchEnabled(true)
            xAxis.apply {
                position          = XAxis.XAxisPosition.BOTTOM
                granularity       = 1f
                setDrawGridLines(false)
                textSize          = 10f
                textColor         = labelGray
                axisLineColor     = gridGray
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor     = gridGray
                textColor     = labelGray
                axisLineColor = gridGray
            }
            axisRight.isEnabled = false
        }
    }

    // ── Daily summary table ────────────────────────────────────────────────────

    private fun showDailySummaryTable(summaries: List<DailySummary>) {
        val container = binding.containerDailySummaries
        container.removeAllViews()

        if (summaries.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "No activity recorded yet."
                setTextColor(Color.parseColor("#777777"))
                setPadding(0, 16, 0, 16)
            }
            container.addView(tv)
            return
        }

        summaries.take(14).forEach { summary ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_daily_summary, container, false)
            row.findViewById<TextView>(R.id.tvDate).text =
                summary.date.format(fullDateFormatter)
            row.findViewById<TextView>(R.id.tvSteps).text =
                "Steps: %,d".format(summary.totalSteps)
            val h = summary.totalSleepMinutes / 60
            val m = summary.totalSleepMinutes % 60
            row.findViewById<TextView>(R.id.tvSleep).text =
                if (summary.totalSleepMinutes > 0) "Sleep: ${h}h ${m}m" else "Sleep: —"
            container.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
