package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.theme.ElectricBlue

/** Files that could not go to Trash: a separate, explicit permanent-delete question. */
@Composable
internal fun TrashFallbackDialog(
    fallback: TrashFallback,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = fallback.ids.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_full_title), fontWeight = FontWeight.Black) },
        text = {
            Text(
                pluralStringResource(
                    if (fallback.folderMissing) R.plurals.trash_folder_missing_body else R.plurals.trash_full_body,
                    count,
                    count,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDeletePermanently) {
                Text(stringResource(R.string.trash_delete_forever))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The app's own explanation shown before Android's access screen opens. */
@Composable
internal fun AccessDisclosureDialog(
    disclosure: AccessDisclosure,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val allFiles = disclosure == AccessDisclosure.ALL_FILES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (allFiles) R.string.disclosure_all_files_title else R.string.disclosure_usage_title),
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Text(stringResource(if (allFiles) R.string.disclosure_all_files_body else R.string.disclosure_usage_body))
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.disclosure_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.disclosure_not_now))
            }
        },
    )
}

/** Covers the app while a full-screen ad is showing. */
@Composable
internal fun FullScreenAdCover() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = ElectricBlue)
            Text(
                stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

internal fun scanFocusTitleRes(focus: ScanFocus): Int = when (focus) {
    ScanFocus.SMART -> R.string.scan_focus_smart
    ScanFocus.DEEP -> R.string.scan_focus_deep
    ScanFocus.JUNK -> R.string.scan_focus_junk
    ScanFocus.DUPLICATES -> R.string.scan_focus_duplicates
    ScanFocus.LARGE_FILES -> R.string.scan_focus_large
    ScanFocus.MEDIA -> R.string.scan_focus_media
    ScanFocus.SCREENSHOTS -> R.string.category_screenshots
    ScanFocus.DOWNLOADS -> R.string.scan_focus_downloads
    ScanFocus.APKS -> R.string.scan_focus_apks
    ScanFocus.ANALYZE -> R.string.scan_focus_analyze
}
