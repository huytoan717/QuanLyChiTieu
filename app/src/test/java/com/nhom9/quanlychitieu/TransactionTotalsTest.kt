package com.nhom9.quanlychitieu

import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.SpendingCaps
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TransactionTotalsTest {
    private fun expense(id: String, at: Long, excluded: Boolean = false) = SavedTransaction(
        id, 100_000L, TransactionType.EXPENSE, "Ăn uống", "", "Tiền mặt", null, at, excluded)

    @Test fun deletingLastCurrentMonthExpenseDoesNotRestoreOlderSpending() {
        val previousMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
        assertEquals(0L, FinanceRepository.calculateCurrentMonthExpense(listOf(expense("old", previousMonth))))
    }

    @Test fun budgetTotalsIgnoreExcludedAndPreviousMonthTransactions() {
        val now = System.currentTimeMillis()
        val previousMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
        val rows = listOf(expense("old", previousMonth), expense("excluded", now, true), expense("current", now))
        assertEquals(100_000L, FinanceRepository.calculateCategoryMonthlySpent(rows, "cat_food"))
    }

    @Test fun removingLastExpenseProducesZeroCategoryTotal() {
        assertEquals(0L, FinanceRepository.calculateCategoryMonthlySpent(emptyList(), "cat_food"))
    }

    @Test fun capCheckUsesSelectedTransactionDate() {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        val rows = listOf(expense("today", System.currentTimeMillis()).copy(amount = 900_000L))
        val caps = SpendingCaps(false, 2_000_000L, true, 1_000_000L)
        assertTrue(FinanceRepository.checkCaps(200_000L, rows, caps, yesterday).isAllowed)
        assertFalse(FinanceRepository.checkCaps(200_000L, rows, caps).isAllowed)
    }
}
