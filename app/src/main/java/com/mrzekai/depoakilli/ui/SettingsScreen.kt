package com.mrzekai.depoakilli.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.BuildConfig
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.ui.common.AppSheet
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.CardRow
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageTitle
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.SheetBody
import com.mrzekai.depoakilli.ui.common.SheetTitle
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Settings (T5.10) on Lime: black groups of 52 dp rows with a Lime status
 * and an arrow — Smart Cleaner Pro; access and language; kept files and
 * folders not suggested; rate, share, feedback and privacy & terms.
 */
@Composable
internal fun SettingsDetailScreen(
    listState: LazyListState = rememberLazyListState(),
    isPro: Boolean = false,
    onOpenPro: () -> Unit = {},
    onOpenLanguageSettings: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit,
    onShareApp: () -> Unit,
    onOpenPrivacyAccess: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
    keptItemCount: Int = 0,
    onClearKeptItems: () -> Unit = {},
    excludedFolders: List<com.mrzekai.depoakilli.data.ExcludedFolder> = emptyList(),
    onIncludeFolder: (com.mrzekai.depoakilli.data.ExcludedFolder) -> Unit = {},
    hasAllFilesAccess: Boolean = false,
    hasUsageAccess: Boolean = false,
) {
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var confirmClearKept by rememberSaveable { mutableStateOf(false) }
    var showExcluded by rememberSaveable { mutableStateOf(false) }
    var showLegal by rememberSaveable { mutableStateOf(false) }
    val on = stringResource(R.string.settings_status_on)
    val off = stringResource(R.string.settings_status_off)
    val locale = LocalConfiguration.current.locales[0]
    val languageName = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

    if (confirmClearKept) {
        AppSheet(onDismiss = { confirmClearKept = false }) {
            SheetTitle(stringResource(R.string.settings_kept_files, keptItemCount))
            SheetBody(stringResource(R.string.settings_kept_reset_confirm))
            PillButton(
                text = stringResource(R.string.settings_kept_reset),
                onClick = {
                    confirmClearKept = false
                    onClearKeptItems()
                },
                style = PillStyle.LIME,
            )
            LinkButton(
                stringResource(R.string.cancel),
                onClick = { confirmClearKept = false },
                color = OnBlack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showExcluded) {
        AppSheet(onDismiss = { showExcluded = false }) {
            SheetTitle(stringResource(R.string.settings_excluded_title))
            // V11: folders the user does not want suggestions from, removable one by one.
            excludedFolders.forEach { folder ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, color = OnBlack, fontWeight = AppWeights.Strong)
                        Text(
                            com.mrzekai.depoakilli.ui.common.bidiWrap(folder.relativePath),
                            color = OnBlackSoft,
                            fontSize = 13.sp,
                        )
                    }
                    LinkButton(
                        stringResource(R.string.settings_excluded_remove),
                        onClick = { onIncludeFolder(folder) },
                        color = Lime,
                    )
                }
            }
            if (excludedFolders.isEmpty()) SheetBody(stringResource(R.string.settings_status_off), soft = true)
        }
    }
    if (showLegal) {
        AppSheet(onDismiss = { showLegal = false }) {
            SheetTitle(stringResource(R.string.settings_privacy_terms))
            listOf(LegalPage.PRIVACY, LegalPage.TERMS, LegalPage.ABOUT).forEach { page ->
                CardRow(
                    title = stringResource(page.titleRes),
                    onClick = {
                        showLegal = false
                        onOpenLegalPage(page)
                    },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
            CardRow(
                title = stringResource(R.string.security_center_title),
                onClick = {
                    showLegal = false
                    onOpenPrivacyAccess()
                },
            )
        }
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

    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "settings-pro") {
                SettingsGroup {
                    // 2.0 Faz 1: the subscription page; Pro unlocks nothing yet.
                    CardRow(
                        title = stringResource(R.string.pro_title),
                        status = if (isPro) on else null,
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = onOpenPro,
                    )
                }
            }
            item(key = "settings-access") {
                SettingsGroup {
                    CardRow(
                        title = stringResource(R.string.security_storage_access),
                        status = if (hasAllFilesAccess) on else off,
                        icon = Icons.Outlined.Storage,
                        onClick = onOpenPrivacyAccess,
                    )
                    CardDivider()
                    CardRow(
                        title = stringResource(R.string.security_usage_access),
                        status = if (hasUsageAccess) on else off,
                        icon = Icons.Outlined.QueryStats,
                        onClick = onOpenPrivacyAccess,
                    )
                    CardDivider()
                    CardRow(
                        title = stringResource(R.string.language_settings),
                        status = languageName,
                        icon = Icons.Outlined.Language,
                        onClick = onOpenLanguageSettings,
                    )
                }
            }
            item(key = "settings-choices") {
                SettingsGroup {
                    // V10: files kept in the swipe review; resetting asks first.
                    CardRow(
                        title = stringResource(R.string.settings_kept_row),
                        status = keptItemCount.toString(),
                        icon = Icons.Outlined.BookmarkBorder,
                        onClick = if (keptItemCount > 0) ({ confirmClearKept = true }) else null,
                        showArrow = keptItemCount > 0,
                    )
                    CardDivider()
                    CardRow(
                        title = stringResource(R.string.settings_excluded_row),
                        status = excludedFolders.size.toString(),
                        icon = Icons.Outlined.FolderOff,
                        onClick = if (excludedFolders.isNotEmpty()) ({ showExcluded = true }) else null,
                        showArrow = excludedFolders.isNotEmpty(),
                    )
                }
            }
            item(key = "settings-about") {
                SettingsGroup {
                    CardRow(stringResource(R.string.rate_us), icon = Icons.Outlined.StarOutline, onClick = onRateApp)
                    CardDivider()
                    CardRow(stringResource(R.string.share_app), icon = Icons.Outlined.Share, onClick = onShareApp)
                    CardDivider()
                    CardRow(
                        stringResource(R.string.send_feedback),
                        icon = Icons.Outlined.MailOutline,
                        onClick = { showFeedbackDialog = true },
                    )
                    CardDivider()
                    CardRow(
                        stringResource(R.string.settings_privacy_terms),
                        icon = Icons.Outlined.Policy,
                        onClick = { showLegal = true },
                    )
                }
            }
        }
    }
}

/** One black group of settings rows. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    BlackCard(contentPadding = PaddingValues(vertical = 4.dp), content = content)
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
        icon = { Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = Lime) },
        title = { Text(stringResource(R.string.send_feedback), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.feedback_dialog_body))
                if (supportEmail.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            supportEmail,
                            fontWeight = FontWeight.Bold,
                            color = Lime,
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
        LegalPage.ABOUT -> stringResource(R.string.about_app_body_v050, info.appVersion)
    }
    val supportEmail = BuildConfig.SUPPORT_EMAIL.trim()
    val body = if (page != LegalPage.ABOUT && supportEmail.isNotBlank()) {
        policyBody + "\n\n" + stringResource(R.string.legal_contact, supportEmail)
    } else {
        policyBody
    }
    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PageTitle(stringResource(page.titleRes), size = 32) }
            item {
                BlackCard(contentPadding = PaddingValues(18.dp)) {
                    Text(body, color = OnBlack, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}
