package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent

@Composable
internal fun DialogHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun FilePreviewDialog(
    file: SmartPreviewFile,
    selected: Boolean?,
    reason: String?,
    onToggleSelection: (() -> Unit)?,
    onDismiss: () -> Unit,
    onExcludeFolder: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SmartBackground,
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = file.name, onBack = onDismiss)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF06132F)),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaPreviewContent(file.toMediaPreview())
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartCard),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            file.name,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 17.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PreviewInfoPill(
                                text = ByteFormatter.format(file.sizeBytes),
                                accent = SmartCyan,
                                modifier = Modifier.weight(1f),
                            )
                            PreviewInfoPill(
                                text = previewKindLabel(file),
                                accent = Color(0xFF9A63FF),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            file.relativePath.ifBlank { stringResource(R.string.smart_path_unknown) }.let(::bidiWrap),
                            color = Color(0xFFA9B9D5),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (!reason.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFF102850),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SmartCyan.copy(alpha = .26f)),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.smart_reason_title),
                                        color = SmartCyan,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        reason,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        if (selected != null && onToggleSelection != null) {
                            val selectionColor = if (selected) Color(0xFF0F4938) else Color(0xFF152C52)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(selectionColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) SmartGreen.copy(alpha = .72f) else Color(0xFF6B85AD),
                                        shape = RoundedCornerShape(18.dp),
                                    )
                                    .clickable(onClick = onToggleSelection)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleSelection() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SmartGreen,
                                        uncheckedColor = Color(0xFFB7C6DE),
                                        checkmarkColor = Color(0xFF03120D),
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(if (selected) R.string.smart_included_in_cleanup else R.string.smart_not_included_in_cleanup),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text(
                                        stringResource(if (selected) R.string.smart_selection_remove_hint else R.string.smart_selection_add_hint),
                                        color = Color(0xFFC0CEE4),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        // V11: "this is my archive" for the file's folder.
                        if (onExcludeFolder != null) {
                            TextButton(onClick = onExcludeFolder, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.exclude_folder_action))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PreviewInfoPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = .14f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .36f)),
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun compactRelativePath(path: String): String {
    val clean = path.trim().trim('/')
    if (clean.isBlank()) return "—"
    val parts = clean.split('/').filter(String::isNotBlank)
    return bidiWrap(parts.takeLast(2).joinToString("/"))
}

@Composable
internal fun previewKindLabel(file: SmartPreviewFile): String {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.mimeType.startsWith("image/") -> stringResource(R.string.smart_file_type_image)
        file.mimeType.startsWith("video/") -> stringResource(R.string.smart_file_type_video)
        file.mimeType.startsWith("audio/") -> stringResource(R.string.smart_file_type_audio)
        extension == "apk" -> stringResource(R.string.smart_file_type_apk)
        extension in setOf("zip", "rar", "7z", "tar", "gz") -> stringResource(R.string.smart_file_type_archive)
        file.mimeType.startsWith("text/") || extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") ->
            stringResource(R.string.smart_file_type_document)
        else -> stringResource(R.string.smart_file_type_other)
    }
}

internal fun categoryAccent(category: CleanCategory): Color = when (category) {
    CleanCategory.JUNK -> Color(0xFF0B84FF)
    CleanCategory.DUPLICATE -> Color(0xFF8A48F5)
    CleanCategory.SCREENSHOT -> Color(0xFFEB4CC4)
    CleanCategory.LARGE_FILE -> Color(0xFFFFA31A)
    CleanCategory.OLD_DOWNLOAD -> Color(0xFF16A8D8)
    CleanCategory.APK_PACKAGE -> Color(0xFF5ECA45)
    CleanCategory.APP_CACHE -> Color(0xFF15B9C8)
    CleanCategory.WHATSAPP_MEDIA -> Color(0xFF19B968)
}

internal fun categoryVisual(category: CleanCategory): ImageVector = when (category) {
    CleanCategory.JUNK -> Icons.Outlined.DeleteSweep
    CleanCategory.DUPLICATE -> Icons.Outlined.ContentCopy
    CleanCategory.SCREENSHOT -> Icons.Outlined.PhotoLibrary
    CleanCategory.LARGE_FILE -> Icons.Outlined.VideoFile
    CleanCategory.OLD_DOWNLOAD -> Icons.Outlined.Folder
    CleanCategory.APK_PACKAGE -> Icons.Outlined.Android
    CleanCategory.APP_CACHE -> Icons.Outlined.CleaningServices
    CleanCategory.WHATSAPP_MEDIA -> Icons.Outlined.Chat
}

internal fun storageAccent(type: StorageFileType): Color = when (type) {
    StorageFileType.IMAGES -> Color(0xFFDA4DD7)
    StorageFileType.VIDEOS -> Color(0xFF2078F5)
    StorageFileType.AUDIO -> Color(0xFF16B7B7)
    StorageFileType.DOCUMENTS -> Color(0xFF7B55E8)
    StorageFileType.ARCHIVES -> Color(0xFFE78B19)
    StorageFileType.APK -> Color(0xFF5BBE35)
    StorageFileType.OTHER -> Color(0xFF72809B)
}

internal fun storageTypeVisual(type: StorageFileType): ImageVector = when (type) {
    StorageFileType.IMAGES -> Icons.Outlined.PhotoLibrary
    StorageFileType.VIDEOS -> Icons.Outlined.VideoFile
    StorageFileType.AUDIO -> Icons.Outlined.MusicNote
    StorageFileType.DOCUMENTS -> Icons.Outlined.Description
    StorageFileType.ARCHIVES -> Icons.Outlined.Folder
    StorageFileType.APK -> Icons.Outlined.Android
    StorageFileType.OTHER -> Icons.Outlined.InsertDriveFile
}

/** Files at the volume root have no folder to exclude (V11). */
internal fun canExcludeFolder(relativePath: String): Boolean =
    com.mrzekai.depoakilli.data.ExcludedFolder.normalize(relativePath).isNotEmpty()
