package com.nhom9.quanlychitieu.ui.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.model.Budget
import com.nhom9.quanlychitieu.model.Category
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.SavingsGoal
import java.util.Calendar

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    data class BudgetState(
        val totalLimit: Long = 15000000L,
        val totalSpent: Long = 9250000L,
        val safeRemaining: Long = 5750000L,
        val remainingDays: Int = 12,
        val dailyAllowance: Long = 479000L,
        val spendingPaceStatus: String = "Tốc độ chi: Rất tốt",
        val budgets: List<Budget> = emptyList(),
        val savingsGoals: List<SavingsGoal> = emptyList(),
        val isLoading: Boolean = false
    )

    private val _state = MutableLiveData(BudgetState())
    val state: LiveData<BudgetState> = _state

    private var cachedTransactions: List<SavedTransaction> = emptyList()
    private var cachedBudgets: List<Budget> = emptyList()

    private var transactionSubscription: AutoCloseable? = null

    fun loadData() {
        if (transactionSubscription != null) return
        _state.value = _state.value?.copy(isLoading = true)
        val context = getApplication<Application>().applicationContext

        // 1. Listen to Transactions
        transactionSubscription = FinanceRepository.listenToTransactions(context) { transactions ->
            cachedTransactions = transactions
            recalculateBudgets()
        }

        // 2. Listen to Budgets
        FinanceRepository.listenToBudgets { budgetList ->
            if (budgetList.isNotEmpty()) {
                cachedBudgets = budgetList
                recalculateBudgets()
            }
        }

        // 3. Listen to Savings Goals
        FinanceRepository.listenToSavingsGoals { goalList ->
            val updatedState = _state.value?.copy(
                savingsGoals = goalList,
                isLoading = false
            ) ?: BudgetState()
            _state.postValue(updatedState)
        }
    }

    private fun recalculateBudgets() {
        val budgetsToProcess = if (cachedBudgets.isNotEmpty()) cachedBudgets else listOf(
            Budget("b1", Category.FOOD.id, "Ăn uống & Cà phê", 4000000L, 3100000L, "MONTHLY", "77% (Cận trần)"),
            Budget("b2", Category.TRANSPORT.id, "Di chuyển & Xăng xe", 2000000L, 950000L, "MONTHLY", "47% (An toàn)"),
            Budget("b3", Category.HOUSING.id, "Thuê nhà & Hóa đơn", 4000000L, 4000000L, "MONTHLY", "100% (Đạt mức trần)"),
            Budget("b4", Category.SHOPPING.id, "Mua sắm & Giải trí", 2500000L, 1200000L, "MONTHLY", "48% (Thong thả)")
        )

        val updatedBudgets = budgetsToProcess.map { budget ->
            val actualSpent = FinanceRepository.calculateCategoryMonthlySpent(cachedTransactions, budget.categoryId)
            val spent = actualSpent
            val percent = if (budget.limitAmount > 0) ((spent.toDouble() / budget.limitAmount) * 100).toInt() else 0
            val status = when {
                percent >= 100 -> "$percent% (Vượt trần)"
                percent >= 70 -> "$percent% (Cận trần)"
                else -> "$percent% (An toàn)"
            }
            budget.copy(spentAmount = spent, statusTag = status)
        }

        val totalLim = updatedBudgets.sumOf { it.limitAmount }.let { if (it > 0) it else 15000000L }
        val totalSp = updatedBudgets.sumOf { it.spentAmount }
        val safeRem = (totalLim - totalSp).coerceAtLeast(0L)

        val calendar = Calendar.getInstance()
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysLeft = (maxDays - currentDay + 1).coerceAtLeast(1)
        val dailyRec = safeRem / daysLeft

        val updatedState = BudgetState(
            totalLimit = totalLim,
            totalSpent = totalSp,
            safeRemaining = safeRem,
            remainingDays = daysLeft,
            dailyAllowance = dailyRec,
            budgets = updatedBudgets,
            savingsGoals = _state.value?.savingsGoals ?: emptyList(),
            isLoading = false
        )

        _state.postValue(updatedState)
    }

    fun saveNewBudget(categoryName: String, limitAmount: Long, onComplete: ((Boolean) -> Unit)? = null) {
        val budgetId = "b_${System.currentTimeMillis()}"
        val category = Category.fromName(categoryName)
        val newBudget = Budget(
            id = budgetId,
            categoryId = category.id,
            categoryName = categoryName,
            limitAmount = limitAmount,
            spentAmount = 0L,
            period = "MONTHLY",
            statusTag = "Mới tạo"
        )
        FinanceRepository.saveBudget(newBudget, onComplete)
    }

    fun updateBudgetLimit(budgetId: String, categoryName: String, newLimit: Long, onComplete: ((Boolean) -> Unit)? = null) {
        val currentSpent = _state.value?.budgets?.find { it.id == budgetId }?.spentAmount ?: 0L
        val category = Category.fromName(categoryName)
        val updatedBudget = Budget(
            id = budgetId,
            categoryId = category.id,
            categoryName = categoryName,
            limitAmount = newLimit,
            spentAmount = currentSpent,
            period = "MONTHLY",
            statusTag = "Đã cập nhật"
        )
        FinanceRepository.saveBudget(updatedBudget, onComplete)
    }

    fun depositToSavingsGoal(goalId: String, amount: Long, onComplete: ((Boolean) -> Unit)? = null) {
        FinanceRepository.depositToSavingsGoal(goalId, amount, onComplete)
    }
    override fun onCleared() {
        transactionSubscription?.close()
        super.onCleared()
    }
}
