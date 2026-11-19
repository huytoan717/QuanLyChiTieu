package com.nhom9.quanlychitieu

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
class TransactionCrudStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "crud-${UUID.randomUUID()}.db"
    private val row = SavedTransaction("original", 150000L, TransactionType.EXPENSE,
        "Ăn uống", "Cà phê", "Ví cũ", null, System.currentTimeMillis(), false)
    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test fun updateKeepsIdAndPersistsEveryEditableField() {
        val changed = row.copy(amount = 500000L, type = TransactionType.TRANSFER, category = "Chuyển khoản",
            note = "Đã sửa", sourceWallet = "Tiền mặt", destinationWallet = "Ví điện tử", occurredAt = 123456L,
            excludeFromBudget = false, categoryId = "cat_transport")
        TransactionStore(context, name).use { it.save(row); it.update(changed, row) }
        TransactionStore(context, name).use {
            assertEquals(listOf(changed), it.all())
            assertEquals(changed, it.pending().single().transaction)
        }
    }

    @Test fun deletionSurvivesReopeningAndStaleCloudSnapshots() {
        TransactionStore(context, name).use { it.save(row); it.delete(row.id) }
        TransactionStore(context, name).use {
            assertNull(it.find(row.id))
            assertTrue(it.containsDraft(row.id))
            assertTrue(it.pending().single().deleted)
            it.mergeRemote(listOf(row), false)
            assertTrue(it.all().isEmpty())
            it.acknowledge(it.pending().single())
            it.mergeRemote(listOf(row), true)
            assertTrue(it.all().isEmpty())
        }
    }

    @Test fun oldAcknowledgementDoesNotClearNewerDelete() {
        TransactionStore(context, name).use {
            it.saveWallet(null, row.sourceWallet, 0L)
            it.createPending(row)
            val old = it.pending().single()
            it.update(row.copy(amount = 200000L), row)
            it.delete(row.id)
            it.acknowledge(old)
            assertTrue(it.pending().single().deleted)
            assertTrue(it.pending().single().revision > old.revision)
        }
    }

    @Test fun pendingEditWinsOverOlderRemoteData() {
        val changed = row.copy(note = "Đã sửa ngoại tuyến")
        TransactionStore(context, name).use {
            it.save(row)
            it.update(changed, row)
            it.mergeRemote(listOf(row), true)
            assertEquals(changed, it.find(row.id))
        }
    }

    @Test fun remoteEditsAndDeletesRefreshOnlyAcknowledgedRecords() {
        TransactionStore(context, name).use {
            it.save(row.copy(id = "legacy-local-only"))
            it.mergeRemote(listOf(row), true)
            it.mergeRemote(listOf(row.copy(note = "Remote edit")), true)
            assertEquals("Remote edit", it.find(row.id)?.note)
            it.mergeRemote(emptyList(), false)
            assertNotNull(it.find(row.id))
            it.mergeRemote(emptyList(), true)
            assertNull(it.find(row.id))
            assertNotNull(it.find("legacy-local-only"))
        }
    }

    @Test fun staleEditOrInvalidTransferCannotOverwriteSavedRecord() {
        TransactionStore(context, name).use {
            it.save(row)
            val newer = row.copy(note = "Newer")
            it.update(newer, row)
            assertThrows(IllegalStateException::class.java) { it.update(row.copy(amount = 1L), row) }
            assertThrows(IllegalArgumentException::class.java) {
                it.update(newer.copy(type = TransactionType.TRANSFER, destinationWallet = newer.sourceWallet), newer)
            }
            assertEquals(newer, it.find(row.id))
            it.delete(row.id)
            assertThrows(IllegalStateException::class.java) { it.update(newer) }
        }
    }

    @Test fun migrationPreservesVersionOneDataAndAllowsEditing() {
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY NOT NULL, amount INTEGER NOT NULL CHECK(amount > 0), type TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, source_wallet TEXT NOT NULL, destination_wallet TEXT, occurred_at INTEGER NOT NULL, exclude_budget INTEGER NOT NULL)")
            db.execSQL("INSERT INTO transactions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
                row.id, row.amount, row.type.name, row.category, row.note, row.sourceWallet, null, row.occurredAt, 0))
            db.version = 1
        }
        TransactionStore(context, name).use {
            assertEquals(row, it.find(row.id))
            assertTrue(it.pending().isEmpty())
            it.update(row.copy(amount = 200000L), row)
            assertEquals(200000L, it.find(row.id)?.amount)
        }
    }

    @Test fun summariesCanReadMoreThanFiftyTransactions() {
        TransactionStore(context, name).use {
            repeat(60) { index -> it.save(row.copy(id = "row-$index")) }
            assertEquals(60, it.all().size)
            assertEquals(50, it.recent().size)
        }
    }

    @Test fun explicitCategoryIdSurvivesStorageAndNoteEdit() {
        val original = row.copy(category = "Danh mục riêng", categoryId = "custom-category-42")
        TransactionStore(context, name).use { it.save(original); it.update(original.copy(note = "Updated"), original) }
        TransactionStore(context, name).use {
            assertEquals(original.copy(note = "Updated"), it.find(original.id))
        }
    }

    @Test fun incompleteServerSnapshotUpdatesValidRowsWithoutDeletingMissingRows() {
        TransactionStore(context, name).use {
            it.mergeRemote(listOf(row, row.copy(id = "other")), true)
            it.mergeRemote(listOf(row.copy(note = "Server edit")), true, complete = false)
            assertEquals("Server edit", it.find(row.id)?.note)
            assertNotNull(it.find("other"))
            it.mergeRemote(listOf(row), false)
            assertEquals("Server edit", it.find(row.id)?.note)
        }
    }
}
