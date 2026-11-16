package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanupPlan
import com.mrzekai.depoakilli.model.PlanGroup

internal val PlanCard = Color(0xFF0A1C42)
internal val PlanCardAlt = Color(0xFF0D2450)
internal val PlanTextSecondary = Color(0xFFB4C4DF)
internal val PlanAccent = Color(0xFF28E58A)
internal val PlanCyan = Color(0xFF16C7FF)
internal val PlanButtonText = Color(0xFF03122B)

/** Stable key of a plan row; the merged small row has no group. */
internal fun PlanGroup.rowKey(): String = group?.name ?: "SMALL_ITEMS"

@Composable
internal fun PlanGroup.title(): String =
    group?.let { stringResource(it.titleRes) } ?: stringResource(R.string.plan_small_items)

/**
 * Rows of the cleanup plan (T1.7): recommendation card, included rows,
 * system steps, worth-a-look rows, truncation note and "Scan again".
 */
internal fun LazyListScope.cleanupPlanItems(
    plan: CleanupPlan,
    appCacheBytes: Long?,
    unusedAppCount: Int?,
    cleanupInProgress: Boolean,
    onCleanRecommended: () -> Unit,
    onToggleRow: (PlanGroup) -> Unit,
    onOpenRow: (PlanGroup) -> Unit,
    onClearAppCaches: () -> Unit,
    onOpenUnusedApps: () -> Unit,
    onScanAgain: () -> Unit,
    afterReviewRow: LazyListScope.(index: Int) -> Unit = {},
) {
    item(key = "plan-card") {
        RecommendedCleanupCard(
            plan = plan,
            cleanupInProgress = cleanupInProgress,
            onClean = onCleanRecommended,
            onOpenRow = onOpenRow,
        )
    }
    val includedRows = plan.included + listOfNotNull(plan.smallItems)
    if (includedRows.isNotEmpty()) {
        item(key = "plan-included-title") { PlanSectionTitle(stringResource(R.string.plan_section_included)) }
        items(includedRows, key = { "included-" + it.rowKey() }) { row ->
            PlanRow(row = row, onToggle = { onToggleRow(row) }, onOpen = { onOpenRow(row) })
        }
    }
    item(key = "plan-system-title") { PlanSectionTitle(stringResource(R.string.plan_section_system)) }
    item(key = "plan-system-cache") {
        SystemStepRow(
            title = stringResource(R.string.plan_clear_app_caches),
            detail = appCacheBytes?.takeIf { it > 0L }?.let {
                stringResource(R.string.plan_cache_about, ByteFormatter.format(it))
            },
            onClick = onClearAppCaches,
        )
    }
    if (unusedAppCount != null) {
        item(key = "plan-system-unused") {
            SystemStepRow(
                title = pluralStringResource(R.plurals.plan_unused_apps, unusedAppCount, unusedAppCount),
                detail = null,
                onClick = onOpenUnusedApps,
            )
        }
    }
    if (plan.review.isNotEmpty()) {
        item(key = "plan-review-title") { PlanSectionTitle(stringResource(R.string.plan_section_review)) }
        plan.review.forEachIndexed { index, row ->
            item(key = "review-" + row.rowKey()) {
                PlanRow(row = row, onToggle = { onToggleRow(row) }, onOpen = { onOpenRow(row) })
            }
            afterReviewRow(this, index)
        }
    }
    if (plan.truncated) {
        item(key = "plan-truncated") {
            val limit = com.mrzekai.depoakilli.data.CleaningThresholds.MAX_RESULT_ITEMS
            Text(
                pluralStringResource(R.plurals.plan_truncated, limit, limit),
                color = Color(0xFFFFC46B),
                fontSize = 13.sp,
            )
        }
    }
    item(key = "plan-scan-again") {
        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.scan_again))
        }
    }
}

@Composable
internal fun RecommendedCleanupCard(
    plan: CleanupPlan,
    cleanupInProgress: Boolean,
    onClean: () -> Unit,
    onOpenRow: (PlanGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PlanCard,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.plan_recommended_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            if (plan.isAlreadyTidy) {
                Text(
                    stringResource(R.string.plan_nothing_big),
                    color = PlanTextSecondary,
                    fontSize = 15.sp,
                )
                val biggest = plan.review.take(3)
                if (biggest.isNotEmpty()) {
                    Text(
                        stringResource(R.string.plan_biggest_space_in),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    biggest.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onOpenRow(row) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                row.title(),
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(ByteFormatter.format(row.bytes), color = PlanTextSecondary)
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = PlanTextSecondary,
                            )
                        }
                    }
                }
            } else {
                val groups = plan.selectedGroupCount
                Text(
                    pluralStringResource(
                        R.plurals.plan_recommended_summary,
                        groups,
                        groups,
                        ByteFormatter.format(plan.selectedBytes),
                    ),
                    color = PlanTextSecondary,
                    fontSize = 15.sp,
                )
                Button(
                    onClick = onClean,
                    enabled = plan.selectedBytes > 0L && !cleanupInProgress,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlanAccent,
                        contentColor = PlanButtonText,
                    ),
                ) {
                    if (cleanupInProgress) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = PlanButtonText)
                    } else {
                        Text(
                            stringResource(R.string.plan_clean_action, ByteFormatter.format(plan.selectedBytes)),
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlanSectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = PlanTextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
internal fun PlanRow(row: PlanGroup, onToggle: () -> Unit, onOpen: () -> Unit) {
    val count = row.items.size
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PlanCardAlt,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.groupSelectable) {
                Checkbox(
                    checked = row.allSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = PlanAccent, checkmarkColor = PlanButtonText),
                )
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    row.title(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.whatsapp_category_summary, count, count, ByteFormatter.format(row.bytes)),
                    color = PlanTextSecondary,
                    fontSize = 13.sp,
                )
            }
            IconButtonArrow(onOpen)
        }
    }
}

@Composable
private fun IconButtonArrow(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PlanTextSecondary,
        )
    }
}

@Composable
internal fun SystemStepRow(title: String, detail: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PlanCard,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                if (detail != null) Text(detail, color = PlanTextSecondary, fontSize = 13.sp)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = PlanTextSecondary,
            )
        }
    }
}

/** Bottom bar of the plan: "Selected: X" and "Review & clean". */
@Composable
internal fun PlanBottomBar(
    selectedBytes: Long,
    enabled: Boolean,
    inProgress: Boolean,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF06112A))
            .border(width = 1.dp, color = Color.White.copy(alpha = .06f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.plan_selected, ByteFormatter.format(selectedBytes)),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onReview,
            enabled = enabled && !inProgress,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PlanAccent, contentColor = PlanButtonText),
        ) {
            if (inProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = PlanButtonText)
            } else {
                Text(stringResource(R.string.smart_review_and_clean), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
internal fun PlanListContainer(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun PlanTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(text) }
}
