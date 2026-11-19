package com.nhom9.quanlychitieu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhom9.quanlychitieu.data.TransactionStore
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TransactionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-transactions-${UUID.randomUUID()}.db"
    private val record = SavedTransaction("draft-1", 3_000_000_000L, TransactionType.INCOME,
        "Lương", "Tháng 10", "Tiền mặt", null, 1792573200000L, true)

    @After fun cleanUp() { context.deleteDatabase(dbName) }

    @Test fun saveSurvivesDatabaseReopening() {
        TransactionStore(context, dbName).use { it.save(record) }
        TransactionStore(context, dbName).use { assertEquals(listOf(record), it.recent()) }
    }

    @Test fun retryingDraftKeepsOneRecord() {
        TransactionStore(context, dbName).use {
            it.save(record)
            it.save(record)
            assertEquals(listOf(record), it.recent())
            assertEquals(record, it.find(record.id))
        }
    }

    @Test fun restoredDraftCannotOverwriteCommittedTransaction() {
        TransactionStore(context, dbName).use {
            it.save(record)
            assertThrows(IllegalStateException::class.java) {
                it.save(record.copy(note = "Changed after process recreation"))
            }
            assertEquals(record, it.find(record.id))
        }
    }

    @Test fun transferRequiresDifferentWallets() {
        TransactionStore(context, dbName).use { store ->
            assertThrows(IllegalArgumentException::class.java) {
                store.save(record.copy(type = TransactionType.TRANSFER, destinationWallet = "Tiền mặt"))
            }
        }
    }
}
