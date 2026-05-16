package com.example.easyfill_project.screen

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

fun getAppTypography(mode: FontSizeMode): Typography {//creating a customized version of the built-in Typography - override with our size
    return when (mode) {

        FontSizeMode.SMALL -> Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 27.sp),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)

        )

        FontSizeMode.NORMAL -> Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 31.sp),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.4.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.2.sp)
        )

        FontSizeMode.LARGE -> Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 35.6.sp),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.8.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.4.sp),
        )
    }
}