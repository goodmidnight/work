package io.goodmidnight.transfer.designsystem.theme

import androidx.compose.ui.graphics.Color
import io.goodmidnight.transfer.designsystem.theme.Alpha.LOW

val Gray_White = Color(0xFFFFFFFF)
val Gray_50 = Color(0xFFF9FAFB)
val Gray_100 = Color(0xFFF2F4F7)
val Gray_200 = Color(0xFFE4E7EC)
val Gray_300 = Color(0xFFD0D5DD)
val Gray_400 = Color(0xFF98A2B3)
val Gray_500 = Color(0xFF667085)
val Gray_600 = Color(0xFF475467)
val Gray_700 = Color(0xFF344054)
val Gray_800 = Color(0xFF1D2939)
val Gray_900 = Color(0xFF101828)
val Gray_Black = Color(0xFF000000)

val Brand_Black = Gray_900

val Green_500 = Color(0xFF34A853)
val Green_300 = Color(0xFF81C995)

val Red_500 = Color(0xFFEA4335)
val Red_300 = Color(0xFFF2D682)


/**
 * Defines the semantic color palette for the monotone-based design system.
 *
 * @property background The underlying background color of the app.
 * @property surface The color of elevated UI elements like cards or dialogs.
 * @property surfaceVariant A subtle alternative surface color for grouping elements.
 * @property onSurface The color of text and icons drawn on top of the surface.
 * @property accent The primary action color for buttons, active states, and highlights.
 * @property onAccent The text/icon color drawn on top of the accent color.
 * @property primaryText The color for the most important text.
 * @property secondaryText The color for supporting, less prominent text.
 * @property disabledText The color for inactive or disabled components.
 * @property icon The default color for system icons.
 * @property error The color representing a failure or destructive action.
 * @property success The color representing a successful state.
 * @property outline The main color for borders and dividers.
 * @property scrim The semi-transparent overlay color behind modals.
 */
data class ColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,

    val accent: Color,
    val onAccent: Color,

    val primaryText: Color,
    val secondaryText: Color,
    val disabledText: Color,

    val icon: Color,

    val error: Color,
    val success: Color,

    val outline: Color,
    val scrim: Color,
)

internal val lightColorScheme = ColorScheme(
    background = Gray_White,
    surface = Gray_White,
    surfaceVariant = Gray_50,
    onSurface = Gray_900,

    accent = Brand_Black,
    onAccent = Gray_White,

    primaryText = Gray_900,
    secondaryText = Gray_500,
    disabledText = Gray_300,

    icon = Gray_800,

    error = Red_500,
    success = Green_500,

    outline = Gray_200,
    scrim = Gray_Black.copy(alpha = LOW),
)

internal val darkColorScheme = ColorScheme(
    background = Gray_Black,
    surface = Gray_900,
    surfaceVariant = Gray_800,
    onSurface = Gray_White,

    accent = Gray_White,
    onAccent = Brand_Black,

    primaryText = Gray_White,
    secondaryText = Gray_400,
    disabledText = Gray_600,

    icon = Gray_300,

    error = Red_300,
    success = Green_300,

    outline = Gray_700,
    scrim = Gray_Black.copy(alpha = LOW),
)