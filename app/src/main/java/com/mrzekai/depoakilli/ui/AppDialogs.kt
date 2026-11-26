package com.mrzekai.depoakilli.ui

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.common.AppSheet
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PermanentDeleteSheet
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.SheetBody
import com.mrzekai.depoakilli.ui.common.SheetTitle
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack

/**
 * Files that could not go to Trash: a separate, explicit permanent-delete
 * question (T5.3). The safe choice, "Cancel", is the big button.
 */
@Composable
internal fun TrashFallbackDialog(
    fallback: TrashFallback,
    bytes: Long,
    onDeletePermanently: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = fallback.ids.size
    PermanentDeleteSheet(
        bytes = bytes,
        body = pluralStringResource(
            if (fallback.folderMissing) R.plurals.trash_folder_missing_body else R.plurals.trash_full_body,
            count,
            count,
        ),
        onDismiss = onDismiss,
        onDeletePermanently = onDeletePermanently,
    )
}

/**
 * The app's own explanation shown before Android's access screen opens
 * (T5.2): three promises, then the permission's name and its single use
 * (Play's prominent-disclosure content), "Continue" and "Not now".
 */
@Composable
internal fun AccessDisclosureDialog(
    disclosure: AccessDisclosure,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val allFiles = disclosure == AccessDisclosure.ALL_FILES
    AppSheet(onDismiss = onDismiss) {
        SheetTitle(
            stringResource(if (allFiles) R.string.disclosure_title_v2 else R.string.disclosure_usage_title),
        )
        listOf(
            R.string.disclosure_promise_1,
            R.string.disclosure_promise_2,
            R.string.disclosure_promise_3,
        ).forEach { promise ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Lime,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(promise),
                    color = OnBlack,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = AppWeights.Strong,
                )
            }
        }
        SheetBody(
            stringResource(if (allFiles) R.string.disclosure_note_v2 else R.string.disclosure_usage_body),
            soft = true,
        )
        // P3: Xiaomi / Redmi / POCO show their own warning with a countdown for
        // Usage access. The screen is the system's; we only say it is coming.
        if (!allFiles && DeviceFamily.isXiaomi(Build.MANUFACTURER, Build.BRAND)) {
            Text(
                stringResource(R.string.disclosure_xiaomi_warning),
                color = AdLabel,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = AppWeights.Strong,
            )
        }
        PillButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            style = PillStyle.LIME,
        )
        LinkButton(
            text = stringResource(R.string.action_not_now),
            onClick = onDismiss,
            color = OnBlack,
            modifier = Modifier.fillMaxWidth(),
        )
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

/** Phone makers whose system adds its own screens to permission flows (P3). */
internal object DeviceFamily {
    private val XIAOMI = setOf("xiaomi", "redmi", "poco")

    fun isXiaomi(manufacturer: String?, brand: String?): Boolean =
        manufacturer.orEmpty().lowercase() in XIAOMI || brand.orEmpty().lowercase() in XIAOMI
}
