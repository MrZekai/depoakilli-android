package com.mrzekai.depoakilli.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.BuildConfig
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.ui.theme.ElectricBlue

@Composable
internal fun SettingsDetailScreen(
    listState: LazyListState = rememberLazyListState(),
    privacyOptionsRequired: Boolean,
    canRequestAds: Boolean,
    adFreeRemainingMillis: Long,
    rewardedAdReady: Boolean,
    onOpenLanguageSettings: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit,
    onShareApp: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
    onShowRewardedAd: () -> Unit,
    onOpenPrivacyAccess: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
    keptItemCount: Int = 0,
    onClearKeptItems: () -> Unit = {},
    excludedFolders: List<com.mrzekai.depoakilli.data.ExcludedFolder> = emptyList(),
    onIncludeFolder: (com.mrzekai.depoakilli.data.ExcludedFolder) -> Unit = {},
) {
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var confirmClearKept by rememberSaveable { mutableStateOf(false) }
    if (confirmClearKept) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearKept = false },
            text = { Text(stringResource(R.string.settings_kept_reset_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmClearKept = false
                    onClearKeptItems()
                }) { Text(stringResource(R.string.settings_kept_reset)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClearKept = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showFeedbackDialog) {
        FeedbackDialog(
            onOpenEmailApp = {
                showFeedbackDialog = false
                onSendFeedback()
            },
            onDismiss = { showFeedbackDialog = false },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // V10: files kept in the swipe review; resetting asks first.
        if (keptItemCount > 0) {
            item(key = "settings-kept") {
                SettingsActionRow(
                    title = stringResource(R.string.settings_kept_files, keptItemCount),
                    subtitle = stringResource(R.string.settings_kept_reset),
                    icon = Icons.Outlined.Security,
                    onClick = { confirmClearKept = true },
                )
            }
        }
        // V11: folders the user does not want suggestions from, removable one by one.
        if (excludedFolders.isNotEmpty()) {
            item(key = "settings-excluded-title") {
                Text(stringResource(R.string.settings_excluded_title), fontWeight = FontWeight.Bold)
            }
            items(excludedFolders, key = { "excluded-" + it.volumeRoot + "/" + it.relativePath }) { folder ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, fontWeight = FontWeight.Bold)
                            Text(
                                com.mrzekai.depoakilli.ui.common.bidiWrap(folder.relativePath),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { onIncludeFolder(folder) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.settings_excluded_remove)) }
                    }
                }
            }
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.security_center_title),
                subtitle = stringResource(R.string.security_center_subtitle),
                icon = Icons.Outlined.Security,
                onClick = onOpenPrivacyAccess,
            )
        }
        if (canRequestAds && adFreeRemainingMillis <= 0L && rewardedAdReady) {
            item {
                SettingsActionRow(
                    title = stringResource(R.string.rewarded_ad_title),
                    subtitle = stringResource(R.string.rewarded_ad_subtitle),
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = onShowRewardedAd,
                )
            }
        }
        if (adFreeRemainingMillis > 0L) {
            item {
                SettingsInfoRow(
                    title = stringResource(R.string.ad_free_active_title),
                    subtitle = pluralStringResource(
                        R.plurals.ad_free_active_subtitle,
                        ((adFreeRemainingMillis + 59_999L) / 60_000L).toInt(),
                        ((adFreeRemainingMillis + 59_999L) / 60_000L).toInt(),
                    ),
                    icon = Icons.Outlined.AutoAwesome,
                )
            }
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.language_settings),
                subtitle = stringResource(R.string.language_settings_subtitle_v050),
                icon = Icons.Outlined.Language,
                onClick = onOpenLanguageSettings,
            )
        }
        item { SettingsActionRow(stringResource(R.string.rate_us), stringResource(R.string.rate_us_subtitle), Icons.Outlined.Bolt, onRateApp) }
        item {
            SettingsActionRow(stringResource(R.string.send_feedback), stringResource(R.string.send_feedback_subtitle), Icons.Outlined.Info) {
                showFeedbackDialog = true
            }
        }
        item { SettingsActionRow(stringResource(R.string.share_app), stringResource(R.string.share_app_subtitle), Icons.Outlined.Android, onShareApp) }
        item { SettingsActionRow(stringResource(R.string.privacy_policy), stringResource(R.string.privacy_policy_subtitle), Icons.Outlined.Security) { onOpenLegalPage(LegalPage.PRIVACY) } }
        item { SettingsActionRow(stringResource(R.string.terms_of_service), stringResource(R.string.terms_of_service_subtitle), Icons.Outlined.Info) { onOpenLegalPage(LegalPage.TERMS) } }
        if (privacyOptionsRequired) {
            item { SettingsActionRow(stringResource(R.string.ad_privacy_preferences), stringResource(R.string.ad_privacy_preferences_subtitle), Icons.Outlined.Security, onShowPrivacyOptions) }
        }
        item { SettingsActionRow(stringResource(R.string.about_app), stringResource(R.string.about_app_subtitle), Icons.Outlined.Info) { onOpenLegalPage(LegalPage.ABOUT) } }
    }
}

/**
 * Feedback contact sheet.
 *
 * Opening the mail app directly dropped users without a configured account
 * into the mail app's account setup, with no way to see where to write
 * (QA v43 #10). The address is now shown and selectable, can be copied, and the
 * mail app is offered through Android's app chooser.
 */
@Composable
internal fun FeedbackDialog(
    onOpenEmailApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val supportEmail = BuildConfig.SUPPORT_EMAIL.trim()
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = ElectricBlue) },
        title = { Text(stringResource(R.string.send_feedback), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.feedback_dialog_body))
                if (supportEmail.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            supportEmail,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                        )
                    }
                    if (copied) {
                        Text(
                            stringResource(R.string.feedback_address_copied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenEmailApp) {
                Text(stringResource(R.string.feedback_open_email))
            }
        },
        dismissButton = {
            if (supportEmail.isNotBlank()) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText(supportEmail, supportEmail))
                            copied = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.feedback_copy_address))
                }
            }
        },
    )
}

@Composable
internal fun LegalDetailScreen(page: LegalPage, info: DeviceInfoSnapshot, modifier: Modifier = Modifier) {
    val policyBody = when (page) {
        LegalPage.PRIVACY -> stringResource(R.string.privacy_policy_body_v050)
        LegalPage.TERMS -> stringResource(R.string.terms_of_service_body_v050)
        LegalPage.ABOUT -> stringResource(R.string.about_app_body_v050, info.appVersion) +
            "\n\n" + stringResource(R.string.smart_cleanup_ad_notice)
    }
    val supportEmail = BuildConfig.SUPPORT_EMAIL.trim()
    val body = if (page != LegalPage.ABOUT && supportEmail.isNotBlank()) {
        policyBody + "\n\n" + stringResource(R.string.legal_contact, supportEmail)
    } else {
        policyBody
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(
                    if (page == LegalPage.PRIVACY) Icons.Outlined.Security else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.padding(16.dp).size(34.dp),
                )
            }
        }
        item { Text(stringResource(page.titleRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
        item { Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun SettingsActionRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
internal fun SettingsInfoRow(title: String, subtitle: String, icon: ImageVector) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
