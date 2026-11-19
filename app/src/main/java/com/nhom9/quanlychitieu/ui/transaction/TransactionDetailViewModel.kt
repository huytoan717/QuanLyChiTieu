package com.nhom9.quanlychitieu.ui.transaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.Wallet

class TransactionDetailViewModel(application: Application) : AndroidViewModel(application) {

    enum class DeleteState { IDLE, DELETING, DELETED, FAILED }

    data class State(
        val transaction: SavedTransaction? = null,
        val wallets: List<Wallet> = emptyList(),
        val loading: Boolean = true,
        val loadFailed: Boolean = false,
        val pending: Boolean = false,
        val syncFailed: Boolean = false,
        val deletion: DeleteState = DeleteState.IDLE
    )

    private val repository by lazy { TransactionRepository.get(application) }
    private var observation: AutoCloseable? = null
    private val mutableState = MutableLiveData(State())
    val state: LiveData<State> = mutableState

    fun attach(id: String?) {
        if (observation != null) return
        if (id.isNullOrBlank()) {
            mutableState.value = State(loading = false)
            return
        }

        observation = repository.observe { snapshot ->
            val previous = checkNotNull(mutableState.value)
            mutableState.value = previous.copy(
                transaction = snapshot.transactions.find { it.id == id },
                wallets = snapshot.wallets,
                loading = false,
                loadFailed = snapshot.loadFailed,
                pending = id in snapshot.pendingIds,
                syncFailed = snapshot.syncFailed
            )
        }
    }

    fun retry() {
        val current = checkNotNull(mutableState.value)
        if (current.deletion == DeleteState.DELETING) return
        mutableState.value = current.copy(
            loading = current.loadFailed,
            loadFailed = false,
            syncFailed = false
        )
        repository.retrySync()
    }

    fun delete(transaction: SavedTransaction) {
        val current = checkNotNull(mutableState.value)
        if (current.transaction != transaction || current.loadFailed ||
            current.deletion == DeleteState.DELETING || current.deletion == DeleteState.DELETED
        ) return

        mutableState.value = current.copy(deletion = DeleteState.DELETING)
        repository.delete(transaction.id) { result ->
            val latest = checkNotNull(mutableState.value)
            mutableState.value = latest.copy(
                deletion = if (result.isSuccess) DeleteState.DELETED else DeleteState.FAILED
            )
        }
    }

    override fun onCleared() {
        observation?.close()
        super.onCleared()
    }
}
