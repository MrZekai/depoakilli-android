package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Android
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
    onRequestAllFilesAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07183E), Color(0xFF040A1C)),
                ),
            ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.security_center_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.security_center_subtitle),
                    color = Color(0xFFAAB7D3),
                )
            }
        }
        item {
            SecurityStatusCard(
                title = stringResource(R.string.security_storage_access),
                subtitle = stringResource(R.string.security_storage_access_desc),
                icon = Icons.Outlined.Storage,
                ready = state.hasAllFilesAccess,
                onClick = if (state.hasAllFilesAccess) null else onRequestAllFilesAccess,
            )
        }
        item {
            SecurityStatusCard(
                title = stringResource(R.string.security_usage_access),
                subtitle = stringResource(R.string.security_usage_access_desc),
                icon = Icons.Outlined.Android,
                ready = state.hasUsageAccess,
                onClick = if (state.hasUsageAccess) null else onRequestUsageAccess,
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPrivacy),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1935)),
            ) {
                Row(
                    modifier = Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Lime400.copy(alpha = .16f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = Lime400,
                            modifier = Modifier.padding(11.dp).size(28.dp),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.security_local_processing),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.security_local_processing_desc),
                            color = Color(0xFFAAB7D3),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityStatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    ready: Boolean,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1935)),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (ready) Lime400.copy(alpha = .16f) else ElectricBlue.copy(alpha = .16f),
                shape = CircleShape,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (ready) Lime400 else ElectricBlue,
                    modifier = Modifier.padding(11.dp).size(28.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    subtitle,
                    color = Color(0xFFAAB7D3),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(
                color = if (ready) Lime400.copy(alpha = .16f) else Color(0xFFFFB21A).copy(alpha = .16f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (ready) stringResource(R.string.status_ready) else stringResource(R.string.status_needed),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (ready) Lime400 else Color(0xFFFFC24B),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
