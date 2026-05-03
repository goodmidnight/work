package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.theme.Alpha
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * A compact element that represents an input, attribute, or action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            LabelMediumText(
                text = text,
                color = when {
                    !enabled -> Theme.colorScheme.disabledText
                    selected -> Theme.colorScheme.onAccent
                    else -> Theme.colorScheme.secondaryText
                }
            )
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Theme.colorScheme.accent,
            containerColor = Theme.colorScheme.surface,
            disabledSelectedContainerColor = Theme.colorScheme.accent.copy(alpha = Alpha.LOW),
            disabledContainerColor = Theme.colorScheme.surface.copy(alpha = Alpha.LOW)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color.Transparent else Theme.colorScheme.outline
        )
    )
}