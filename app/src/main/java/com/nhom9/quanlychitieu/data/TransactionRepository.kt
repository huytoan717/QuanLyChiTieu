package com.nhom9.quanlychitieu.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.Wallet
import java.util.concurrent.CopyOnWriteArrayList

data class TransactionSnapshot(
    val transactions: List<SavedTransaction>,
    val pendingIds: Set<String>,
    val syncFailed: Boolean = false,
    val loadFailed: Boolean = false,
    val wallets: List<Wallet> = emptyList()
)

/** One local source for every screen; remote changes never overwrite pending local work. */
class TransactionRepository(
    context: Context,
    private val databaseName: String = "transactions.db",
    private val remote: TransactionRemote = FirestoreTransactionRemote()
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(TransactionSnapshot) -> Unit>()
    private var started = false
    private var syncing = false
    private var syncFailed = false
    @Volatile private var closed = false

    fun observe(listener: (TransactionSnapshot) -> Unit): AutoCloseable {
        listeners.add(listener)
        TransactionStore.io.execute { publish(); startSync() }
        return AutoCloseable { listeners.remove(listener) }
    }

    fun read(id: String, onComplete: (Result<SavedTransaction?>) -> Unit) {
        TransactionStore.io.execute {
            val result = runCatching { store { it.find(id) } }
            main.post { if (!closed) onComplete(result) }
        }
    }

    fun readAll(onComplete: (Result<List<SavedTransaction>>) -> Unit) {
        TransactionStore.io.execute {
            val result = runCatching { store { it.all() } }
            main.post { if (!closed) onComplete(result) }
        }
    }

    fun containsDraft(id: String, onComplete: (Result<Boolean>) -> Unit) {
        TransactionStore.io.execute {
            val result = runCatching { store { it.containsDraft(id) } }
            main.post { if (!closed) onComplete(result) }
        }
    }

    fun create(transaction: SavedTransaction, onComplete: (Result<Unit>) -> Unit) =
        mutate(onComplete) { it.createPending(transaction) }

    fun update(transaction: SavedTransaction, expected: SavedTransaction?, onComplete: (Result<Unit>) -> Unit) =
        mutate(onComplete) { it.update(transaction, expected) }

    fun delete(id: String, onComplete: (Result<Unit>) -> Unit) = mutate(onComplete) { it.delete(id) }

    fun saveWallet(id: String?, name: String, openingBalance: Long, onComplete: (Result<Unit>) -> Unit) =
        mutate(onComplete) { it.saveWallet(id, name, openingBalance) }

    fun deleteWallet(id: String, onComplete: (Result<Unit>) -> Unit) =
        mutate(onComplete) { it.deleteWallet(id) }

    private fun mutate(onComplete: (Result<Unit>) -> Unit, action: (TransactionStore) -> Unit) {
        TransactionStore.io.execute {
            val result = runCatching { store(action) }
            publish()
            main.post { if (!closed) onComplete(result) }
            if (result.isSuccess) startSync()
        }
    }

    fun retrySync() {
        TransactionStore.io.execute {
            syncFailed = false
            if (!syncing) {
                remote.close()
                started = false
            }
            startSync()
            publish()
        }
    }

    private fun startSync() {
        if (closed) return
        if (!started) {
            started = true
            main.post {
                if (!closed) remote.listen({ rows, authoritative, complete ->
                    TransactionStore.io.execute {
                        if (!closed) {
                            val result = runCatching { store { it.mergeRemote(rows, authoritative, complete) } }
                            if (result.isFailure) syncFailed = true
                            publish()
                            flush()
                        }
                    }
                }, {
                    TransactionStore.io.execute { syncFailed = true; publish() }
                })
            }
        }
        flush()
    }

    private fun flush() {
        if (closed || syncing) return
        val pending = runCatching { store { it.pending().firstOrNull() } }.getOrElse {
            syncFailed = true
            publish()
            return
        } ?: return
        syncing = true
        main.post {
            if (!closed) remote.write(pending) { success ->
                TransactionStore.io.execute {
                    if (!closed) {
                        syncing = false
                        syncFailed = !success
                        val acknowledged = success && runCatching { store { it.acknowledge(pending) } }.isSuccess
                        if (success && !acknowledged) syncFailed = true
                        publish()
                        if (acknowledged) flush()
                    }
                }
            }
        }
    }

    private fun publish() {
        if (closed) return
        val snapshot = runCatching {
            store { TransactionSnapshot(it.all(), it.pending().map { p -> p.transaction.id }.toSet(),
                syncFailed, wallets = it.wallets()) }
        }.getOrElse { TransactionSnapshot(emptyList(), emptySet(), syncFailed, loadFailed = true) }
        main.post { if (!closed) listeners.forEach { it(snapshot) } }
    }

    private fun <T> store(action: (TransactionStore) -> T): T =
        TransactionStore(appContext, databaseName).use(action)

    override fun close() {
        closed = true
        listeners.clear()
        remote.close()
    }

    companion object {
        @Volatile private var instance: TransactionRepository? = null

        fun get(context: Context): TransactionRepository = instance ?: synchronized(this) {
            instance ?: TransactionRepository(context).also { instance = it }
        }

        /** Allows an isolated database/transport in instrumentation, without touching user data. */
        internal fun replaceInstance(repository: TransactionRepository?): TransactionRepository? = synchronized(this) {
            val previous = instance
            instance = repository
            previous
        }
    }
}
