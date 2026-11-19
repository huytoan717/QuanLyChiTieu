package com.nhom9.quanlychitieu

import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance
import com.nhom9.quanlychitieu.model.VndAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WalletBalanceTest {
    @Test fun emptyHistoryHasNoSampleMoney() {
        assertEquals(0L, FinanceRepository.calculateNetBalance(emptyList()))
    }

    @Test fun incomeAndExpenseStartFromActualOpeningBalance() {
        val income = transaction("income", 500_000L, TransactionType.INCOME)
        val expense = transaction("expense", 200_000L, TransactionType.EXPENSE)
        assertEquals(300_000L, FinanceRepository.calculateNetBalance(listOf(income, expense)))
    }

    @Test fun transfersDoNotCreateMoney() {
        val transfer = transaction("transfer", 300_000L, TransactionType.TRANSFER)
        assertEquals(0L, FinanceRepository.calculateNetBalance(listOf(transfer)))
    }

    @Test fun walletBalanceIncludesIncomeExpenseAndBothTransferDirections() {
        val cash = Wallet(id = "Tiền mặt", name = "Tiền ăn", openingBalance = 1_000_000L)
        val bank = Wallet(id = "Tài khoản cá nhân", name = "Ngân hàng", openingBalance = 2_000_000L)
        val rows = listOf(transaction("income", 500_000L, TransactionType.INCOME),
            transaction("expense", 200_000L, TransactionType.EXPENSE).copy(excludeFromBudget = true),
            transaction("out", 300_000L, TransactionType.TRANSFER),
            transaction("in", 100_000L, TransactionType.TRANSFER).copy(
                sourceWallet = bank.id, destinationWallet = cash.id))
        val cashBalance = WalletBalance.calculate(cash, rows)
        val bankBalance = WalletBalance.calculate(bank, rows)
        assertEquals(1_100_000L, cashBalance)
        assertEquals(2_200_000L, bankBalance)
        assertEquals(3_300_000L, WalletBalance.total(listOf(cash.copy(balance = cashBalance), bank.copy(balance = bankBalance))))
    }

    @Test fun editingMovingAndDeletingTransactionRecalculateFromOriginalBalance() {
        val cash = Wallet(id = "Tiền mặt", openingBalance = 1_000_000L)
        val original = transaction("same-id", 200_000L, TransactionType.EXPENSE)
        assertEquals(800_000L, WalletBalance.calculate(cash, listOf(original)))
        assertEquals(700_000L, WalletBalance.calculate(cash, listOf(original.copy(amount = 300_000L))))
        assertEquals(1_000_000L, WalletBalance.calculate(cash, listOf(original.copy(sourceWallet = "Tài khoản cá nhân"))))
        assertEquals(1_000_000L, WalletBalance.calculate(cash, emptyList()))
    }

    @Test fun showsNegativeBalanceAndCountsTransactionsOlderThanRecentFifty() {
        val cash = Wallet(id = "Tiền mặt")
        val rows = (1..60).map { transaction("$it", 1_000L, TransactionType.EXPENSE) }
        assertEquals(-60_000L, WalletBalance.calculate(cash, rows))
    }

    @Test fun largeBalancesNeverSilentlyWrapAround() {
        assertThrows(ArithmeticException::class.java) {
            WalletBalance.calculate(Wallet(id = "Tiền mặt", openingBalance = 1L),
                listOf(transaction("large", Long.MAX_VALUE, TransactionType.INCOME)))
        }
        assertThrows(ArithmeticException::class.java) {
            WalletBalance.total(listOf(Wallet(balance = Long.MAX_VALUE), Wallet(balance = 1L)))
        }
    }

    @Test fun openingBalanceParserAllowsZeroAndRejectsMalformedMoney() {
        assertEquals(0L, VndAmount.parseNonNegative("0"))
        assertEquals(1_000_000L, VndAmount.parseNonNegative("1.000.000"))
        for (raw in listOf("", "-100", "1.5", "1,5", "9223372036854775808")) {
            assertNull(VndAmount.parseNonNegative(raw))
        }
    }

    private fun transaction(id: String, amount: Long, type: TransactionType) = SavedTransaction(
        id, amount, type, "Ăn uống", "", "Tiền mặt", "Tài khoản cá nhân", 1L, false)
}
