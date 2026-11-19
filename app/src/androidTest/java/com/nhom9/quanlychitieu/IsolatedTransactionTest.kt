package com.nhom9.quanlychitieu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom9.quanlychitieu.data.TransactionRemote
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.data.TransactionStore
import com.nhom9.quanlychitieu.model.SavedTransaction
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class IsolatedTransactionTest : AutoCloseable {
    val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "transaction-ui-${UUID.randomUUID()}.db"
    private val repository = TransactionRepository(context, databaseName, OfflineRemote)
    private val previous = TransactionRepository.replaceInstance(repository)

    fun seed(vararg transactions: SavedTransaction) {
        withStore { store -> transactions.forEach(store::save) }
    }

    fun <T> withStore(action: (TransactionStore) -> T): T =
        TransactionStore(context, databaseName).use(action)

    fun awaitLocalWork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // A main-thread remote callback can schedule another store read and publish.
        repeat(3) {
            TransactionStore.io.submit {}.get(10, TimeUnit.SECONDS)
            instrumentation.waitForIdleSync()
        }
    }

    override fun close() {
        try {
            repository.close()
            awaitLocalWork()
            context.deleteDatabase(databaseName)
        } finally {
            TransactionRepository.replaceInstance(previous)
        }
    }

    private object OfflineRemote : TransactionRemote {
        override fun listen(
            onSnapshot: (List<SavedTransaction>, Boolean, Boolean) -> Unit,
            onError: () -> Unit
        ) = Unit

        override fun write(pending: TransactionStore.Pending, onComplete: (Boolean) -> Unit) {
            onComplete(false)
        }

        override fun close() = Unit
    }
}
