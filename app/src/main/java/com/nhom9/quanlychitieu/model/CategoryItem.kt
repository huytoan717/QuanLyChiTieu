package com.nhom9.quanlychitieu.model

import androidx.annotation.DrawableRes

data class CategoryItem(
    val title: String,
    val percentage: String,
    val amount: String,
    val progress: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val badgeBgRes: Int,
    @param:DrawableRes val progressDrawableRes: Int
)