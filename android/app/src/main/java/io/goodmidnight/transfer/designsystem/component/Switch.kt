package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * A toggle switch styled for the monotone design system.
 */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Theme.colorScheme.surface,
            checkedTrackColor = Theme.colorScheme.accent,
            checkedBorderColor = Theme.colorScheme.accent,
            uncheckedThumbColor = Theme.colorScheme.icon,
            uncheckedTrackColor = Theme.colorScheme.surfaceVariant,
            uncheckedBorderColor = Theme.colorScheme.outline
        )
    )
}

@Composable
@ComponentPreview
private fun SwitchPreview() {
    Theme {
        Column {
            ToggleSwitch(checked = true, onCheckedChange = {})
            ToggleSwitch(checked = false, onCheckedChange = {})
        }
    }
}