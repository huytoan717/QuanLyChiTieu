package com.nhom9.quanlychitieu.model

data class FirebaseBudget(
    val id: String = "",
    val categoryName: String = "",
    val limitAmount: Long = 0L,
    val spentAmount: Long = 0L,
    val period: String = "MONTHLY",
    val statusTag: String = "An toàn"
)

data class FirebaseSavingsGoal(
    val id: String = "",
    val title: String = "",
    val targetAmount: Long = 0L,
    val currentAmount: Long = 0L,
    val targetDate: String = "",
    val iconName: String = "ic_plane"
)

data class FirebaseWallet(
    val id: String = "",
    val name: String = "",
    val subtitle: String = "",
    val balance: Long = 0L,
    val isPrimary: Boolean = false,
    val type: String = "BANK"
)