package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Bottom of the app shell: the black tab bar on the tabs; on detail screens,
 * only the navigation-bar area. The app shows no ads (2.0).
 */
@Composable
internal fun AppBottomBar(
    detailScreenOpen: Boolean,
    darkNavigationArea: Boolean = false,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
) {
    if (!detailScreenOpen) {
        // Faz 5: black bottom menu; the selected tab is a dark pill with Lime.
        Column {
            Column(
                modifier = Modifier
                    .background(Black)
                    .navigationBarsPadding(),
            ) {
                NavigationBar(containerColor = Black, contentColor = OnBlack, tonalElevation = 0.dp) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        val title = stringResource(tab.titleRes)
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { onSelectTab(index) },
                            icon = { Icon(tab.icon, contentDescription = title) },
                            label = { Text(title, fontWeight = AppWeights.Strong) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Lime,
                                selectedTextColor = Lime,
                                unselectedIconColor = OnBlackSoft,
                                unselectedTextColor = OnBlackSoft,
                                indicatorColor = BlackChip,
                            ),
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .background(if (darkNavigationArea) Black else Lime)
                .navigationBarsPadding(),
        ) {}
    }
}
