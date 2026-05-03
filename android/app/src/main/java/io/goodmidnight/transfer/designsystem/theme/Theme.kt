package io.goodmidnight.transfer.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable (() -> Unit),
) {
    CompositionLocalProvider(
        LocalColorScheme provides if (darkTheme) darkColorScheme else lightColorScheme,
        LocalType provides Type(),
        content = content
    )
}

object Theme {
    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalColorScheme.current

    val type: Type
        @Composable
        @ReadOnlyComposable
        get() = LocalType.current
}

val LocalColorScheme =
    staticCompositionLocalOf<ColorScheme> { error("CompositionLocal ColorScheme not present") }
val LocalType = staticCompositionLocalOf<Type> { error("CompositionLocal Type not present") }
