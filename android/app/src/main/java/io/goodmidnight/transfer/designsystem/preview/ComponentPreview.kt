package io.goodmidnight.transfer.designsystem.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * [ComponentPreview]
 * - A custom annotation used to display [Composable] components in the Preview pane.
 * - Automatically generates both Light (Day) and Dark (Night) mode previews.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Night", group = "Component", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Day", group = "Component", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class ComponentPreview