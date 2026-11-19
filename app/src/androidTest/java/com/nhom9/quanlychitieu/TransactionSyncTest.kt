package com.nhom9.quanlychitieu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom9.quanlychitieu.data.*
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TransactionSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = "sync-${UUID.randomUUID()}.db"
    private val remote = ControlledRemote()
    private var repository = TransactionRepository(context, database, remote)
    private val row = SavedTransaction("sync-row", 100000L, TransactionType.INCOME,
        "Lương", "Initial", "Tiền mặt", null, System.currentTimeMillis(), false)

    @After fun close() {
        repository.close()
        drain()
        context.deleteDatabase(database)
    }

    @Test fun editDuringInFlightCreateThenDeleteCannotBeAcknowledgedByOldResponse() {
        var latest: TransactionSnapshot? = null
        repository.observe { latest = it }.use {
            complete { repository.create(row, it) }
            drain()
            assertEquals(1, remote.writes.size)
            complete { repository.update(row.copy(note = "Changed"), row, it) }
            complete { repository.delete(row.id, it) }
            drain()
            assertTrue(requireNotNull(latest).transactions.isEmpty())
            assertTrue(row.id in requireNotNull(latest).pendingIds)
            remote.finish(0, true)
            drain()
            assertEquals(2, remote.writes.size)
            assertTrue(remote.writes[1].first.deleted)
            assertTrue(row.id in requireNotNull(latest).pendingIds)
            remote.finish(1, true)
            drain()
            assertTrue(requireNotNull(latest).pendingIds.isEmpty())
            remote.emit(listOf(row), true)
            drain()
            assertTrue(requireNotNull(latest).transactions.isEmpty())
        }
    }

    @Test fun failedDeleteIsRetriedAfterRepositoryRestart() {
        TransactionStore(context, database).use { it.save(row) }
        complete { repository.delete(row.id, it) }
        drain()
        remote.finish(0, false)
        drain()
        repository.close()
        repository = TransactionRepository(context, database, remote)
        var latest: TransactionSnapshot? = null
        repository.observe { latest = it }.use {
            drain()
            assertEquals(2, remote.writes.size)
            assertTrue(remote.writes[1].first.deleted)
            remote.finish(1, true)
            drain()
            assertTrue(requireNotNull(latest).transactions.isEmpty())
            assertTrue(requireNotNull(latest).pendingIds.isEmpty())
        }
    }

    @Test fun twoObserversSeeEditThenEmptyListAndClosedObserverStopsReceiving() {
        TransactionStore(context, database).use { it.save(row) }
        var first: TransactionSnapshot? = null
        var second: TransactionSnapshot? = null
        val a = repository.observe { first = it }
        repository.observe { second = it }.use {
            drain()
            complete { repository.update(row.copy(amount = 200000L), row, it) }
            drain()
            assertEquals(200000L, requireNotNull(first).transactions.single().amount)
            assertEquals(first, second)
            a.close()
            complete { repository.delete(row.id, it) }
            drain()
            assertEquals(1, requireNotNull(first).transactions.size)
            assertTrue(requireNotNull(second).transactions.isEmpty())
        }
    }

    private fun complete(action: ((Result<Unit>) -> Unit) -> Unit) {
        val latch = CountDownLatch(1)
        var result: Result<Unit>? = null
        action { result = it; latch.countDown() }
        assertTrue("Local mutation completed", latch.await(10, TimeUnit.SECONDS))
        result!!.getOrThrow()
    }

    private fun drain() {
        repeat(4) {
            TransactionStore.io.submit {}.get(10, TimeUnit.SECONDS)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    private class ControlledRemote : TransactionRemote {
        val writes = mutableListOf<Pair<TransactionStore.Pending, (Boolean) -> Unit>>()
        private var listener: ((List<SavedTransaction>, Boolean, Boolean) -> Unit)? = null
        override fun listen(onSnapshot: (List<SavedTransaction>, Boolean, Boolean) -> Unit, onError: () -> Unit) {
            listener = onSnapshot
        }
        override fun write(pending: TransactionStore.Pending, onComplete: (Boolean) -> Unit) {
            writes.add(pending to onComplete)
        }
        fun finish(index: Int, success: Boolean) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
            writes[index].second(success)
        }
        fun emit(rows: List<SavedTransaction>, authoritative: Boolean) =
            InstrumentationRegistry.getInstrumentation().runOnMainSync { listener?.invoke(rows, authoritative, true) }
        override fun close() { listener = null }
    }
}
