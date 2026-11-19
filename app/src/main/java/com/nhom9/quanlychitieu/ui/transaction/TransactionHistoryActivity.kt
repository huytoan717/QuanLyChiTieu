package com.nhom9.quanlychitieu.ui.transaction

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.ui.components.TransactionSyncBanner
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance
import com.nhom9.quanlychitieu.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionHistoryActivity : AppCompatActivity() {

    private var subscription: AutoCloseable? = null
    private var currentFilterType: TransactionType? = null // null: Tất cả
    private var allTransactions: List<SavedTransaction> = emptyList()
    private var wallets: List<Wallet> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transaction_history)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_history)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentFilterType = savedInstanceState?.getString("filter")?.let { name ->
            TransactionType.entries.firstOrNull { it.name == name }
        }
        setupTypeFilters()
        setupClickListeners()
        loadHistoryTransactions()
    }

    private fun setupTypeFilters() {
        val btnAll = findViewById<TextView>(R.id.btnFilterAll)
        val btnExpense = findViewById<TextView>(R.id.btnFilterExpense)
        val btnIncome = findViewById<TextView>(R.id.btnFilterIncome)
        val btnTransfer = findViewById<TextView>(R.id.btnFilterTransfer)

        fun updateFilterState(index: Int) {
            currentFilterType = when (index) {
                1 -> TransactionType.EXPENSE
                2 -> TransactionType.INCOME
                3 -> TransactionType.TRANSFER
                else -> null
            }

            btnAll?.background = if (index == 0) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnAll?.setTextColor(ContextCompat.getColor(this, if (index == 0) R.color.text_primary else R.color.text_secondary))

            btnExpense?.background = if (index == 1) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnExpense?.setTextColor(ContextCompat.getColor(this, if (index == 1) R.color.text_primary else R.color.text_secondary))

            btnIncome?.background = if (index == 2) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnIncome?.setTextColor(ContextCompat.getColor(this, if (index == 2) R.color.text_primary else R.color.text_secondary))

            btnTransfer?.background = if (index == 3) ContextCompat.getDrawable(this, R.drawable.bg_btn_pill) else null
            btnTransfer?.setTextColor(ContextCompat.getColor(this, if (index == 3) R.color.text_primary else R.color.text_secondary))

            renderFilteredTransactions()
        }

        btnAll?.setOnClickListener { updateFilterState(0) }
        btnExpense?.setOnClickListener { updateFilterState(1) }
        btnIncome?.setOnClickListener { updateFilterState(2) }
        btnTransfer?.setOnClickListener { updateFilterState(3) }
        updateFilterState(when (currentFilterType) {
            TransactionType.EXPENSE -> 1
            TransactionType.INCOME -> 2
            TransactionType.TRANSFER -> 3
            else -> 0
        })
    }

    private fun handleBackNavigation() {
        if (isTaskRoot) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        } else {
            finish()
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnBackHistory)?.setOnClickListener {
            handleBackNavigation()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun loadHistoryTransactions() {
        subscription = TransactionRepository.get(this).observe { snapshot ->
            TransactionSyncBanner.bind(findViewById(R.id.transactionSyncBanner), snapshot.pendingIds.size,
                snapshot.syncFailed, snapshot.loadFailed) { TransactionRepository.get(this).retrySync() }
            if (snapshot.loadFailed) {
                findViewById<TextView>(R.id.txtEmptyHistory)?.apply {
                    setText(R.string.ui_transactions_load_error)
                    visibility = View.VISIBLE
                }
            } else {
                allTransactions = snapshot.transactions
                wallets = snapshot.wallets
                renderFilteredTransactions()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("filter", currentFilterType?.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        subscription?.close()
        super.onDestroy()
    }

    private fun renderFilteredTransactions() {
        val filteredList = if (currentFilterType == null) {
            allTransactions
        } else {
            allTransactions.filter { it.type == currentFilterType }
        }

        val container = findViewById<LinearLayout>(R.id.historyListContainer) ?: return
        val txtEmpty = findViewById<TextView>(R.id.txtEmptyHistory)
        val txtCount = findViewById<TextView>(R.id.txtTransactionCount)

        container.removeAllViews()

        txtCount?.text = "${filteredList.size} giao dịch trong lịch sử"

        if (filteredList.isEmpty()) {
            txtEmpty?.setText(R.string.ui_transactions_empty)
            txtEmpty?.visibility = View.VISIBLE
            return
        } else {
            txtEmpty?.visibility = View.GONE
        }

        val dateFormat = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

        filteredList.forEach { transaction ->
            val row = layoutInflater.inflate(R.layout.item_saved_transaction, container, false)
            val (typeLabel, amountFormat, color) = when (transaction.type) {
                TransactionType.EXPENSE -> Triple(
                    "Chi tiêu",
                    "−%s VNĐ",
                    R.color.red_expense
                )
                TransactionType.INCOME -> Triple(
                    "Thu nhập",
                    "+%s VNĐ",
                    R.color.green_accent
                )
                TransactionType.TRANSFER -> Triple(
                    "Chuyển khoản",
                    "%s VNĐ",
                    R.color.cyan_accent
                )
            }

            row.findViewById<TextView>(R.id.savedTransactionTitle).text =
                if (transaction.type == TransactionType.TRANSFER) typeLabel
                else "$typeLabel · ${transaction.category}"

            row.findViewById<TextView>(R.id.savedTransactionAmount).apply {
                text = String.format(amountFormat, VndAmount.format(transaction.amount))
                setTextColor(ContextCompat.getColor(context, color))
            }

            val source = WalletBalance.name(transaction.sourceWallet, wallets)
            val walletInfo = if (transaction.type == TransactionType.TRANSFER) {
                val destination = WalletBalance.name(transaction.destinationWallet, wallets).ifBlank {
                    getString(R.string.transaction_detail_missing_value)
                }
                getString(R.string.wallet_integration_transfer_wallets, source, destination)
            } else source

            row.findViewById<TextView>(R.id.savedTransactionDetails).text =
                "$walletInfo\n${dateFormat.format(transaction.occurredAt)}"

            val noteView = row.findViewById<TextView>(R.id.savedTransactionNote)
            if (transaction.note.isNotBlank()) {
                noteView.text = transaction.note
                noteView.visibility = View.VISIBLE
            } else {
                noteView.visibility = View.GONE
            }

            row.setOnClickListener {
                startActivity(TransactionDetailActivity.intent(this, transaction.id))
            }

            container.addView(row)
        }
    }
}
