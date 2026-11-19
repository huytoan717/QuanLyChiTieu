package com.nhom9.quanlychitieu.ui.report

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.ui.components.BarChartView
import com.nhom9.quanlychitieu.ui.components.BottomNavHelper
import com.nhom9.quanlychitieu.ui.components.DonutChartView
import com.nhom9.quanlychitieu.ui.components.UiChrome

class ReportActivity : AppCompatActivity() {

    private val viewModel: ReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        UiChrome.setup(this, R.id.main_report)
        BottomNavHelper.setup(this, selectedTabIndex = 1)

        setupTimeFilterTabs()
        setupClickListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            val txtPeriodLabel = findViewById<TextView>(R.id.txtReportPeriodLabel)
            val txtTrend = findViewById<TextView>(R.id.txtReportTrend)
            val txtTotalAmount = findViewById<TextView>(R.id.txtReportTotalAmount)
            val txtTotalIncome = findViewById<TextView>(R.id.txtReportTotalIncome)
            val txtSavingsRate = findViewById<TextView>(R.id.txtReportSavingsRate)
            val txtAiInsightContent = findViewById<TextView>(R.id.txtAiInsightContent)
            val txtBarChartSubtitle = findViewById<TextView>(R.id.txtBarChartSubtitle)
            val barChartView = findViewById<BarChartView>(R.id.barChartView)
            val donutChartView = findViewById<DonutChartView>(R.id.donutChartView)

            txtTrend?.text = state.trendPercentage
            txtTotalAmount?.text = "${VndAmount.format(state.totalExpense)} VNĐ"
            txtTotalIncome?.text = "${VndAmount.format(state.totalIncome)} đ"
            txtSavingsRate?.text = state.savingsRate
            txtAiInsightContent?.text = state.aiInsightText

            val percentages = state.donutBreakdown.map { it.toInt() }
            if (percentages.size >= 4) {
                findViewById<TextView>(R.id.txtDistPercentShopping)?.text = "${percentages[0]}%"
                findViewById<TextView>(R.id.txtDistPercentTransport)?.text = "${percentages[1]}%"
                findViewById<TextView>(R.id.txtDistPercentHome)?.text = "${percentages[2]}%"
                findViewById<TextView>(R.id.txtDistPercentGame)?.text = "${percentages[3]}%"
            }

            barChartView?.setChartData(
                BarChartView.BarData(
                    labels = state.labels,
                    incomeHeights = state.barIncomeList.toList(),
                    expenseHeights = state.barExpenseList.toList()
                )
            )

            val topLabelText = when (state.periodIndex) {
                0 -> {
                    txtPeriodLabel?.text = "Tổng chi tiêu tuần này"
                    txtBarChartSubtitle?.text = "Biểu đồ 7 ngày trong tuần (T2 - CN)"
                    "TỔNG CHI TUẦN"
                }
                2 -> {
                    txtPeriodLabel?.text = "Tổng chi tiêu năm nay"
                    txtBarChartSubtitle?.text = "Tổng quan 12 tháng năm 2025"
                    "TỔNG CHI NĂM"
                }
                else -> {
                    txtPeriodLabel?.text = "Tổng chi tiêu kỳ này"
                    txtBarChartSubtitle?.text = "So sánh 4 tuần trong tháng"
                    "TỔNG CHI THÁNG"
                }
            }

            donutChartView?.setChartData(
                DonutChartView.DonutData(
                    topLabel = topLabelText,
                    amountText = VndAmount.format(state.totalExpense),
                    unitText = "VNĐ",
                    percentages = percentages
                )
            )
        }
    }

    private fun setupTimeFilterTabs() {
        val btnWeek = findViewById<TextView>(R.id.btnReportWeek)
        val btnMonth = findViewById<TextView>(R.id.btnReportMonth)
        val btnYear = findViewById<TextView>(R.id.btnReportYear)

        fun updateSegmentState(index: Int) {
            btnWeek?.background = if (index == 0) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnWeek?.setTextColor(ContextCompat.getColor(this, if (index == 0) R.color.text_primary else R.color.text_secondary))

            btnMonth?.background = if (index == 1) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnMonth?.setTextColor(ContextCompat.getColor(this, if (index == 1) R.color.text_primary else R.color.text_secondary))

            btnYear?.background = if (index == 2) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnYear?.setTextColor(ContextCompat.getColor(this, if (index == 2) R.color.text_primary else R.color.text_secondary))

            viewModel.selectPeriod(index)
        }

        btnWeek?.setOnClickListener { updateSegmentState(0) }
        btnMonth?.setOnClickListener { updateSegmentState(1) }
        btnYear?.setOnClickListener { updateSegmentState(2) }
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.btnShare)?.setOnClickListener {
            Toast.makeText(this, "Chia sẻ báo cáo phân tích tài chính", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        BottomNavHelper.setup(this, selectedTabIndex = 1)
        viewModel.selectPeriod(viewModel.state.value?.periodIndex ?: 1)
    }
}