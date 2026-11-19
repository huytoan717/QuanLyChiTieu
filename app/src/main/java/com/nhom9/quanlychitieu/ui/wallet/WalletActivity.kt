package com.nhom9.quanlychitieu.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.data.TransactionSnapshot
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.ui.components.UiChrome

class WalletActivity : AppCompatActivity() {
    private val repository by lazy { TransactionRepository.get(this) }
    private var observation: AutoCloseable? = null
    private var openingEditor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        UiChrome.setup(this, R.id.main_wallet)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.txtWalletTitle), true)

        findViewById<View>(R.id.btnBackWallet).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCreateWallet).setOnClickListener { openEditor(null) }
        findViewById<View>(R.id.btnRetryWalletLoad).setOnClickListener {
            it.isVisible = false
            findViewById<TextView>(R.id.txtWalletStatus).setText(R.string.wallet_loading)
            repository.retrySync()
        }
    }

    override fun onStart() {
        super.onStart()
        observation = repository.observe(::render)
    }

    override fun onResume() {
        super.onResume()
        openingEditor = false
    }

    override fun onStop() {
        observation?.close()
        observation = null
        super.onStop()
    }

    private fun render(snapshot: TransactionSnapshot) {
        val wallets = if (snapshot.loadFailed) emptyList() else snapshot.wallets
        val status = when {
            snapshot.loadFailed -> R.string.wallet_load_failed
            wallets.isEmpty() -> R.string.wallet_empty
            else -> null
        }
        findViewById<TextView>(R.id.txtWalletStatus).apply {
            isVisible = status != null
            status?.let { setText(it) }
        }
        findViewById<View>(R.id.btnRetryWalletLoad).isVisible = snapshot.loadFailed
        findViewById<View>(R.id.btnCreateWallet).apply {
            isEnabled = !snapshot.loadFailed
            alpha = if (isEnabled) 1f else 0.54f
        }
        showWalletRows(findViewById(R.id.walletList), wallets, ::openEditor)
    }

    private fun openEditor(id: String?) {
        if (openingEditor) return
        openingEditor = true
        startActivity(Intent(this, EditWalletActivity::class.java).apply {
            if (id != null) putExtra(EditWalletActivity.EXTRA_WALLET_ID, id)
        })
    }
}

internal fun showWalletRows(
    container: LinearLayout,
    wallets: List<Wallet>,
    onEdit: ((String) -> Unit)? = null
) {
    container.removeAllViews()
    val context = container.context
    val inflater = LayoutInflater.from(context)
    for (wallet in wallets) {
        val row = inflater.inflate(R.layout.item_wallet, container, false)
        row.findViewById<TextView>(R.id.txtWalletName).text = wallet.name
        row.findViewById<TextView>(R.id.txtWalletBalance).text =
            context.getString(R.string.ui_transaction_amount_transfer, VndAmount.format(wallet.balance))
        if (onEdit != null) {
            row.findViewById<View>(R.id.btnEditWallet).apply {
                isVisible = true
                contentDescription = context.getString(R.string.wallet_edit_description, wallet.name)
                setOnClickListener { onEdit(wallet.id) }
            }
        }
        container.addView(row)
    }
}
