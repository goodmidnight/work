package io.goodmidnight.transfer.ui.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Builds an [Intent] to navigate the user directly to the application's detail settings screen.
 * Useful for requesting permissions that the user has permanently denied.
 *
 * @return An [Intent] configured to open this app's specific settings page.
 */
fun Context.buildSettingsIntent(): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null)
)