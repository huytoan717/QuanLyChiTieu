package com.nhom9.quanlychitieu.model

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

data class SavedTransaction(
    val id: String,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val note: String,
    val sourceWallet: String,
    val destinationWallet: String?,
    val occurredAt: Long,
    val excludeFromBudget: Boolean,
    val categoryId: String = Category.fromName(category).id
)