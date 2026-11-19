package com.nhom9.quanlychitieu.model

enum class Category(
    val id: String,
    val displayName: String,
    val iconResName: String
) {
    FOOD("cat_food", "Ăn uống & Cà phê", "ic_utensils"),
    TRANSPORT("cat_transport", "Di chuyển & Xăng xe", "ic_car"),
    HOUSING("cat_housing", "Thuê nhà & Hóa đơn", "ic_house"),
    SHOPPING("cat_shopping", "Mua sắm & Giải trí", "ic_shopping_cart"),
    ENTERTAINMENT("cat_entertainment", "Giải trí & Game", "ic_gamepad"),
    HEALTH("cat_health", "Sức khỏe & Y tế", "ic_heart"),
    EDUCATION("cat_education", "Giáo dục & Học tập", "ic_school"),
    OTHER("cat_other", "Khác", "ic_more_horiz");

    companion object {
        fun fromId(id: String): Category {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: FOOD
        }

        fun fromName(name: String): Category {
            return when {
                name.contains("Ăn", ignoreCase = true) -> FOOD
                name.contains("chuyển", ignoreCase = true) -> TRANSPORT
                name.contains("nhà", ignoreCase = true) || name.contains("Thuê", ignoreCase = true) || name.contains("Hóa đơn", ignoreCase = true) -> HOUSING
                name.contains("Mua", ignoreCase = true) -> SHOPPING
                name.contains("Giải", ignoreCase = true) || name.contains("game", ignoreCase = true) -> ENTERTAINMENT
                name.contains("khỏe", ignoreCase = true) -> HEALTH
                name.contains("dục", ignoreCase = true) -> EDUCATION
                else -> OTHER
            }
        }
    }
}