package com.nhom9.quanlychitieu.model

data class Budget(
    val id: String = "",
    val categoryId: String = "cat_food",
    val categoryName: String = "",
    val limitAmount: Long = 0L,
    val spentAmount: Long = 0L,
    val period: String = "MONTHLY",
    val statusTag: String = "An toàn"
) {
    val progressPercentage: Int
        get() = if (limitAmount > 0) ((spentAmount.toDouble() / limitAmount) * 100).toInt().coerceIn(0, 100) else 0
}

data class SavingsGoal(
    val id: String = "",
    val title: String = "",
    val targetAmount: Long = 0L,
    val currentAmount: Long = 0L,
    val targetDate: String = "",
    val iconName: String = "ic_plane"
) {
    val progressPercentage: Int
        get() = if (targetAmount > 0) ((currentAmount.toDouble() / targetAmount) * 100).toInt().coerceIn(0, 100) else 0
}

data class Wallet(
    val id: String = "",
    val name: String = "",
    val subtitle: String = "",
    val balance: Long = 0L,
    val isPrimary: Boolean = false,
    val type: String = "BANK",
    val openingBalance: Long = 0L
)
