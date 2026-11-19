package com.nhom9.quanlychitieu.ui.transaction

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.method.TextKeyListener
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.Category
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.ui.components.UiChrome
import com.nhom9.quanlychitieu.ui.main.MainActivity
import com.nhom9.quanlychitieu.ui.wallet.WalletActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {
    private var currentType = 0
    private var selectedCategoryIndex = 0
    private var sourceWalletId: String? = null
    private var destinationWalletId: String? = null
    private var wallets: List<Wallet> = emptyList()
    private var walletsLoaded = false
    private var defaultWalletsSelected = false
    private var walletObservation: AutoCloseable? = null
    private var walletDialog: AlertDialog? = null
    private var customCategory: String? = null
    private var editId: String? = null
    private var formFilled = false
    private var capDialog: AlertDialog? = null
    private var occurredAt = System.currentTimeMillis()
    private var draftId = UUID.randomUUID().toString()
    private var detailsChanged = false
    private var saving = false
    private lateinit var amount: EditText
    private lateinit var note: EditText
    private lateinit var errorText: TextView
    private lateinit var saveViewModel: TransactionSaveViewModel
    private val categoryIds = intArrayOf(R.id.catFood, R.id.catShopping, R.id.catTransport,
        R.id.catBills, R.id.catEntertainment, R.id.catHealth, R.id.catEducation, R.id.catOther)
    private val expenseCategories = arrayOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn",
        "Giải trí", "Sức khỏe", "Giáo dục", "Khác")
    private val incomeCategories = arrayOf("Lương", "Thưởng", "Kinh doanh", "Đầu tư",
        "Quà tặng", "Hoàn tiền", "Trợ cấp", "Khác")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)
        UiChrome.setup(this, R.id.main_add_transaction, includeIme = true)
        amount = findViewById(R.id.edtAmount)
        note = findViewById(R.id.edtNote)
        errorText = findViewById(R.id.txtAmountError)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.txtTransactionTitle), true)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.txtCategoryHeading), true)
        // Keep pasted punctuation/signs intact so validation can reject malformed money.
        // A numeric IME alone must not silently turn "-100" into "100" or "1.5" into "15".
        amount.keyListener = TextKeyListener.getInstance()
        amount.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        // Reject oversized values in validation, rather than truncating a paste to a different amount.
        amount.filters = emptyArray()
        note.filters = arrayOf(InputFilter.LengthFilter(maxOf(500, savedInstanceState?.getString("note")?.length ?: 0)))
        editId = intent.getStringExtra("transaction_id")
        editId?.let { draftId = it; findViewById<TextView>(R.id.txtTransactionTitle).setText(R.string.transaction_edit_title) }
        currentType = intent.getIntExtra("type", 0).coerceIn(0, 2)
        savedInstanceState?.let {
            currentType = it.getInt("type", currentType)
            selectedCategoryIndex = it.getInt("category", selectedCategoryIndex)
            sourceWalletId = it.getString("sourceName")
            destinationWalletId = it.getString("destinationName")
            defaultWalletsSelected = it.getBoolean("defaultWalletsSelected", sourceWalletId != null)
            customCategory = it.getString("customCategory")
            formFilled = it.getBoolean("formFilled")
            occurredAt = it.getLong("time", occurredAt)
            draftId = it.getString("draftId") ?: draftId
            detailsChanged = it.getBoolean("detailsChanged")
            amount.setText(it.getString("amount", ""))
            note.setText(it.getString("note", ""))
            findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked = it.getBoolean("exclude")
        }
        listOf(R.id.btnTypeExpense, R.id.btnTypeIncome, R.id.btnTypeTransfer)
            .forEachIndexed { index, id ->
                findViewById<View>(id).setOnClickListener {
                    if (!saving && currentType != index) { currentType = index; customCategory = null; selectedCategoryIndex = 0; renderSelection() }
                }
            }
        categoryIds.forEachIndexed { index, id ->
            findViewById<View>(id).setOnClickListener {
                if (!saving) { selectedCategoryIndex = index; customCategory = null; renderSelection() }
            }
        }
        val grid = findViewById<GridLayout>(R.id.categoryGrid)
        val columns = if (resources.configuration.fontScale >= 1.3f ||
            resources.configuration.screenWidthDp < 360) 2 else 4
        grid.columnCount = columns
        for (index in 0 until grid.childCount) {
            val child = grid.getChildAt(index)
            child.layoutParams = (child.layoutParams as GridLayout.LayoutParams).apply {
                width = 0
                rowSpec = GridLayout.spec(index / columns)
                columnSpec = GridLayout.spec(index % columns, 1f)
            }
        }
        listOf(R.id.btnQuick20k to 20_000L, R.id.btnQuick50k to 50_000L,
            R.id.btnQuick100k to 100_000L, R.id.btnQuick200k to 200_000L,
            R.id.btnQuick500k to 500_000L).forEach { (id, increment) ->
            findViewById<View>(id).setOnClickListener {
                if (!saving) {
                    val result = VndAmount.add(amount.text.toString(), increment)
                    if (result == null) showError(getString(R.string.transaction_amount_invalid_or_large))
                    else { amount.setText(VndAmount.format(result)); amount.setSelection(amount.length()) }
                }
            }
        }
        amount.addTextChangedListener { errorText.visibility = View.GONE }
        findViewById<View>(R.id.btnSelectWallet).setOnClickListener { selectWallet(false) }
        findViewById<View>(R.id.btnDestinationWallet).setOnClickListener { selectWallet(true) }
        findViewById<View>(R.id.btnSelectTime).setOnClickListener { selectTime() }
        findViewById<View>(R.id.btnSaveTransaction).setOnClickListener { save() }
        findViewById<View>(R.id.btnBack).setOnClickListener { leaveForm() }
        onBackPressedDispatcher.addCallback(this) { leaveForm() }
        renderSelection()
        saveViewModel = ViewModelProvider(this)[TransactionSaveViewModel::class.java]
        saveViewModel.original.observe(this) { transaction ->
            if (transaction != null) {
                note.filters = arrayOf(InputFilter.LengthFilter(maxOf(500, transaction.note.length, note.length())))
            }
            if (transaction != null && !formFilled) {
                currentType = transaction.type.ordinal
                sourceWalletId = transaction.sourceWallet
                destinationWalletId = transaction.destinationWallet
                occurredAt = transaction.occurredAt
                draftId = transaction.id
                amount.setText(VndAmount.format(transaction.amount))
                note.setText(transaction.note)
                findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked = transaction.excludeFromBudget
                val categories = if (currentType == 1) incomeCategories else expenseCategories
                selectedCategoryIndex = categories.indexOf(transaction.category)
                customCategory = transaction.category.takeIf { selectedCategoryIndex < 0 && currentType != 2 }
                formFilled = true
                renderSelection()
            }
        }
        saveViewModel.state.observe(this) { state ->
            setSaving(state in listOf(TransactionSaveViewModel.State.SAVING,
                TransactionSaveViewModel.State.CHECKING, TransactionSaveViewModel.State.CONFIRMING))
            if (state == TransactionSaveViewModel.State.CHECKING)
                findViewById<TextView>(R.id.txtSaveLabel).setText(R.string.transaction_checking)
            when (state) {
                TransactionSaveViewModel.State.SAVED -> {
                    Toast.makeText(this, if (editId == null) R.string.transaction_created_local else R.string.transaction_updated_local, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    safeFinish()
                }
                TransactionSaveViewModel.State.CONFIRMING -> showCapConfirmation()
                TransactionSaveViewModel.State.LOAD_FAILED -> {
                    showError(getString(R.string.transaction_edit_load_failed))
                    findViewById<TextView>(R.id.txtSaveLabel).setText(R.string.transaction_edit_retry)
                }
                TransactionSaveViewModel.State.NOT_FOUND -> {
                    showError(getString(R.string.transaction_edit_missing))
                    findViewById<View>(R.id.btnSaveTransaction).isEnabled = false
                }
                TransactionSaveViewModel.State.FAILED ->
                    showError(getString(if (editId == null) R.string.transaction_save_failed else R.string.transaction_edit_failed))
                else -> Unit
            }
        }
        if (editId != null) saveViewModel.loadForEdit(editId!!)
        else if (savedInstanceState != null) saveViewModel.reconcileRestoredDraft(draftId)

        walletObservation = TransactionRepository.get(this).observe { snapshot ->
            walletsLoaded = !snapshot.loadFailed
            if (walletsLoaded) {
                wallets = snapshot.wallets
                if (editId == null && !defaultWalletsSelected) {
                    sourceWalletId = wallets.firstOrNull()?.id
                    destinationWalletId = wallets.firstOrNull { it.id != sourceWalletId }?.id
                    defaultWalletsSelected = true
                }
            }
            renderWalletChoices()
        }
    }

    private fun showCapConfirmation() {
        if (capDialog?.isShowing == true || isFinishing || isDestroyed) return
        capDialog = AlertDialog.Builder(this)
            .setTitle(R.string.transaction_cap_title)
            .setMessage(getString(R.string.transaction_cap_confirm, saveViewModel.warningMessage.orEmpty()))
            .setPositiveButton(R.string.transaction_cap_save) { _, _ -> saveViewModel.confirmSave() }
            .setNegativeButton(R.string.transaction_cap_cancel) { _, _ -> saveViewModel.cancelConfirmation() }
            .setOnCancelListener { saveViewModel.cancelConfirmation() }
            .show()
    }

    override fun onDestroy() {
        walletObservation?.close()
        walletDialog?.dismiss()
        capDialog?.dismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("type", currentType)
        outState.putInt("category", selectedCategoryIndex)
        outState.putString("sourceName", sourceWalletId)
        outState.putString("destinationName", destinationWalletId)
        outState.putBoolean("defaultWalletsSelected", defaultWalletsSelected)
        outState.putString("customCategory", customCategory)
        outState.putBoolean("formFilled", formFilled)
        outState.putLong("time", occurredAt)
        outState.putString("draftId", draftId)
        outState.putBoolean("detailsChanged", detailsChanged)
        outState.putString("amount", amount.text.toString())
        outState.putString("note", note.text.toString())
        outState.putBoolean("exclude", findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked)
        super.onSaveInstanceState(outState)
    }

    private fun renderSelection() {
        listOf(R.id.btnTypeExpense, R.id.btnTypeIncome, R.id.btnTypeTransfer)
            .forEachIndexed { index, id ->
                findViewById<TextView>(id).apply {
                    isSelected = index == currentType
                    background = if (isSelected) ContextCompat.getDrawable(context, R.drawable.bg_btn_pill) else null
                    setTextColor(ContextCompat.getColor(context,
                        if (isSelected) R.color.text_primary else R.color.text_secondary))
                }
            }
        findViewById<TextView>(R.id.txtCategoryHeading).text = customCategory?.let {
            getString(R.string.transaction_legacy_category, it)
        } ?: getString(R.string.activity_add_transaction_22_text)
        val labels = resources.getStringArray(R.array.transaction_amount_labels)
        val colors = intArrayOf(R.color.red_expense, R.color.green_accent, R.color.cyan_accent)
        findViewById<TextView>(R.id.txtAmountLabel).apply {
            text = labels[currentType]
            setTextColor(ContextCompat.getColor(context, colors[currentType]))
        }
        val transfer = currentType == 2
        findViewById<View>(R.id.categorySection).visibility = if (transfer) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnDestinationWallet).visibility = if (transfer) View.VISIBLE else View.GONE
        findViewById<View>(R.id.switchExcludeBudget).visibility = if (currentType == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.txtExcludeBudgetHint).visibility = if (currentType == 0) View.VISIBLE else View.GONE
        val categories = resources.getStringArray(if (currentType == 1)
            R.array.transaction_income_labels else R.array.transaction_expense_labels)
        categoryIds.forEachIndexed { index, id ->
            findViewById<TextView>(id).apply {
                isSelected = index == selectedCategoryIndex
                setBackgroundResource(if (isSelected) R.drawable.bg_category_active else R.drawable.bg_category_inactive)
                text = categories[index]
                // Income categories must not retain expense-specific pictograms.
                val icon = ContextCompat.getDrawable(context, if (currentType == 1)
                    R.drawable.ic_wallet else expenseIcons[index])?.mutate()
                val iconSize = (24 * resources.displayMetrics.density + 0.5f).toInt()
                icon?.setBounds(0, 0, iconSize, iconSize)
                icon?.setTint(ContextCompat.getColor(context, R.color.text_primary))
                setCompoundDrawablesRelative(null, icon, null, null)
            }
        }
        renderDetails()
    }

    private val expenseIcons = intArrayOf(R.drawable.ic_utensils, R.drawable.ic_shopping_cart,
        R.drawable.ic_car, R.drawable.ic_credit_card, R.drawable.ic_gamepad,
        R.drawable.ic_heart, R.drawable.ic_school, R.drawable.ic_more_horiz)

    private fun renderDetails() {
        renderWalletChoices()
        findViewById<TextView>(R.id.txtTransactionTime).text =
            SimpleDateFormat(getString(R.string.transaction_date_time_pattern),
                Locale.forLanguageTag("vi-VN")).format(occurredAt)
    }

    private fun renderWalletChoices() {
        findViewById<TextView>(R.id.txtWalletLabel).setText(if (currentType == 2)
            R.string.transaction_source_wallet else R.string.transaction_wallet)
        findViewById<TextView>(R.id.txtWalletName).text = selectedWalletName(sourceWalletId)
        findViewById<TextView>(R.id.txtDestinationWalletName).text = selectedWalletName(destinationWalletId)
    }

    private fun selectedWalletName(id: String?): String {
        return wallets.firstOrNull { it.id == id }?.name ?: getString(
            if (id == null || !walletsLoaded) R.string.wallet_integration_select
            else R.string.wallet_integration_unavailable
        )
    }

    private fun selectWallet(destination: Boolean) {
        if (saving || walletDialog != null) return
        val options = wallets
        val selectedId = if (destination) destinationWalletId else sourceWalletId
        val builder = AlertDialog.Builder(this)
            .setTitle(if (destination) R.string.transaction_choose_destination_wallet
                else R.string.transaction_choose_wallet)
            .setNegativeButton(R.string.transaction_cancel, null)
            .setNeutralButton(R.string.wallet_manage_title) { _, _ ->
                startActivity(Intent(this, WalletActivity::class.java))
            }
            .setOnDismissListener { walletDialog = null }
        if (!walletsLoaded) {
            builder.setMessage(R.string.wallet_integration_unloaded)
                .setPositiveButton(R.string.transaction_edit_retry) { _, _ ->
                    TransactionRepository.get(this).retrySync()
                }
        } else if (options.isEmpty()) {
            builder.setMessage(R.string.wallet_integration_empty)
        } else {
            builder.setSingleChoiceItems(options.map { it.name }.toTypedArray(),
                options.indexOfFirst { it.id == selectedId }) { dialog, which ->
                val id = options[which].id
                if (walletsLoaded && wallets.any { it.id == id }) {
                    if (destination) destinationWalletId = id else sourceWalletId = id
                    detailsChanged = true
                    errorText.visibility = View.GONE
                    renderWalletChoices()
                } else {
                    showError(getString(R.string.wallet_integration_unavailable))
                }
                dialog.dismiss()
            }
        }
        walletDialog = builder.show()
    }

    private fun selectTime() {
        if (saving) return
        val calendar = Calendar.getInstance().apply { timeInMillis = occurredAt }
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            TimePickerDialog(this, R.style.TransactionTimePickerDialog, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                occurredAt = calendar.timeInMillis
                detailsChanged = true
                renderDetails()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).apply {
                setButton(TimePickerDialog.BUTTON_POSITIVE, getString(R.string.transaction_confirm), this)
                setButton(TimePickerDialog.BUTTON_NEGATIVE, getString(R.string.transaction_cancel), this)
                show()
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            setButton(DatePickerDialog.BUTTON_POSITIVE, getString(R.string.transaction_next), this)
            setButton(DatePickerDialog.BUTTON_NEGATIVE, getString(R.string.transaction_cancel), this)
            show()
        }
    }

    private fun save() {
        if (saving) return
        if (saveViewModel.state.value == TransactionSaveViewModel.State.LOAD_FAILED) {
            editId?.let { saveViewModel.loadForEdit(it) }
            return
        }
        val value = VndAmount.parse(amount.text.toString())
        if (value == null) {
            showError(getString(R.string.transaction_amount_required))
            amount.requestFocus()
            return
        }
        val source = if (walletsLoaded) wallets.find { it.id == sourceWalletId }?.id else null
        if (source == null) {
            val error = when {
                !walletsLoaded -> R.string.wallet_integration_unloaded
                wallets.isEmpty() -> R.string.wallet_integration_empty
                else -> R.string.wallet_integration_select_source
            }
            showError(getString(error))
            return
        }
        val destination = destinationWalletId.takeIf { currentType == 2 }
        val walletError = when {
            currentType != 2 -> null
            wallets.size < 2 -> R.string.wallet_integration_transfer_needs_two
            destination == null || wallets.none { it.id == destination } -> R.string.wallet_integration_select_destination
            source == destination -> R.string.transaction_wallets_must_differ
            else -> null
        }
        if (walletError != null) {
            showError(getString(walletError))
            return
        }
        val category = if (currentType == 2) "Chuyển khoản" else
            customCategory ?: (if (currentType == 1) incomeCategories else expenseCategories)[selectedCategoryIndex]
        val original = saveViewModel.original.value
        val categoryId = original?.takeIf { it.type.ordinal == currentType && it.category == category }?.categoryId
            ?: Category.fromName(category).id
        val transaction = SavedTransaction(
            draftId, value, TransactionType.entries[currentType], category,
            if (editId == null) note.text.toString().trim() else note.text.toString(), source,
            destination, occurredAt,
            currentType == 0 && findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked, categoryId
        )

        saveViewModel.save(transaction)
    }

    private fun setSaving(value: Boolean) {
        saving = value
        amount.isEnabled = !value
        note.isEnabled = !value
        listOf(R.id.btnSaveTransaction, R.id.btnSelectWallet, R.id.btnDestinationWallet,
            R.id.btnSelectTime, R.id.switchExcludeBudget).forEach { findViewById<View>(it).isEnabled = !value }
        findViewById<TextView>(R.id.txtSaveLabel).setText(if (value)
            R.string.transaction_saving else if (editId == null) R.string.transaction_save else R.string.transaction_update_action)
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        findViewById<NestedScrollView>(R.id.transactionScroll).smoothScrollTo(0, 0)
    }

    private fun safeFinish() {
        if (isTaskRoot) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        finish()
    }

    private fun leaveForm() {
        if (saving) return
        val changed = amount.text.isNotBlank() || note.text.isNotBlank() || currentType != 0 ||
            selectedCategoryIndex != 0 || detailsChanged || findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked
        if (!changed) {
            safeFinish()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.transaction_discard_title)
                .setMessage(R.string.transaction_discard_message)
                .setNegativeButton(R.string.transaction_keep_editing, null)
                .setPositiveButton(R.string.transaction_discard) { _, _ -> safeFinish() }
                .show()
        }
    }
}
