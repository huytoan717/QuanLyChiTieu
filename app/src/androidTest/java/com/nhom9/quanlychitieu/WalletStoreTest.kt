package com.nhom9.quanlychitieu

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhom9.quanlychitieu.data.TransactionStore
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WalletStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "wallet-test-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesTransactionsAndCreatesWalletsForLegacyReferences() {
        val income = transaction("income", 2_000_000, TransactionType.INCOME, "Ví sổ tay")
        val transfer = transaction("transfer", 300_000, TransactionType.TRANSFER, "Ví sổ tay", "Ví tiết kiệm")
        val deleted = transaction("deleted", 50_000, TransactionType.EXPENSE, "Ví đã xóa")
        val path = context.getDatabasePath(databaseName)
        check(path.parentFile!!.isDirectory || path.parentFile!!.mkdirs())
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL("""
                CREATE TABLE transactions (
                    id TEXT PRIMARY KEY NOT NULL, amount INTEGER NOT NULL CHECK(amount > 0),
                    type TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL,
                    source_wallet TEXT NOT NULL, destination_wallet TEXT, occurred_at INTEGER NOT NULL,
                    exclude_budget INTEGER NOT NULL, revision INTEGER NOT NULL DEFAULT 0,
                    dirty INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0,
                    remote_known INTEGER NOT NULL DEFAULT 0, category_id TEXT
                )
            """.trimIndent())
            for (row in listOf(income, transfer, deleted)) {
                database.execSQL(
                    """INSERT INTO transactions (id, amount, type, category, note, source_wallet,
                        destination_wallet, occurred_at, exclude_budget, revision, dirty, deleted,
                        remote_known, category_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    arrayOf<Any?>(row.id, row.amount, row.type.name, row.category, row.note, row.sourceWallet,
                        row.destinationWallet, row.occurredAt, 0, 1, if (row == transfer) 1 else 0,
                        if (row == deleted) 1 else 0, 0, row.categoryId)
                )
            }
            database.version = 2
        }

        withStore { store ->
            assertEquals(setOf(income, transfer), store.all().toSet())
            assertEquals(transfer, store.pending().single().transaction)
            val wallets = store.wallets()
            assertEquals(setOf("Tiền mặt", "Tài khoản cá nhân", "Ví điện tử", "Ví sổ tay", "Ví tiết kiệm"),
                wallets.map { it.name }.toSet())
            assertTrue(wallets.all { it.openingBalance == 0L })
            assertEquals(1_700_000L, store.wallet("Ví sổ tay").balance)
            assertEquals(300_000L, store.wallet("Ví tiết kiệm").balance)
        }
    }

    @Test
    fun openingBalanceAndRenameSurviveReopeningAndKeepTransactionReferences() {
        withStore { store ->
            store.saveWallet(null, "  Ví sinh hoạt  ", 1_000_000L)
            val wallet = store.wallet("Ví sinh hoạt")
            store.createPending(transaction("expense", 200_000, TransactionType.EXPENSE, wallet.id))
        }

        val originalId = withStore { store ->
            val wallet = store.wallet("Ví sinh hoạt")
            assertEquals(1_000_000L, wallet.openingBalance)
            assertEquals(800_000L, wallet.balance)
            store.saveWallet(wallet.id, "Ví chi tiêu", 2_000_000L)
            wallet.id
        }

        withStore { store ->
            val wallet = store.wallet("Ví chi tiêu")
            assertFalse(store.wallets().any { it.name == "Ví sinh hoạt" })
            assertEquals(originalId, wallet.id)
            assertEquals(originalId, store.find("expense")?.sourceWallet)
            assertEquals(2_000_000L, wallet.openingBalance)
            assertEquals(1_800_000L, wallet.balance)
        }
    }

    @Test
    fun balancesReflectIncomeExpenseTransfersEditsAndDeletes() = withStore { store ->
        store.saveWallet(null, "Ví sinh hoạt", 1_000_000L)
        val source = store.wallet("Ví sinh hoạt").id
        val destination = store.wallet("Tài khoản cá nhân").id
        val income = transaction("income", 500_000, TransactionType.INCOME, source)
        val expense = transaction("expense", 200_000, TransactionType.EXPENSE, source)
            .copy(excludeFromBudget = true)
        val transfer = transaction("transfer", 300_000, TransactionType.TRANSFER, source, destination)
        store.createPending(income)
        store.createPending(expense)
        assertEquals(1_300_000L, WalletBalance.total(store.wallets()))

        store.createPending(transfer)
        assertEquals(1_000_000L, store.wallet("Ví sinh hoạt").balance)
        assertEquals(300_000L, store.wallet("Tài khoản cá nhân").balance)
        assertEquals(1_300_000L, WalletBalance.total(store.wallets()))

        store.update(expense.copy(amount = 250_000), expense)
        assertEquals(950_000L, store.wallet("Ví sinh hoạt").balance)
        assertEquals(1_250_000L, WalletBalance.total(store.wallets()))

        store.delete(transfer.id)
        assertEquals(1_250_000L, store.wallet("Ví sinh hoạt").balance)
        assertEquals(0L, store.wallet("Tài khoản cá nhân").balance)
        assertEquals(1_250_000L, WalletBalance.total(store.wallets()))
    }

    @Test
    fun invalidValuesAndUnsafeDeletesLeaveBalancesUnchanged() = withStore { store ->
        val startingWallets = store.wallets()
        assertThrows(IllegalArgumentException::class.java) { store.saveWallet(null, "  ", 0L) }
        assertThrows(IllegalArgumentException::class.java) { store.saveWallet(null, "Ví sai", -1L) }
        assertThrows(IllegalArgumentException::class.java) { store.saveWallet(null, "Ví sai", 1_000_000_000_001L) }
        assertThrows(IllegalArgumentException::class.java) { store.saveWallet(null, "  TIỀN MẶT ", 0L) }
        assertEquals(startingWallets, store.wallets())

        store.saveWallet(null, "Ví có tiền", 10_000L)
        val funded = store.wallet("Ví có tiền")
        assertThrows(IllegalArgumentException::class.java) { store.deleteWallet(funded.id) }

        store.createPending(transaction("expense", 10_000, TransactionType.EXPENSE, funded.id))
        assertEquals(0L, store.wallet("Ví có tiền").balance)
        assertThrows(IllegalArgumentException::class.java) { store.deleteWallet(funded.id) }

        store.saveWallet(null, "Ví để trống", 0L)
        val emptyId = store.wallet("Ví để trống").id
        store.deleteWallet(emptyId)
        assertFalse(store.wallets().any { it.id == emptyId })

        val stale = transaction("stale-selection", 1_000, TransactionType.INCOME, emptyId)
        assertThrows(IllegalArgumentException::class.java) { store.createPending(stale) }
        assertNull(store.find(stale.id))
        assertEquals(0L, store.wallet("Ví có tiền").balance)
    }

    @Test
    fun staleRemoteRowsDoNotOverridePendingAmountsOrRestoreDeletedBalances() = withStore { store ->
        val cash = store.wallet("Tiền mặt")
        store.saveWallet(cash.id, cash.name, 1_000_000L)
        val remoteTip = transaction("tip", 10_000, TransactionType.INCOME, cash.id)
        store.mergeRemote(listOf(remoteTip), authoritative = true)
        assertEquals(1_010_000L, store.wallet(cash.name).balance)

        store.update(remoteTip.copy(amount = 20_000), remoteTip)
        store.mergeRemote(listOf(remoteTip), authoritative = true)
        assertEquals(1_020_000L, store.wallet(cash.name).balance)

        store.delete(remoteTip.id)
        store.mergeRemote(listOf(remoteTip), authoritative = false)
        assertEquals(1_000_000L, store.wallet(cash.name).balance)

        store.acknowledge(store.pending().single())
        store.mergeRemote(listOf(remoteTip), authoritative = true)
        assertNull(store.find(remoteTip.id))
        assertEquals(1_000_000L, store.wallet(cash.name).balance)
    }

    private fun TransactionStore.wallet(name: String): Wallet = wallets().single { it.name == name }

    @Test fun importedWalletNamesStayDistinctAfterRenameAndCaseDifferences() = withStore { store ->
        store.saveWallet(null, "Cash", 0L)
        store.saveWallet("Cash", "Travel", 0L)
        val first = transaction("one", 100L, TransactionType.INCOME, "Travel")
        val second = transaction("two", 200L, TransactionType.INCOME, "cash")
        store.mergeRemote(listOf(first, second), true)
        val wallets = store.wallets()
        assertEquals(wallets.size, wallets.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet().size)
        assertEquals("Cash", wallets.single { it.name == "Travel" }.id)
        assertEquals(100L, wallets.single { it.id == "Travel" }.balance)
        assertEquals(200L, wallets.single { it.id == "cash" }.balance)
        assertEquals(first, store.find(first.id))
        store.saveWallet("Cash", "Travel", 500L)
        assertEquals(500L, store.wallets().single { it.id == "Cash" }.balance)
    }

    private fun <T> withStore(action: (TransactionStore) -> T): T =
        TransactionStore(context, databaseName).use(action)

    private fun transaction(
        id: String,
        amount: Long,
        type: TransactionType,
        source: String,
        destination: String? = null
    ) = SavedTransaction(
        id = id,
        amount = amount,
        type = type,
        category = when (type) {
            TransactionType.EXPENSE -> "Ăn uống"
            TransactionType.INCOME -> "Lương"
            TransactionType.TRANSFER -> "Chuyển khoản"
        },
        note = "Giao dịch kiểm thử",
        sourceWallet = source,
        destinationWallet = destination,
        occurredAt = 1_784_000_000_000L,
        excludeFromBudget = false
    )
}
