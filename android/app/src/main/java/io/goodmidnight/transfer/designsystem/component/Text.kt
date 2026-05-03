package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme

@Composable
fun HeadlineLargeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.headlineLarge,
        overflow = overflow,
        maxLines = maxLines,
        textAlign = textAlign,
        textDecoration = textDecoration
    )
}

@Composable
fun TitleLargeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.titleLarge,
        overflow = overflow,
        maxLines = maxLines,
        textAlign = textAlign,
    )
}

@Composable
fun TitleMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.titleMedium,
        overflow = overflow,
        maxLines = maxLines,
        textAlign = textAlign,
    )
}

@Composable
fun TitleSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.titleSmall,
        overflow = overflow,
        maxLines = maxLines,
        textAlign = textAlign,
    )
}

@Composable
fun BodyLargeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.bodyLarge,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

@Composable
fun BodyMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.bodyMedium,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        fontWeight = fontWeight
    )
}

@Composable
fun LabelMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.labelMedium
    )
}

@Composable
fun LabelSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Theme.colorScheme.primaryText,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Theme.type.labelSmall
    )
}


@Composable
@ComponentPreview
fun TextPreview() {
    Theme {
        Column {
            HeadlineLargeText(text = "Headline Large (28sp Bold)")
            TitleLargeText(text = "Title Large (22sp Bold)")
            TitleMediumText(text = "Title Medium (18sp SemiBold)")
            TitleSmallText(text = "Title Small (16sp SemiBold)")
            BodyLargeText(text = "Body Large (16sp Normal)")
            BodyMediumText(text = "Body Medium (14sp Normal)")
            LabelMediumText(text = "Label Medium (12sp Medium)")
            LabelSmallText(text = "Label Small (10sp Medium)")
        }
    }
}