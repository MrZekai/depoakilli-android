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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Lime400

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
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF07183E),
                        Color(0xFF040A1C),
                    ),
                ),
            ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.security_center_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.security_center_subtitle),
                    color = Color(0xFFAAB7D3),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1935)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (ready) {
                    Lime400.copy(alpha = .16f)
                } else {
                    ElectricBlue.copy(alpha = .16f)
                },
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (ready) Lime400 else ElectricBlue,
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
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = subtitle,
                    color = Color(0xFFAAB7D3),
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = if (ready) {
                            Lime400.copy(alpha = .16f)
                        } else {
                            Color(0xFFFFB21A).copy(alpha = .16f)
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (ready) R.string.status_ready
                                else R.string.permissions_review_status,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (ready) Lime400 else Color(0xFFFFC24B),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = stringResource(R.string.permissions_manage_android),
                        color = Color(0xFF78B9FF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF78B9FF),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1935)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF24D9A7).copy(alpha = .14f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF54E2B5),
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
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = subtitle,
                    color = Color(0xFFAAB7D3),
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    color = Color(0xFF24D9A7).copy(alpha = .14f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permissions_on_device_badge),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF54E2B5),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1935)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF8B5CF6).copy(alpha = .15f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PrivacyTip,
                        contentDescription = null,
                        tint = Color(0xFFBA9BFF),
                        modifier = Modifier.padding(11.dp).size(28.dp),
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permissions_privacy_policy_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.permissions_privacy_policy_subtitle),
                    color = Color(0xFFAAB7D3),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFBA9BFF),
            )
        }
    }
}
