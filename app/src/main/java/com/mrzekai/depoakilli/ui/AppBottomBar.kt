package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.mrzekai.depoakilli.ads.BannerAd
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Bottom of the app shell (T3.4): on the tabs, the banner (when allowed) above
 * the tab bar; on detail screens, only the banner. At least 24 dp separate
 * the banner from any action.
 */
@Composable
internal fun AppBottomBar(
    detailScreenOpen: Boolean,
    bannerAllowed: Boolean,
    darkNavigationArea: Boolean = false,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
) {
    if (!detailScreenOpen) {
        // Faz 5: black bottom menu; the banner sits above it (at least 24 dp
        // from any action) and the selected tab is a dark pill with Lime.
        Column {
            if (bannerAllowed) {
                // The 24 dp gap from the page's actions is page colour (Lime), so
                // no empty black strip shows above the banner; the banner then
                // sits right on the menu (Faz 5B, P3).
                Spacer(Modifier.fillMaxWidth().height(24.dp).background(Lime))
            }
            Column(
                modifier = Modifier
                    .background(Black)
                    .navigationBarsPadding(),
            ) {
                if (bannerAllowed) {
                    BannerAd(canRequestAds = true)
                }
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
        ) {
            if (bannerAllowed) {
                Spacer(Modifier.height(24.dp))
                BannerAd(canRequestAds = true)
            }
        }
    }
}
