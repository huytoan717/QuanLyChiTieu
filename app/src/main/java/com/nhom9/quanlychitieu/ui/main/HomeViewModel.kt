package com.nhom9.quanlychitieu.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.model.Category
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    data class HomeState(
        val recentTransactions: List<SavedTransaction> = emptyList(),
        val wallets: List<Wallet> = emptyList(),
        val netBalance: Long = 0L,
        val monthlyExpense: Long = 0L,
        val totalIncome: Long = 0L,
        val remainingLimit: Long = 0L,
        val transportSpent: Long = 0L,
        val transportPercent: Int = 0,
        val digitalSpent: Long = 0L,
        val digitalPercent: Int = 0,
        val rentSpent: Long = 0L,
        val rentPercent: Int = 0,
        val gameSpent: Long = 0L,
        val gamePercent: Int = 0,
        val pendingCount: Int = 0,
        val syncFailed: Boolean = false,
        val loadFailed: Boolean = false,
        val isLoading: Boolean = false
    )

    private val _state = MutableLiveData(HomeState())
    val state: LiveData<HomeState> = _state

    private var subscription: AutoCloseable? = null

    fun loadData() {
        if (subscription != null) return
        _state.value = _state.value?.copy(isLoading = true)
        val context = getApplication<Application>().applicationContext

        subscription = TransactionRepository.get(context).observe { snapshot ->
            if (snapshot.loadFailed) {
                _state.value = _state.value?.copy(loadFailed = true, isLoading = false)
                return@observe
            }
            val transactions = snapshot.transactions
            val net = WalletBalance.total(snapshot.wallets)
            val monthlyExp = FinanceRepository.calculateCurrentMonthExpense(transactions)
            val inc = FinanceRepository.calculateTotalIncome(transactions)
            val totalLimit = 15000000L
            val remaining = (totalLimit - monthlyExp).coerceAtLeast(0L)
            val recent = transactions.take(3)

            // Dynamic category calculations using unique Category IDs
            val transport = FinanceRepository.calculateCategoryMonthlySpent(transactions, Category.TRANSPORT.id)
            val digital = FinanceRepository.calculateCategoryMonthlySpent(transactions, Category.FOOD.id)
            val rent = FinanceRepository.calculateCategoryMonthlySpent(transactions, Category.HOUSING.id)
            val game = FinanceRepository.calculateCategoryMonthlySpent(transactions, Category.SHOPPING.id)

            val totalCatSum = (transport + digital + rent + game).coerceAtLeast(1L)
            val transP = ((transport.toDouble() / totalCatSum) * 100).toInt().coerceIn(0, 100)
            val digiP = ((digital.toDouble() / totalCatSum) * 100).toInt().coerceIn(0, 100)
            val rentP = ((rent.toDouble() / totalCatSum) * 100).toInt().coerceIn(0, 100)
            val gameP = ((game.toDouble() / totalCatSum) * 100).toInt().coerceIn(0, 100)

            _state.postValue(
                HomeState(
                    recentTransactions = recent,
                    wallets = snapshot.wallets,
                    netBalance = net,
                    monthlyExpense = monthlyExp,
                    totalIncome = inc,
                    remainingLimit = remaining,
                    transportSpent = transport,
                    transportPercent = transP,
                    digitalSpent = digital,
                    digitalPercent = digiP,
                    rentSpent = rent,
                    rentPercent = rentP,
                    gameSpent = game,
                    gamePercent = gameP,
                    pendingCount = snapshot.pendingIds.size,
                    syncFailed = snapshot.syncFailed,
                    loadFailed = false,
                    isLoading = false
                )
            )
        }
    }
    fun retry() = TransactionRepository.get(getApplication()).retrySync()

    override fun onCleared() {
        subscription?.close()
        super.onCleared()
    }
}
