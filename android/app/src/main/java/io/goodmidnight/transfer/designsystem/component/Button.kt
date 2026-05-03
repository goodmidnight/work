package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.theme.Alpha
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * The primary action button used for main interactions in the application.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Theme.colorScheme.accent,
            contentColor = Theme.colorScheme.onAccent,
            disabledContainerColor = Theme.colorScheme.accent.copy(alpha = Alpha.LOW),
            disabledContentColor = Theme.colorScheme.onAccent.copy(alpha = Alpha.MEDIUM)
        )
    ) {
        TitleSmallText(
            text = text,
            color = if (enabled) Theme.colorScheme.onAccent else Theme.colorScheme.onAccent.copy(alpha = Alpha.MEDIUM)
        )
    }
}

/**
 * A secondary action button used for alternative or less prominent actions.
 * Uses an outlined style to remain visually subtle.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        border = BorderStroke(1.dp, if (enabled) Theme.colorScheme.outline else Theme.colorScheme.outline.copy(alpha = Alpha.LOW)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Theme.colorScheme.primaryText,
            disabledContentColor = Theme.colorScheme.disabledText
        )
    ) {
        TitleSmallText(
            text = text,
            color = if (enabled) Theme.colorScheme.primaryText else Theme.colorScheme.disabledText
        )
    }
}