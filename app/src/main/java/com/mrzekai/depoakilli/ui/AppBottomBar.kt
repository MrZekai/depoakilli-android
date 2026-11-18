package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.ads.BannerAd
import com.mrzekai.depoakilli.ui.theme.ElectricBlue

/**
 * Bottom of the app shell (T3.4): on the tabs, the banner (when allowed) above
 * the tab bar; on detail screens, only the banner. At least 24 dp separate
 * the banner from any action.
 */
@Composable
internal fun AppBottomBar(
    detailScreenOpen: Boolean,
    bannerAllowed: Boolean,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
) {
    if (!detailScreenOpen) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
        ) {
            if (bannerAllowed) {
                // At least 24 dp between the banner and any action above or below it.
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                Spacer(Modifier.height(24.dp))
                BannerAd(canRequestAds = true)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
            NavigationBar(containerColor = Color(0xFF07132C)) {
                AppTab.entries.forEachIndexed { index, tab ->
                    val title = stringResource(tab.titleRes)
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { onSelectTab(index) },
                        icon = { Icon(tab.icon, contentDescription = title) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            selectedTextColor = ElectricBlue,
                            indicatorColor = Color(0xFF123A75),
                        ),
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .background(Color(0xFF06112A))
                .navigationBarsPadding(),
        ) {
            if (bannerAllowed) {
                HorizontalDivider(color = Color.White.copy(alpha = .10f))
                Spacer(Modifier.height(24.dp))
                BannerAd(canRequestAds = true)
            }
        }
    }
}
