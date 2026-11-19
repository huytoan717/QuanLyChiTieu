package com.nhom9.quanlychitieu.ui.wallet

import android.os.Bundle
import android.text.InputType
import android.text.method.TextKeyListener
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.ui.components.UiChrome

class EditWalletActivity : AppCompatActivity() {
    private lateinit var viewModel: WalletEditViewModel
    private lateinit var name: EditText
    private lateinit var openingBalance: EditText
    private var walletId: String? = null
    private var formInitialized = false
    private var deleteDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_wallet)
        UiChrome.setup(this, R.id.main_edit_wallet, includeIme = true)

        walletId = intent.getStringExtra(EXTRA_WALLET_ID)
        val title = findViewById<TextView>(R.id.txtWalletEditorTitle)
        if (walletId != null) title.setText(R.string.wallet_edit_title)
        ViewCompat.setAccessibilityHeading(title, true)

        name = findViewById(R.id.edtWalletName)
        openingBalance = findViewById(R.id.edtWalletOpeningBalance)
        // Keep pasted signs and separators available for validation.
        openingBalance.keyListener = TextKeyListener.getInstance()
        openingBalance.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        formInitialized = savedInstanceState?.getBoolean(KEY_FORM_INITIALIZED) == true

        viewModel = ViewModelProvider(this)[WalletEditViewModel::class.java]
        findViewById<View>(R.id.btnBackEditWallet).setOnClickListener { closeEditor() }
        onBackPressedDispatcher.addCallback(this) { closeEditor() }
        findViewById<View>(R.id.btnSaveWallet).setOnClickListener { save() }
        findViewById<View>(R.id.btnDeleteWallet).setOnClickListener { confirmDelete() }
        findViewById<View>(R.id.btnRetryWalletEditLoad).setOnClickListener { viewModel.retry() }

        viewModel.state.observe(this, ::render)
        viewModel.attach(walletId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FORM_INITIALIZED, formInitialized)
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    private fun render(state: WalletEditViewModel.State) {
        if (isFinishing) return
        state.completed?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val missing = walletId != null && state.wallet == null
        val formAvailable = !state.loading && !state.loadFailed && !missing
        if (formAvailable && !formInitialized) {
            name.setText(state.wallet?.name.orEmpty())
            openingBalance.setText(VndAmount.format(state.wallet?.openingBalance ?: 0L))
            formInitialized = true
        }

        val busy = state.pending != null
        val message = when {
            state.pending != null -> getString(state.pending)
            state.loading -> getString(R.string.wallet_loading)
            state.loadFailed -> getString(R.string.wallet_load_failed)
            missing -> getString(R.string.wallet_missing)
            else -> state.error
        }
        findViewById<TextView>(R.id.txtWalletEditStatus).apply {
            isVisible = message != null
            text = message
        }
        findViewById<View>(R.id.walletEditForm).isVisible = formAvailable
        findViewById<View>(R.id.btnRetryWalletEditLoad).isVisible = state.loadFailed && !busy
        findViewById<View>(R.id.btnBackEditWallet).isEnabled = !busy
        findViewById<View>(R.id.btnSaveWallet).apply {
            isEnabled = !busy
            alpha = if (isEnabled) 1f else 0.54f
        }
        findViewById<View>(R.id.btnDeleteWallet).apply {
            isVisible = walletId != null
            isEnabled = !busy
            alpha = if (isEnabled) 1f else 0.54f
        }
        name.isEnabled = !busy
        openingBalance.isEnabled = !busy
        if (!formAvailable) deleteDialog?.dismiss()
    }

    private fun save() {
        val walletName = name.text.toString().trim()
        if (walletName.isBlank()) {
            name.error = getString(R.string.wallet_name_required)
            name.requestFocus()
            return
        }
        val amount = VndAmount.parseNonNegative(openingBalance.text.toString())
        if (amount == null || amount > MAX_OPENING_BALANCE) {
            openingBalance.error = getString(R.string.wallet_opening_balance_invalid)
            openingBalance.requestFocus()
            return
        }
        viewModel.save(walletId, walletName, amount)
    }

    private fun confirmDelete() {
        val wallet = viewModel.state.value?.wallet ?: return
        if (deleteDialog != null) return
        deleteDialog = AlertDialog.Builder(this)
            .setTitle(R.string.wallet_delete)
            .setMessage(getString(R.string.wallet_delete_confirm, wallet.name))
            .setNegativeButton(R.string.transaction_cancel, null)
            .setPositiveButton(R.string.wallet_delete) { _, _ -> viewModel.delete(wallet.id) }
            .setOnDismissListener { deleteDialog = null }
            .show()
    }

    private fun closeEditor() {
        if (viewModel.state.value?.pending == null) finish()
    }

    companion object {
        const val EXTRA_WALLET_ID = "wallet_id"
        private const val KEY_FORM_INITIALIZED = "wallet_form_initialized"
        private const val MAX_OPENING_BALANCE = 1_000_000_000_000L
    }
}
