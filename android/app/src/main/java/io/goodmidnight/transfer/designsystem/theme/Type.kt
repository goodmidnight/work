package io.goodmidnight.transfer.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.goodmidnight.transfer.R.font

data class Type(
    val headlineLarge: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    val titleLarge: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    val titleSmall: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = PRETENDARD,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp
    ),
) {
    companion object {
        val PRETENDARD = FontFamily(
            Font(font.pretendard_bold, FontWeight.Bold, FontStyle.Normal),
            Font(font.pretendard_black, FontWeight.Black, FontStyle.Normal),
            Font(font.pretendard_medium, FontWeight.Medium, FontStyle.Normal),
            Font(font.pretendard_regular, FontWeight.Normal, FontStyle.Normal),
            Font(font.pretendard_semi_bold, FontWeight.SemiBold, FontStyle.Normal),
            Font(font.pretendard_light, FontWeight.Light, FontStyle.Normal),
            Font(font.pretendard_thin, FontWeight.Thin, FontStyle.Normal),
            Font(font.pretendard_extra_bold, FontWeight.ExtraBold, FontStyle.Normal),
            Font(font.pretendard_extra_light, FontWeight.ExtraLight, FontStyle.Normal),
        )
    }
}


