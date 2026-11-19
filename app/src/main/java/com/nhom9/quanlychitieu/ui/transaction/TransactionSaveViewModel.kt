package com.nhom9.quanlychitieu.ui.transaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType

/** Keeps loading, confirmation and persistence independent of Activity recreation. */
class TransactionSaveViewModel(application: Application) : AndroidViewModel(application) {
    enum class State { IDLE, CHECKING, CONFIRMING, SAVING, SAVED, FAILED, LOAD_FAILED, NOT_FOUND }
    private val repository = TransactionRepository.get(application)
    private val mutableState = MutableLiveData(State.IDLE)
    val state: LiveData<State> = mutableState
    private val mutableOriginal = MutableLiveData<SavedTransaction?>()
    val original: LiveData<SavedTransaction?> = mutableOriginal
    var warningMessage: String? = null
        private set
    private var editId: String? = null
    private var pending: SavedTransaction? = null

    fun loadForEdit(id: String) {
        if (editId == id && state.value != State.LOAD_FAILED) return
        editId = id
        mutableState.value = State.CHECKING
        repository.read(id) { result ->
            if (result.isFailure) mutableState.value = State.LOAD_FAILED
            else if (result.getOrNull() == null) mutableState.value = State.NOT_FOUND
            else {
                mutableOriginal.value = result.getOrThrow()
                mutableState.value = State.IDLE
            }
        }
    }

    fun reconcileRestoredDraft(id: String) {
        if (state.value != State.IDLE || editId != null) return
        mutableState.value = State.CHECKING
        repository.containsDraft(id) { result ->
            mutableState.value = result.fold(
                onSuccess = { if (it) State.SAVED else State.IDLE }, onFailure = { State.FAILED })
        }
    }

    fun save(transaction: SavedTransaction) {
        if (state.value !in listOf(State.IDLE, State.FAILED)) return
        if (editId != null && mutableOriginal.value == null) return
        pending = transaction
        if (transaction.type != TransactionType.EXPENSE) {
            persist()
            return
        }
        mutableState.value = State.CHECKING
        repository.readAll { result ->
            if (result.isFailure) {
                mutableState.value = State.FAILED
                return@readAll
            }
            val rows = result.getOrThrow().filter { it.id != transaction.id }
            val caps = FinanceRepository.getSpendingCaps(getApplication())
            val check = FinanceRepository.checkCaps(transaction.amount, rows, caps, transaction.occurredAt)
            if (!check.isAllowed) {
                warningMessage = check.warningMessage
                mutableState.value = State.CONFIRMING
            } else persist()
        }
    }

    fun confirmSave() {
        if (state.value == State.CONFIRMING) persist()
    }

    fun cancelConfirmation() {
        if (state.value != State.CONFIRMING) return
        pending = null
        warningMessage = null
        mutableState.value = State.IDLE
    }

    private fun persist() {
        val transaction = pending ?: return
        mutableState.value = State.SAVING
        val complete: (Result<Unit>) -> Unit = {
            mutableState.value = if (it.isSuccess) State.SAVED else State.FAILED
        }
        if (editId != null) repository.update(transaction, mutableOriginal.value, complete)
        else repository.create(transaction, complete)
    }
}
