package com.nhom9.quanlychitieu.ui.wallet

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.model.Wallet

class WalletEditViewModel(application: Application) : AndroidViewModel(application) {
    data class State(
        val wallet: Wallet? = null,
        val loading: Boolean = true,
        val loadFailed: Boolean = false,
        @StringRes val pending: Int? = null,
        @StringRes val completed: Int? = null,
        val error: String? = null
    )

    private val repository by lazy { TransactionRepository.get(application) }
    private val mutableState = MutableLiveData(State())
    val state: LiveData<State> = mutableState
    private val current get() = checkNotNull(mutableState.value)
    private var observation: AutoCloseable? = null

    fun attach(id: String?) {
        if (observation != null) return
        observation = repository.observe { snapshot ->
            mutableState.value = current.copy(
                wallet = snapshot.wallets.find { it.id == id },
                loading = false,
                loadFailed = snapshot.loadFailed
            )
        }
    }

    fun retry() {
        mutableState.value = current.copy(loading = true, loadFailed = false, error = null)
        repository.retrySync()
    }

    fun save(id: String?, name: String, openingBalance: Long) {
        if (current.pending != null) return
        mutableState.value = current.copy(pending = R.string.transaction_saving, error = null)
        repository.saveWallet(id, name, openingBalance) { result ->
            finishChange(result, R.string.wallet_saved, R.string.wallet_save_failed)
        }
    }

    fun delete(id: String) {
        if (current.pending != null) return
        mutableState.value = current.copy(pending = R.string.wallet_deleting, error = null)
        repository.deleteWallet(id) { result ->
            finishChange(result, R.string.wallet_deleted, R.string.wallet_delete_failed)
        }
    }

    private fun finishChange(result: Result<Unit>, @StringRes success: Int, @StringRes failure: Int) {
        val exception = result.exceptionOrNull()
        mutableState.value = current.copy(
            pending = null,
            completed = success.takeIf { exception == null },
            error = exception?.let {
                (it as? IllegalArgumentException)?.message ?: getApplication<Application>().getString(failure)
            }
        )
    }

    override fun onCleared() {
        observation?.close()
        super.onCleared()
    }
}
