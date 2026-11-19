package com.nhom9.quanlychitieu.ui.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance
import com.nhom9.quanlychitieu.ui.components.UiChrome
import com.nhom9.quanlychitieu.ui.main.MainActivity
import com.nhom9.quanlychitieu.ui.transaction.TransactionDetailViewModel.DeleteState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: TransactionDetailViewModel
    private var displayedTransaction: SavedTransaction? = null
    private var deleteDialog: AlertDialog? = null
    private var editInProgress = false

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        editInProgress = false
        viewModel.state.value?.let(::render)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)
        UiChrome.setup(this, R.id.main_transaction_detail)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.txtDetailTitle), true)

        editInProgress = savedInstanceState?.getBoolean(KEY_EDIT_IN_PROGRESS) ?: false
        viewModel = ViewModelProvider(this)[TransactionDetailViewModel::class.java]

        findViewById<View>(R.id.btnBackTransactionDetail).setOnClickListener { closeDetail() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeDetail()
        })
        findViewById<View>(R.id.btnRetryTransactionLoad).setOnClickListener { viewModel.retry() }
        findViewById<View>(R.id.btnRetryTransactionSync).setOnClickListener { viewModel.retry() }
        findViewById<View>(R.id.btnEditTransaction).setOnClickListener {
            val transaction = displayedTransaction ?: return@setOnClickListener
            if (editInProgress || isDeleting()) return@setOnClickListener

            editInProgress = true
            viewModel.state.value?.let(::render)
            editLauncher.launch(
                Intent(this, AddTransactionActivity::class.java)
                    .putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            )
        }
        findViewById<View>(R.id.btnDeleteTransaction).setOnClickListener { confirmDelete() }

        viewModel.state.observe(this, ::render)
        viewModel.attach(intent.getStringExtra(EXTRA_TRANSACTION_ID))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_EDIT_IN_PROGRESS, editInProgress)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    private fun render(state: TransactionDetailViewModel.State) {
        if (isFinishing) return
        if (state.deletion == DeleteState.DELETED) {
            setResult(RESULT_OK)
            Toast.makeText(this, R.string.transaction_detail_deleted, Toast.LENGTH_SHORT).show()
            closeDetail()
            return
        }

        val deleting = state.deletion == DeleteState.DELETING
        val transaction = state.transaction?.takeUnless { state.loading || state.loadFailed }
        if (displayedTransaction != transaction) deleteDialog?.dismiss()
        displayedTransaction = transaction

        val message = when {
            transaction != null -> null
            deleting -> R.string.transaction_detail_deleting
            state.loading -> R.string.ui_transactions_loading
            state.loadFailed -> R.string.ui_transactions_load_error
            else -> R.string.transaction_detail_missing
        }
        findViewById<TextView>(R.id.txtDetailMessage).apply {
            isVisible = message != null
            message?.let { setText(it) }
        }
        findViewById<View>(R.id.btnRetryTransactionLoad).isVisible = state.loadFailed && !deleting
        findViewById<View>(R.id.transactionDetailContent).isVisible = transaction != null

        setActionEnabled(R.id.btnBackTransactionDetail, !deleting)
        if (transaction == null) return

        renderRecord(transaction, state.wallets)
        findViewById<TextView>(R.id.txtDetailSyncStatus).setText(
            when {
                state.pending && state.syncFailed -> R.string.transaction_detail_pending_failed
                state.syncFailed -> R.string.transaction_detail_sync_failed
                state.pending -> R.string.transaction_detail_sync_pending
                else -> R.string.transaction_detail_sync_no_pending
            }
        )
        findViewById<View>(R.id.btnRetryTransactionSync).isVisible = state.syncFailed
        findViewById<TextView>(R.id.txtDetailActionStatus).apply {
            isVisible = deleting || state.deletion == DeleteState.FAILED
            setText(
                if (deleting) R.string.transaction_detail_deleting
                else R.string.transaction_detail_delete_failed
            )
        }

        val actionsEnabled = !deleting && !editInProgress
        setActionEnabled(R.id.btnRetryTransactionSync, actionsEnabled)
        setActionEnabled(R.id.btnEditTransaction, actionsEnabled)
        setActionEnabled(R.id.btnDeleteTransaction, actionsEnabled)
    }

    private fun renderRecord(transaction: SavedTransaction, wallets: List<Wallet>) {
        val transfer = transaction.type == TransactionType.TRANSFER
        findViewById<TextView>(R.id.txtDetailType).setText(typeLabel(transaction.type))
        findViewById<TextView>(R.id.txtDetailAmount).apply {
            text = formatAmount(transaction)
            val color = when (transaction.type) {
                TransactionType.EXPENSE -> R.color.red_expense
                TransactionType.INCOME -> R.color.green_accent
                TransactionType.TRANSFER -> R.color.cyan_accent
            }
            setTextColor(ContextCompat.getColor(context, color))
        }
        findViewById<View>(R.id.transactionDetailCategorySection).isVisible = !transfer
        findViewById<TextView>(R.id.txtDetailCategory).text = transaction.category.ifBlank {
            getString(R.string.transaction_detail_missing_value)
        }
        findViewById<TextView>(R.id.txtDetailSourceWalletLabel).setText(
            if (transfer) R.string.transaction_source_wallet else R.string.transaction_wallet
        )
        findViewById<TextView>(R.id.txtDetailSourceWallet).text =
            walletName(transaction.sourceWallet, wallets)
        findViewById<View>(R.id.transactionDetailDestinationSection).isVisible = transfer
        findViewById<TextView>(R.id.txtDetailDestinationWallet).text =
            walletName(transaction.destinationWallet, wallets)
        findViewById<TextView>(R.id.txtDetailOccurredAt).text = formatDate(transaction.occurredAt)
        findViewById<TextView>(R.id.txtDetailNote).text = transaction.note.ifBlank {
            getString(R.string.transaction_detail_empty_note)
        }
        findViewById<View>(R.id.transactionDetailBudgetSection).isVisible =
            transaction.type == TransactionType.EXPENSE
        findViewById<TextView>(R.id.txtDetailBudget).setText(
            if (transaction.excludeFromBudget) R.string.transaction_detail_budget_excluded
            else R.string.transaction_detail_budget_included
        )
    }

    private fun setActionEnabled(@IdRes id: Int, enabled: Boolean) {
        findViewById<View>(id).apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.54f
        }
    }

    private fun confirmDelete() {
        val transaction = displayedTransaction ?: return
        if (deleteDialog != null || editInProgress || isDeleting()) return
        val type = getString(typeLabel(transaction.type))
        val identification = if (transaction.type == TransactionType.TRANSFER || transaction.category.isBlank()) {
            type
        } else {
            getString(R.string.ui_transaction_title_category, type, transaction.category)
        }
        val wallets = viewModel.state.value?.wallets.orEmpty()
        val source = walletName(transaction.sourceWallet, wallets)
        val wallet = if (transaction.type == TransactionType.TRANSFER) {
            val destination = walletName(transaction.destinationWallet, wallets)
            getString(R.string.ui_transaction_wallet_transfer, source, destination)
        } else source
        deleteDialog = AlertDialog.Builder(this)
            .setTitle(R.string.transaction_detail_delete_title)
            .setMessage(
                getString(
                    R.string.wallet_integration_delete_confirm,
                    identification,
                    formatAmount(transaction),
                    wallet,
                    formatDate(transaction.occurredAt)
                )
            )
            .setNegativeButton(R.string.transaction_cancel, null)
            .setPositiveButton(R.string.transaction_detail_delete) { _, _ ->
                viewModel.delete(transaction)
            }
            .setOnDismissListener { deleteDialog = null }
            .show()
    }

    private fun isDeleting() = viewModel.state.value?.deletion == DeleteState.DELETING

    private fun walletName(id: String?, wallets: List<Wallet>): String =
        WalletBalance.name(id, wallets).ifBlank { getString(R.string.transaction_detail_missing_value) }

    private fun closeDetail() {
        if (isDeleting()) return
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        finish()
    }

    private fun formatDate(occurredAt: Long): String = SimpleDateFormat(
        getString(R.string.ui_transaction_date_time_pattern),
        Locale.forLanguageTag("vi-VN")
    ).format(Date(occurredAt))

    private fun formatAmount(transaction: SavedTransaction): String {
        val template = when (transaction.type) {
            TransactionType.EXPENSE -> R.string.ui_transaction_amount_expense
            TransactionType.INCOME -> R.string.ui_transaction_amount_income
            TransactionType.TRANSFER -> R.string.ui_transaction_amount_transfer
        }
        return getString(template, VndAmount.format(transaction.amount))
    }

    @StringRes
    private fun typeLabel(type: TransactionType): Int = when (type) {
        TransactionType.EXPENSE -> R.string.ui_transaction_type_expense
        TransactionType.INCOME -> R.string.ui_transaction_type_income
        TransactionType.TRANSFER -> R.string.ui_transaction_type_transfer
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        private const val KEY_EDIT_IN_PROGRESS = "edit_in_progress"

        fun intent(context: Context, id: String): Intent =
            Intent(context, TransactionDetailActivity::class.java).putExtra(EXTRA_TRANSACTION_ID, id)
    }
}
