package com.nhom9.quanlychitieu.model

import androidx.annotation.DrawableRes

data class TransactionItem(
    val title: String,
    val time: String,
    val amount: String,
    val statusOrPayment: String,
    val isPositive: Boolean,
    @param:DrawableRes val iconRes: Int
)