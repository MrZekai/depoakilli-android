package com.mrzekai.depoakilli.ui.common

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Coral
import com.mrzekai.depoakilli.ui.theme.GiantScale
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/*
 * Building blocks of the "Make Room" design (Faz 5): Lime pages, black cards,
 * one giant figure and one giant button per screen.
 */

/** Card corner 24 dp, bottom-sheet top corners 34 dp, buttons fully round. */
object MakeRoomShapes {
    val Card = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val Pill = CircleShape
}

/** Giant figure and button size for the current window and font scale. */
@Composable
fun rememberGiantScale(): GiantScale {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    return remember(configuration.screenHeightDp, fontScale) {
        GiantScale.of(configuration.screenHeightDp, fontScale)
    }
}

/** Centres the content and caps it at 480 dp on landscape and large screens. */
@Composable
fun PageFrame(
    modifier: Modifier = Modifier,
    background: Color = Lime,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = GiantScale.MAX_CONTENT_WIDTH_DP.dp)
                .fillMaxWidth(),
            content = content,
        )
    }
}

/** Large page title on Lime ("Tools", "Settings"…). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, size: Int = 44) {
    Text(
        text,
        modifier = modifier.semantics { heading() },
        color = Ink,
        fontSize = size.sp,
        lineHeight = (size + 4).sp,
        fontWeight = AppWeights.Giant,
    )
}

/** Small label line on Lime above or below the giant figure. */
@Composable
fun PageLabel(text: String, modifier: Modifier = Modifier, strong: Boolean = false, center: Boolean = true) {
    Text(
        text,
        modifier = modifier,
        color = if (strong) Ink else InkSoft,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = if (strong) AppWeights.Title else AppWeights.Strong,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** The one giant figure of a screen: single line, shrinks to fit (never below 44 sp). */
@Composable
fun GiantNumber(
    text: String,
    scale: GiantScale,
    modifier: Modifier = Modifier,
    color: Color = Ink,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.displayLarge.copy(
            color = color,
            fontWeight = AppWeights.Giant,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = GiantScale.MIN_NUMBER_SP.sp,
            maxFontSize = scale.numberSp.sp,
            stepSize = 2f.sp,
        ),
    )
}

/**
 * The giant round button: black circle with a flat shadow 14 dp below it
 * (Ink at 28 %, no blur). [hint] is the small line under the label.
 */
@Composable
fun GiantButton(
    label: String,
    onClick: () -> Unit,
    diameter: Dp,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Box(modifier = modifier.size(width = diameter, height = diameter + 14.dp)) {
        Box(
            Modifier
                .offset(y = 14.dp)
                .size(diameter)
                .clip(CircleShape)
                .background(Ink.copy(alpha = .28f)),
        )
        Box(
            Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(Black)
                .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Lime, strokeWidth = 3.dp, modifier = Modifier.size(diameter / 4))
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = diameter / 8),
                ) {
                    Text(
                        label,
                        color = if (enabled) Lime else OnBlackSoft,
                        fontSize = (diameter.value / 7.4f).coerceIn(18f, 28f).sp,
                        lineHeight = (diameter.value / 6.4f).coerceIn(22f, 32f).sp,
                        fontWeight = AppWeights.Giant,
                        textAlign = TextAlign.Center,
                    )
                    if (hint != null) {
                        Text(
                            hint,
                            color = OnBlackSoft,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = AppWeights.Strong,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Black card with 24 dp corners; children default to light text. */
@Composable
fun BlackCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Black,
        contentColor = OnBlack,
        shape = MakeRoomShapes.Card,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Lime icon on a dark chip, used at the start of rows and tiles. */
@Composable
fun IconChip(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(BlackChip),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Lime, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * One black row (80 dp): Lime icon, title and one-line detail, Lime size on
 * the right and an arrow. Used by the findings list and Home.
 */
@Composable
fun BigRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
    /** Replaces the icon chip (E1: a real app icon). */
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        color = Black,
        contentColor = OnBlack,
        shape = MakeRoomShapes.Card,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (leading != null) leading() else IconChip(icon)
            Column(Modifier.weight(1f)) {
                Text(title, color = OnBlack, fontWeight = AppWeights.Title, fontSize = 16.sp, lineHeight = 20.sp)
                if (detail != null) {
                    Text(
                        detail,
                        color = OnBlackSoft,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        // C5: room for two short lines instead of a cut first one.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Text(value, color = Lime, fontWeight = AppWeights.Giant, fontSize = 16.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = OnBlackSoft)
        }
    }
}

/** Row inside a black card (settings, trash files…): 52 dp minimum. */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    status: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = onClick != null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Lime, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnBlack, fontWeight = AppWeights.Strong, fontSize = 15.sp, lineHeight = 20.sp)
            if (detail != null) Text(detail, color = OnBlackSoft, fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (status != null) Text(status, color = Lime, fontWeight = AppWeights.Strong, fontSize = 14.sp)
        trailing?.invoke(this)
        if (showArrow) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = OnBlackSoft)
        }
    }
}

/** Divider between rows of a black card. */
@Composable
fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 1.dp, max = 1.dp)
            .background(com.mrzekai.depoakilli.ui.theme.BlackLine),
    )
}

/** Look of a full-width pill button. */
enum class PillStyle { LIME, BLACK, BLACK_LIME, WHITE, CORAL_OUTLINE, INK_OUTLINE, LIME_OUTLINE }

/** Fully rounded 56 dp button in one of the design's styles. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    style: PillStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    // B3: a disabled button is a dark chip with soft text on every page, so it
    // never turns into faded green-on-Lime or faded white-on-Lime.
    val disabledLook = !enabled && !busy
    val (container, content, border) = if (disabledLook) Triple(BlackChip, OnBlackSoft, null) else when (style) {
        PillStyle.LIME -> Triple(Lime, Ink, null)
        PillStyle.BLACK -> Triple(Black, OnBlack, null)
        PillStyle.BLACK_LIME -> Triple(Black, Lime, null)
        PillStyle.WHITE -> Triple(OnBlack, Black, null)
        PillStyle.CORAL_OUTLINE -> Triple(Color.Transparent, Coral, BorderStroke(2.dp, Coral))
        PillStyle.INK_OUTLINE -> Triple(Color.Transparent, Ink, BorderStroke(2.dp, Ink))
        PillStyle.LIME_OUTLINE -> Triple(Color.Transparent, Lime, BorderStroke(2.dp, Lime))
    }
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MakeRoomShapes.Pill,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    color = content,
                    fontWeight = AppWeights.Giant,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Two (or more) tabs on top of a tool that holds two lists (C4): the selected
 * tab is a black pill with Lime text, the others are Ink-outlined.
 */
@Composable
fun HubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Lime)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            Surface(
                onClick = { if (!isSelected) onSelect(index) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = MakeRoomShapes.Pill,
                color = if (isSelected) Black else Lime,
                contentColor = if (isSelected) Lime else Ink,
                border = if (isSelected) null else BorderStroke(2.dp, Ink),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        title,
                        color = if (isSelected) Lime else Ink,
                        fontWeight = AppWeights.Title,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Bottom padding for a scrolling list: the navigation bar plus 24 dp, so the last row is never cut (B2). */
@Composable
fun listBottomPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

/**
 * Icon and text on top at full width, the action below on its own line (B2).
 * Used for permission and action cards whose button text is longer than one
 * word, so the text column is never squeezed letter by letter.
 */
@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PillStyle = PillStyle.LIME,
    iconTint: Color = Lime,
) {
    BlackCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = OnBlack, fontWeight = AppWeights.Title, fontSize = 17.sp, lineHeight = 22.sp)
                Text(body, color = OnBlackSoft, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        PillButton(text = action, onClick = onClick, style = style, enabled = enabled, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Plain text action ("Not now", "Undo"…) with a 48 dp touch target. */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MakeRoomShapes.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) color else color.copy(alpha = .5f),
            fontWeight = AppWeights.Title,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** The "Ad" label every native placement shows. */
@Composable
fun AdTag(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.ad_label),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AdLabel)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Ink,
        fontSize = 12.sp,
        fontWeight = AppWeights.Giant,
    )
}

/** Thin progress bar on black (Lime fill). [progress] is 0..1. */
@Composable
fun LimeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Lime,
    trackColor: Color = BlackChip,
) {
    // One continuous bar (C6), animated between steps; never a segmented indicator.
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 8.dp, max = 8.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Progress of a scan whose total is unknown while files are being indexed:
 * it rises with the files found and slows down, staying under [ceiling].
 */
fun indexingProgress(files: Int, ceiling: Float = 0.5f): Float =
    0.05f + (ceiling - 0.05f) * (files / (files + 20_000f))

/** Outline around a Lime-page section when it must stand out (rarely needed). */
fun Modifier.inkOutline(): Modifier = this.border(2.dp, Ink, MakeRoomShapes.Card)

/**
 * System bar icon colours: dark icons on Lime screens, light icons on black
 * screens. Applied while the calling screen is shown.
 */
@Composable
fun SystemBarIcons(darkStatusIcons: Boolean, darkNavigationIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, darkStatusIcons, darkNavigationIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = darkStatusIcons
            controller.isAppearanceLightNavigationBars = darkNavigationIcons
        }
        onDispose { }
    }
}

/**
 * Sticky black bar at the bottom of review and tool lists (T5.8): the
 * selected count and total on the left, a Lime "Move to Trash" on the right.
 */
@Composable
fun SelectionBar(
    selectedCount: Int,
    selectedBytes: Long,
    enabled: Boolean,
    busy: Boolean,
    onMoveToTrash: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String = stringResource(R.string.tool_move_to_trash),
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Black, contentColor = OnBlack) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    com.mrzekai.depoakilli.model.ByteFormatter.format(selectedBytes),
                    color = if (selectedCount > 0) Lime else OnBlackSoft,
                    fontWeight = AppWeights.Giant,
                    fontSize = 18.sp,
                )
                Text(
                    if (selectedCount > 0) {
                        stringResource(R.string.smart_detail_selected_label) + " · " + selectedCount
                    } else {
                        stringResource(R.string.nothing_selected_hint)
                    },
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                )
            }
            PillButton(
                text = actionText,
                onClick = onMoveToTrash,
                style = PillStyle.LIME,
                enabled = enabled,
                busy = busy,
                modifier = Modifier.weight(1.3f),
            )
        }
    }
}
