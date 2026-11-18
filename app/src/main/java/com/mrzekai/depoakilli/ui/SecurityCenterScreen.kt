package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

@Composable
internal fun SecurityCenterScreen(
    state: CleanerUiState,
    onManageAllFilesAccess: () -> Unit,
    onManageUsageAccess: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Lime),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.security_center_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.security_center_subtitle),
                    color = InkSoft,
                )
            }
        }

        item {
            PermissionManageCard(
                title = stringResource(R.string.security_storage_access),
                subtitle = stringResource(R.string.security_storage_access_desc),
                icon = Icons.Outlined.Storage,
                ready = state.hasAllFilesAccess,
                onClick = onManageAllFilesAccess,
            )
        }

        item {
            PermissionManageCard(
                title = stringResource(R.string.security_usage_access),
                subtitle = stringResource(R.string.security_usage_access_desc),
                icon = Icons.Outlined.Android,
                ready = state.hasUsageAccess,
                onClick = onManageUsageAccess,
            )
        }

        item {
            SecurityInfoCard(
                title = stringResource(R.string.security_local_processing),
                subtitle = stringResource(R.string.security_local_processing_desc),
                icon = Icons.Outlined.Security,
            )
        }

        item {
            PrivacyPolicyCard(onClick = onOpenPrivacy)
        }
    }
}

@Composable
private fun PermissionManageCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    ready: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Black),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = BlackChip,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Lime,
                    modifier = Modifier.padding(11.dp).size(28.dp),
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = OnBlack,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = subtitle,
                    color = OnBlackSoft,
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = if (ready) Lime else AdLabel,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (ready) R.string.status_ready
                                else R.string.permissions_review_status,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Ink,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = stringResource(R.string.permissions_manage_android),
                        color = Lime,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Lime,
            )
        }
    }
}

@Composable
private fun SecurityInfoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Black),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = BlackChip,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Lime,
                    modifier = Modifier.padding(11.dp).size(28.dp),
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = OnBlack,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = subtitle,
                    color = OnBlackSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    color = BlackChip,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permissions_on_device_badge),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Lime,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicyCard(
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Black),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = BlackChip,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PrivacyTip,
                        contentDescription = null,
                        tint = Lime,
                        modifier = Modifier.padding(11.dp).size(28.dp),
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permissions_privacy_policy_title),
                    color = OnBlack,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.permissions_privacy_policy_subtitle),
                    color = OnBlackSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Lime,
            )
        }
    }
}
