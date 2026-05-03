package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * A monotone styled slider allowing users to make selections from a range of values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(value) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Slider(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Theme.colorScheme.accent,
            activeTrackColor = Theme.colorScheme.accent,
            activeTickColor = Theme.colorScheme.onAccent,
            inactiveTrackColor = Theme.colorScheme.surfaceVariant,
            inactiveTickColor = Theme.colorScheme.outline
        )
    )
}

@Composable
@ComponentPreview
private fun SliderPreview() {
    var sliderValue by remember { mutableFloatStateOf(1.5f) }

    Theme {
        Surface(color = Theme.colorScheme.background) {
            Slider(
                modifier = Modifier.padding(16.dp),
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0.5f..2f,
                steps = 5,
            )
        }
    }
}