package com.nhom9.quanlychitieu.ui.components

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.nhom9.quanlychitieu.R

object TransactionSyncBanner {
    fun bind(root: View, pendingCount: Int, syncFailed: Boolean, loadFailed: Boolean, retry: () -> Unit) {
        root.isVisible = pendingCount > 0 || syncFailed || loadFailed
        root.findViewById<TextView>(R.id.txtTransactionSyncStatus).text = when {
            loadFailed -> root.context.getString(R.string.ui_transactions_load_error)
            pendingCount > 0 -> root.context.resources.getQuantityString(
                if (syncFailed) R.plurals.transaction_sync_failed_count else R.plurals.transaction_sync_pending_count,
                pendingCount, pendingCount)
            else -> root.context.getString(R.string.transaction_detail_sync_failed)
        }
        root.findViewById<View>(R.id.btnRetryTransactionSync).apply {
            isVisible = syncFailed || loadFailed || pendingCount > 0
            setOnClickListener { retry() }
        }
    }
}
