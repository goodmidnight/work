package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Alpha
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * Custom RadioButton styled for the monotone design system.
 */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        interactionSource = interactionSource,
        colors = RadioButtonDefaults.colors(
            selectedColor = Theme.colorScheme.accent,
            unselectedColor = Theme.colorScheme.icon,
            disabledSelectedColor = Theme.colorScheme.accent.copy(alpha = Alpha.LOW),
            disabledUnselectedColor = Theme.colorScheme.icon.copy(alpha = Alpha.LOW)
        ),
    )
}

@ComponentPreview
@Composable
private fun RadioButtonPreview() {
    var isSelected1 by remember { mutableStateOf(true) }
    var isSelected2 by remember { mutableStateOf(false) }

    Theme {
        Column {
            RadioButton(selected = isSelected1, onClick = { isSelected1 = !isSelected1 })
            RadioButton(selected = isSelected2, onClick = { isSelected2 = !isSelected2 })
            RadioButton(selected = true, onClick = null, enabled = false)
        }
    }
}