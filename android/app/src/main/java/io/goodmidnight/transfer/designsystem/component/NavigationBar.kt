package io.goodmidnight.transfer.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.modifier.noRippleClickable
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.AppIcons
import io.goodmidnight.transfer.designsystem.theme.Theme

data class NavigationItem(
    val name: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Bottom navigation bar for top-level destinations.
 * Uses accent color to indicate the currently selected item.
 */
@Composable
fun NavigationBar(
    items: List<NavigationItem>,
    currentRoute: String?,
    onItemClick: (NavigationItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = Theme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
            .border(
                border = BorderStroke(1.dp, Theme.colorScheme.outline),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
            .padding(vertical = 8.dp),
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route

            val textColor: Color by animateColorAsState(
                if (isSelected) Theme.colorScheme.accent else Theme.colorScheme.secondaryText,
                label = "nav_text_color"
            )
            val iconColor: Color by animateColorAsState(
                if (isSelected) Theme.colorScheme.accent else Theme.colorScheme.icon,
                label = "nav_icon_color"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .noRippleClickable { onItemClick(item) },
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconColor
                )
                LabelMediumText(
                    text = item.label,
                    color = textColor
                )
            }
        }
    }
}

@Composable
@ComponentPreview
private fun BottomNavigationBarPreview() {
    Theme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colorScheme.background),
            verticalArrangement = Arrangement.Bottom
        ) {
            NavigationBar(
                items = listOf(
                    NavigationItem("홈", "홈", AppIcons.Home, "home"),
                    NavigationItem("폴더", "폴더", AppIcons.CreateNewFolder, "folder"),
                    NavigationItem("설정", "설정", AppIcons.Search, "settings") // AppIcons에 추가된 아이콘 가정
                ),
                currentRoute = "home",
                onItemClick = {}
            )
        }
    }
}