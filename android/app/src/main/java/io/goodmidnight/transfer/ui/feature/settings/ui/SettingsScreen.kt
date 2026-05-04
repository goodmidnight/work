package io.goodmidnight.transfer.ui.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.goodmidnight.transfer.designsystem.component.BodyLargeText
import io.goodmidnight.transfer.designsystem.component.BodyMediumText
import io.goodmidnight.transfer.designsystem.component.CardStyle
import io.goodmidnight.transfer.designsystem.component.LabelMediumText
import io.goodmidnight.transfer.designsystem.component.TitleMediumText
import io.goodmidnight.transfer.designsystem.component.TitleTopBar
import io.goodmidnight.transfer.designsystem.component.Card
import io.goodmidnight.transfer.designsystem.component.ContainerTextField
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.AppIcons
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.feature.settings.data.SettingsState

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsState,
    onBackClick: () -> Unit,
    onChangeDeviceNameClick: () -> Unit,
    onChangeSaveLocationClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onDismissDialogs: () -> Unit,
    onConfirmDeviceName: (String) -> Unit,
    onConfirmSaveLocation: (String) -> Unit,
) {
    if (state.showChangeNameDialog) {
        ChangeSettingDialog(
            title = "Change Device Name",
            currentValue = state.deviceName,
            placeHolder = "Enter new device name",
            onDismiss = onDismissDialogs,
            onConfirm = onConfirmDeviceName
        )
    }

    if (state.showChangeLocationDialog) {
        ChangeSettingDialog(
            title = "Change Save Folder",
            currentValue = state.saveLocation.substringAfter("Downloads/").ifEmpty { "Transfer" },
            placeHolder = "Enter folder name (e.g. MyFiles)",
            prefixText = "Downloads / ",
            onDismiss = onDismissDialogs,
            onConfirm = { subFolder ->
                val formattedLocation = "Downloads/${subFolder.trim('/')}"
                onConfirmSaveLocation(formattedLocation)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TitleTopBar(
            title = "Settings",
            onBack = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            SettingSection(title = "Device Info") {
                SettingItem(
                    title = "Device Name",
                    subtitle = state.deviceName.ifEmpty { "Unknown Device" },
                    onClick = onChangeDeviceNameClick
                )
            }

            SettingSection(title = "Transfer Preferences") {
                SettingItem(
                    title = "Save Location",
                    subtitle = state.saveLocation.ifEmpty { "Downloads/Transfer" },
                    onClick = onChangeSaveLocationClick
                )
            }

            SettingSection(title = "Storage Management") {
                SettingItem(
                    title = "Clear Temporary Cache",
                    subtitle = "Free up space by deleting temporary files created during transfers. (${state.cacheSize})",
                    onClick = onClearCacheClick
                )
            }

            SettingSection(title = "About") {
                SettingItem(
                    title = "App Version",
                    subtitle = "1.0.0",
                    onClick = null
                )
            }
        }
    }
}

/**
 * A highly reusable generic dialog for updating simple string settings (Name, Path, etc.)
 */
@Composable
private fun ChangeSettingDialog(
    title: String,
    currentValue: String,
    placeHolder: String,
    prefixText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var textFieldValue by remember { mutableStateOf(currentValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(style = CardStyle.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TitleMediumText(text = title)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prefixText != null) {
                        LabelMediumText(
                            text = prefixText,
                            color = Theme.colorScheme.secondaryText,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    ContainerTextField(
                        value = textFieldValue,
                        onValueChange = {
                            if (!it.contains("/")) textFieldValue = it
                        },
                        singleLine = true,
                        placeHolder = placeHolder
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        LabelMediumText(
                            text = "Cancel",
                            color = Theme.colorScheme.secondaryText
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(textFieldValue) },
                        enabled = textFieldValue.isNotBlank() // Prevent empty inputs
                    ) {
                        LabelMediumText(
                            text = "Save",
                            color = if (textFieldValue.isNotBlank()) Theme.colorScheme.accent else Theme.colorScheme.secondaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LabelMediumText(
            text = title.uppercase(),
            color = Theme.colorScheme.secondaryText,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(style = CardStyle.DEFAULT) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            BodyLargeText(text = title)
            Spacer(modifier = Modifier.height(4.dp))
            BodyMediumText(
                text = subtitle,
                color = Theme.colorScheme.secondaryText
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = Theme.colorScheme.icon
            )
        }
    }
}

@Composable
@ComponentPreview
private fun SettingsScreenPreview() {
    Theme {
        SettingsScreen(
            state = SettingsState(
                deviceName = "My Android Device",
                saveLocation = "Downloads/TransferApp",
                cacheSize = "24.5 MB",
                showChangeNameDialog = true
            ),
            onBackClick = {},
            onChangeDeviceNameClick = {},
            onChangeSaveLocationClick = {},
            onClearCacheClick = {},
            onDismissDialogs = {},
            onConfirmDeviceName = {},
            onConfirmSaveLocation = {}
        )
    }
}