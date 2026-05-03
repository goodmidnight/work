package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * Defines the visual style of the [Card].
 */
enum class CardStyle {
    DEFAULT,   // Uses the standard surface color
    VARIANT,   // Uses the surfaceVariant color for subtle contrast
    OUTLINED   // Transparent background with a distinct outline
}

/**
 * A generic container component for grouping related content and actions.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.DEFAULT,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when (style) {
                CardStyle.DEFAULT -> Theme.colorScheme.surface
                CardStyle.VARIANT -> Theme.colorScheme.surfaceVariant
                CardStyle.OUTLINED -> Color.Transparent
            },
            contentColor = Theme.colorScheme.primaryText
        ),
        border = if (style == CardStyle.OUTLINED) {
            BorderStroke(1.dp, Theme.colorScheme.outline)
        } else null,
        content = content
    )
}