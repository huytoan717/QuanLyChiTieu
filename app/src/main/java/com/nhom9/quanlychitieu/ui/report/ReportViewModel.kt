package com.nhom9.quanlychitieu.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import java.util.Calendar
import java.util.Locale

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    data class ReportState(
        val periodIndex: Int = 1, // 0: Tuần, 1: Tháng, 2: Năm
        val totalExpense: Long = 8450000L,
        val totalIncome: Long = 2050000L,
        val savingsRate: String = "58.8%",
        val trendPercentage: String = "-12.5%",
        val aiInsightText: String = "Ví dụ: Giảm chi ăn uống có thể giúp dành thêm tiền cho mục tiêu tiết kiệm.",
        val labels: List<String> = listOf("Tuần 1", "Tuần 2", "Tuần 3", "Tuần 4"),
        val barIncomeList: FloatArray = floatArrayOf(0.6f, 0.75f, 0.85f, 0.65f),
        val barExpenseList: FloatArray = floatArrayOf(0.4f, 0.95f, 0.5f, 0.45f),
        val donutBreakdown: FloatArray = floatArrayOf(35f, 25f, 20f, 20f),
        val isLoading: Boolean = false
    )

    private val _state = MutableLiveData(ReportState())
    val state: LiveData<ReportState> = _state

    private var subscription: AutoCloseable? = null
    private var selectedPeriod = 1
    private var cachedTransactions: List<SavedTransaction> = emptyList()

    fun selectPeriod(periodIndex: Int) {
        selectedPeriod = periodIndex
        render(cachedTransactions, selectedPeriod)
        if (subscription == null) {
            subscription = FinanceRepository.listenToTransactions(getApplication()) {
                cachedTransactions = it
                render(it, selectedPeriod)
            }
        }
    }

    private fun rowsInPeriod(rows: List<SavedTransaction>, period: Int): List<SavedTransaction> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            when (period) {
                0 -> add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
                2 -> set(Calendar.DAY_OF_YEAR, 1)
                else -> set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val end = (start.clone() as Calendar).apply {
            when (period) {
                0 -> add(Calendar.DAY_OF_MONTH, 7)
                2 -> add(Calendar.YEAR, 1)
                else -> add(Calendar.MONTH, 1)
            }
        }
        return rows.filter { it.occurredAt >= start.timeInMillis && it.occurredAt < end.timeInMillis }
    }

    private fun render(transactions: List<SavedTransaction>, periodIndex: Int) {
            val periodRows = rowsInPeriod(transactions, periodIndex)
            val totalExp = FinanceRepository.calculateTotalExpense(periodRows)
            val totalInc = FinanceRepository.calculateTotalIncome(periodRows)

            val savings = if (totalInc > 0) {
                val rate = ((totalInc - totalExp).toDouble() / totalInc) * 100
                String.format(Locale.US, "%.1f%%", rate.coerceIn(0.0, 100.0))
            } else "0.0%"

            val (labels, incomeBars, expenseBars, donutPercentages) = when (periodIndex) {
                0 -> aggregateWeekData(periodRows)
                2 -> aggregateYearData(periodRows)
                else -> aggregateMonthData(periodRows)
            }

            val insight = when (periodIndex) {
                0 -> "✦ Nhận xét Tuần: Chi tiêu cao nhất rơi vào các ngày có giao dịch ăn uống & di chuyển."
                2 -> "✦ Nhận xét Năm: Thống kê thu chi tổng quan qua 12 tháng năm nay."
                else -> "✦ Nhận xét Tháng: Giảm chi ăn uống có thể giúp dành thêm tiền cho quỹ tích lũy."
            }

            _state.postValue(
                ReportState(
                    periodIndex = periodIndex,
                    totalExpense = totalExp,
                    totalIncome = totalInc,
                    savingsRate = savings,
                    trendPercentage = if (totalExp > totalInc) "-12.5%" else "+8.4%",
                    aiInsightText = insight,
                    labels = labels,
                    barIncomeList = incomeBars,
                    barExpenseList = expenseBars,
                    donutBreakdown = donutPercentages,
                    isLoading = false
                )
            )
    }

    override fun onCleared() {
        subscription?.close()
        super.onCleared()
    }

    private fun aggregateWeekData(transactions: List<SavedTransaction>): Tuple4<List<String>, FloatArray, FloatArray, FloatArray> {
        val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        val incArray = FloatArray(7) { 0f }
        val expArray = FloatArray(7) { 0f }

        val calendar = Calendar.getInstance()
        val currentWeek = calendar.get(Calendar.WEEK_OF_YEAR)
        val currentYear = calendar.get(Calendar.YEAR)

        transactions.forEach { t ->
            calendar.timeInMillis = t.occurredAt
            if (calendar.get(Calendar.WEEK_OF_YEAR) == currentWeek && calendar.get(Calendar.YEAR) == currentYear) {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val dayIndex = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
                if (dayIndex in 0..6) {
                    if (t.type == TransactionType.INCOME) incArray[dayIndex] += t.amount.toFloat()
                    else if (t.type == TransactionType.EXPENSE) expArray[dayIndex] += t.amount.toFloat()
                }
            }
        }

        val maxVal = (incArray.maxOrNull() ?: 0f).coerceAtLeast(expArray.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val incHeights = FloatArray(7) { (incArray[it] / maxVal).coerceIn(0f, 1.0f) }
        val expHeights = FloatArray(7) { (expArray[it] / maxVal).coerceIn(0f, 1.0f) }

        return Tuple4(labels, incHeights, expHeights, calculateCategoryDonut(transactions))
    }

    private fun aggregateMonthData(transactions: List<SavedTransaction>): Tuple4<List<String>, FloatArray, FloatArray, FloatArray> {
        val labels = listOf("Tuần 1", "Tuần 2", "Tuần 3", "Tuần 4")
        val incArray = FloatArray(4) { 0f }
        val expArray = FloatArray(4) { 0f }

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        transactions.forEach { t ->
            calendar.timeInMillis = t.occurredAt
            if (calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear) {
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                val weekIndex = ((dayOfMonth - 1) / 7).coerceIn(0, 3)
                if (t.type == TransactionType.INCOME) incArray[weekIndex] += t.amount.toFloat()
                else if (t.type == TransactionType.EXPENSE) expArray[weekIndex] += t.amount.toFloat()
            }
        }

        val maxVal = (incArray.maxOrNull() ?: 0f).coerceAtLeast(expArray.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val incHeights = FloatArray(4) { (incArray[it] / maxVal).coerceIn(0f, 1.0f) }
        val expHeights = FloatArray(4) { (expArray[it] / maxVal).coerceIn(0f, 1.0f) }

        return Tuple4(labels, incHeights, expHeights, calculateCategoryDonut(transactions))
    }

    private fun aggregateYearData(transactions: List<SavedTransaction>): Tuple4<List<String>, FloatArray, FloatArray, FloatArray> {
        val labels = listOf("T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12")
        val incArray = FloatArray(12) { 0f }
        val expArray = FloatArray(12) { 0f }

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        transactions.forEach { t ->
            calendar.timeInMillis = t.occurredAt
            if (calendar.get(Calendar.YEAR) == currentYear) {
                val monthIndex = calendar.get(Calendar.MONTH).coerceIn(0, 11)
                if (t.type == TransactionType.INCOME) incArray[monthIndex] += t.amount.toFloat()
                else if (t.type == TransactionType.EXPENSE) expArray[monthIndex] += t.amount.toFloat()
            }
        }

        val maxVal = (incArray.maxOrNull() ?: 0f).coerceAtLeast(expArray.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val incHeights = FloatArray(12) { (incArray[it] / maxVal).coerceIn(0f, 1.0f) }
        val expHeights = FloatArray(12) { (expArray[it] / maxVal).coerceIn(0f, 1.0f) }

        return Tuple4(labels, incHeights, expHeights, calculateCategoryDonut(transactions))
    }

    private fun calculateCategoryDonut(transactions: List<SavedTransaction>): FloatArray {
        val shopping = transactions.filter { it.type == TransactionType.EXPENSE && (it.category.contains("Mua", ignoreCase = true) || it.category.contains("Ăn", ignoreCase = true)) }.sumOf { it.amount }
        val transport = transactions.filter { it.type == TransactionType.EXPENSE && it.category.contains("chuyển", ignoreCase = true) }.sumOf { it.amount }
        val home = transactions.filter { it.type == TransactionType.EXPENSE && (it.category.contains("nhà", ignoreCase = true) || it.category.contains("Thuê", ignoreCase = true)) }.sumOf { it.amount }
        val game = transactions.filter { it.type == TransactionType.EXPENSE && (it.category.contains("Giải", ignoreCase = true) || it.category.contains("game", ignoreCase = true)) }.sumOf { it.amount }

        val total = (shopping + transport + home + game).toDouble().coerceAtLeast(1.0)
        return floatArrayOf(
            ((shopping / total) * 100).toFloat().coerceIn(0f, 100f),
            ((transport / total) * 100).toFloat().coerceIn(0f, 100f),
            ((home / total) * 100).toFloat().coerceIn(0f, 100f),
            ((game / total) * 100).toFloat().coerceIn(0f, 100f)
        )
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}