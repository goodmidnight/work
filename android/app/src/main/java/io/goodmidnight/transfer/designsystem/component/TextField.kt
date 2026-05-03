package io.goodmidnight.transfer.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Alpha
import io.goodmidnight.transfer.designsystem.theme.AppIcons
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * Common text selection colors used across all custom text fields.
 */
@Composable
private fun getMonotoneTextSelectionColors() = TextSelectionColors(
    handleColor = Theme.colorScheme.accent,
    backgroundColor = Theme.colorScheme.accent.copy(alpha = Alpha.LOW)
)

@Composable
fun WFUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    placeHolder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val textColor by animateColorAsState(
        targetValue = if (enabled) Theme.colorScheme.primaryText else Theme.colorScheme.disabledText,
        label = "textColor"
    )
    val outlineColor by animateColorAsState(
        targetValue = if (enabled) Theme.colorScheme.outline else Theme.colorScheme.outline.copy(alpha = Alpha.LOW),
        label = "outlineColor"
    )

    CompositionLocalProvider(LocalTextSelectionColors provides getMonotoneTextSelectionColors()) {
        BasicTextField(
            modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            value = value,
            singleLine = singleLine,
            interactionSource = interactionSource,
            onValueChange = onValueChange,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            enabled = enabled,
            textStyle = Theme.type.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(Theme.colorScheme.accent),
            decorationBox = { text ->
                Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                if (value.isEmpty()) {
                                    BodyMediumText(
                                        text = placeHolder ?: "",
                                        color = Theme.colorScheme.disabledText,
                                    )
                                }
                                text()
                            }
                        }

                        if (value.isNotEmpty()) {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    imageVector = AppIcons.Clear,
                                    contentDescription = "Clear text",
                                    tint = Theme.colorScheme.icon
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = outlineColor)
                }
            }
        )
    }
}

@Composable
fun WFContainerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    placeHolder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val textColor by animateColorAsState(
        targetValue = if (enabled) Theme.colorScheme.primaryText else Theme.colorScheme.disabledText,
        label = "textColor"
    )

    CompositionLocalProvider(LocalTextSelectionColors provides getMonotoneTextSelectionColors()) {
        BasicTextField(
            modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            value = value,
            singleLine = singleLine,
            interactionSource = interactionSource,
            onValueChange = onValueChange,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            enabled = enabled,
            textStyle = Theme.type.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(Theme.colorScheme.accent),
            decorationBox = { text ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(
                            color = Theme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            if (value.isEmpty()) {
                                BodyMediumText(
                                    text = placeHolder ?: "",
                                    color = Theme.colorScheme.disabledText,
                                )
                            }
                            text()
                        }
                    }

                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = AppIcons.Clear,
                                contentDescription = "Clear text",
                                tint = Theme.colorScheme.icon
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun WFSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    placeHolder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor by animateColorAsState(
        targetValue = if (enabled) Theme.colorScheme.primaryText else Theme.colorScheme.disabledText,
        label = "textColor"
    )

    CompositionLocalProvider(LocalTextSelectionColors provides getMonotoneTextSelectionColors()) {
        BasicTextField(
            modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            value = value,
            singleLine = singleLine,
            interactionSource = interactionSource,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
            enabled = enabled,
            textStyle = Theme.type.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(Theme.colorScheme.accent),
            decorationBox = { text ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Theme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            if (value.isEmpty()) {
                                BodyMediumText(
                                    text = placeHolder ?: "",
                                    color = Theme.colorScheme.disabledText,
                                )
                            }
                            text()
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AnimatedVisibility(visible = value.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    imageVector = AppIcons.Clear,
                                    contentDescription = "Clear text",
                                    tint = Theme.colorScheme.icon
                                )
                            }
                        }
                        IconButton(onClick = { onSearch(value) }) {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = "Search",
                                tint = Theme.colorScheme.icon
                            )
                        }
                    }
                }
            }
        )
    }
}


@Composable
@ComponentPreview
fun SearchTextFieldPreview(
    modifier: Modifier = Modifier,
) {
    var value: String by remember { mutableStateOf("") }
    val onValueChange: (String) -> Unit = { value = it }

    Theme {
        Column(
            modifier = modifier
                .background(Theme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            WFSearchTextField(
                value = value,
                onValueChange = onValueChange,
                placeHolder = "Enter a text",
                onSearch = {}
            )
        }
    }
}


@ComponentPreview
@Composable
private fun WFUnderlineTextFieldsPreview() {
    var text1 by remember { mutableStateOf("입력 중...") }
    var text2 by remember { mutableStateOf("") }
    var text3 by remember { mutableStateOf("잘못된 입력") }
    var text4 by remember { mutableStateOf("비활성화") }

    Theme {
        Column(
            modifier = Modifier.background(Theme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WFUnderlineTextField(
                value = text1,
                onValueChange = { text1 = it },
            )
            // 일반 상태
            WFUnderlineTextField(
                value = text2,
                onValueChange = { text2 = it },
            )
            // 에러 상태
            WFUnderlineTextField(
                value = text3,
                onValueChange = { text3 = it },
            )
            // 비활성화 상태
            WFUnderlineTextField(
                value = text4,
                onValueChange = { text4 = it },
                enabled = false
            )
        }
    }
}

@ComponentPreview
@Composable
private fun WFContainerTextFieldsPreview() {
    var text1 by remember { mutableStateOf("입력 중...") }
    var text2 by remember { mutableStateOf("") }
    var text3 by remember { mutableStateOf("잘못된 입력") }
    var text4 by remember { mutableStateOf("비활성화") }

    Theme {
        Column(
            modifier = Modifier.background(Theme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WFContainerTextField(
                value = text1,
                onValueChange = { text1 = it },
            )
            // 일반 상태
            WFContainerTextField(
                value = text2,
                onValueChange = { text2 = it },
            )
            // 에러 상태
            WFContainerTextField(
                value = text3,
                onValueChange = { text3 = it },
            )
            // 비활성화 상태
            WFContainerTextField(
                value = text4,
                onValueChange = { text4 = it },
                enabled = false
            )
        }
    }
}