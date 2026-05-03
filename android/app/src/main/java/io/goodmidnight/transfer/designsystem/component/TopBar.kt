package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.modifier.noRippleClickable
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.AppIcons
import io.goodmidnight.transfer.designsystem.theme.Theme

/**
 * Standard top app bar colors for the monotone design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun getTopAppBarColors() = TopAppBarDefaults.topAppBarColors().copy(
    containerColor = Theme.colorScheme.background,
    scrolledContainerColor = Theme.colorScheme.background,
    navigationIconContentColor = Theme.colorScheme.icon,
    titleContentColor = Theme.colorScheme.primaryText,
    actionIconContentColor = Theme.colorScheme.icon
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = {
                TitleLargeText(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            },
            navigationIcon = {
                onBack?.let { backAction ->
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier
                                .size(24.dp)
                                .noRippleClickable { backAction() },
                        )
                    }
                }
            },
            actions = actions,
            colors = getTopAppBarColors(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            TitleLargeText(text = title)
        },
        navigationIcon = {},
        actions = actions,
        colors = getTopAppBarColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onBack: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            WFSearchTextField(
                value = value,
                onValueChange = onValueChange,
                onSearch = onSearch,
                focusRequester = focusRequester
            )
        },
        navigationIcon = {
            onBack?.let { backAction ->
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(24.dp)
                            .noRippleClickable { backAction() },
                    )
                }
            }
        },
        colors = getTopAppBarColors(),
    )
}


@Composable
@ComponentPreview
fun TitleTopBarPreview() {
    Theme {
        TitleTopBar("타이틀", onBack = {})
    }
}

@Composable
@ComponentPreview
fun MainTopBarPreview() {
    Theme {
        MainTopBar("타이틀")
    }
}


@Composable
@ComponentPreview
fun SearchTopBarPreview() {
    Theme {
        SearchTopBar(
            value = "",
            onSearch = {},
            onValueChange = {},
        )
    }
}