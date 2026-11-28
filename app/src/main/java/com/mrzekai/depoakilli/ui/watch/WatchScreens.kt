package com.mrzekai.depoakilli.ui.watch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.common.AppSheet
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.LimeProgressBar
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageTitle
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.SheetBody
import com.mrzekai.depoakilli.ui.common.SheetTitle
import com.mrzekai.depoakilli.ui.pro.ProBadge
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.CardLine
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.watch.WatchGrowth
import com.mrzekai.depoakilli.watch.WatchKind
import com.mrzekai.depoakilli.watch.WatchNotifier
import com.mrzekai.depoakilli.watch.WatchRules
import com.mrzekai.depoakilli.watch.WatchSnapshot
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the watch screens get from CleanerApp; every action is a user tap. */
internal data class WatchUi(
    val snapshot: WatchSnapshot,
    val isPro: Boolean,
    val notificationsOn: Boolean,
    val hibernationRestricted: Boolean,
    val nowMillis: Long,
)

internal data class WatchActions(
    val turnOn: () -> Unit,
    val turnOff: () -> Unit,
    val setThreshold: (Boolean) -> Unit,
    val setWeekly: (Boolean) -> Unit,
    val setWhatsApp: (Boolean) -> Unit,
    val setNewVideos: (Boolean) -> Unit,
    val openPaywall: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openHibernationSettings: () -> Unit,
    val openAppInfo: () -> Unit,
    val lookAtNew: () -> Unit,
    val openDetails: () -> Unit,
)

@Composable
internal fun percentOf(percent: Int): String =
    NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(percent / 100.0)

/** "today 09:12", "yesterday 18:40" or a short date with time; always the real time. */
@Composable
internal fun lastCheckText(millis: Long): String {
    if (millis <= 0L) return stringResource(R.string.watch_no_check)
    val locale = LocalConfiguration.current.locales[0]
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val clock = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    val today = LocalDate.now()
    return when (time.toLocalDate()) {
        today -> stringResource(R.string.watch_time_today, clock)
        today.minusDays(1) -> stringResource(R.string.watch_time_yesterday, clock)
        else -> time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))
    }
}

/** Z2: what the watch does, before it is turned on. */
@Composable
internal fun WatchIntroOverlay(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    BackHandler(onBack = onNotNow)
    Box(
        Modifier
            .fillMaxSize()
            .background(Lime)
            .clickable(enabled = false, onClick = {})
            .testTag("watch-intro"),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.app_name),
                color = Ink,
                fontSize = 14.sp,
                fontWeight = AppWeights.Title,
                modifier = Modifier.statusBarsPadding().padding(start = 22.dp, top = 22.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Black, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(CardLine, RoundedCornerShape(2.dp)),
                )
                Text(
                    stringResource(R.string.watch_intro_label),
                    color = Lime,
                    fontSize = 13.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    stringResource(R.string.watch_intro_title),
                    color = OnBlack,
                    fontSize = 32.sp,
                    lineHeight = 37.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntroPoint(stringResource(R.string.watch_intro_point_look))
                    IntroPoint(stringResource(R.string.watch_intro_point_measure))
                    IntroPoint(stringResource(R.string.watch_intro_point_never))
                }
                PillButton(
                    text = stringResource(R.string.watch_turn_on),
                    onClick = onTurnOn,
                    style = PillStyle.LIME,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp).testTag("watch-turn-on"),
                )
                LinkButton(
                    stringResource(R.string.action_not_now),
                    onClick = onNotNow,
                    color = OnBlackSoft,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.watch_intro_note),
                    color = OnBlackSoft,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = Lime, modifier = Modifier.size(20.dp))
        Text(text, color = OnBlack, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = AppWeights.Strong, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Shown once after the watch is turned on, when Android may put the app to sleep. */
@Composable
internal fun WatchHibernationSheet(onOpenSetting: () -> Unit, onDismiss: () -> Unit) {
    AppSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.watch_hibernation_title))
        SheetBody(stringResource(R.string.watch_hibernation_body))
        PillButton(text = stringResource(R.string.watch_open_setting), onClick = onOpenSetting, style = PillStyle.LIME)
        LinkButton(stringResource(R.string.action_not_now), onClick = onDismiss, color = OnBlack, modifier = Modifier.fillMaxWidth())
    }
}

/** Z3: status, last real check, this week's change, the switches and the promise. */
@Composable
internal fun WatchScreen(ui: WatchUi, actions: WatchActions, modifier: Modifier = Modifier) {
    val s = ui.snapshot
    val latest = s.latest
    val stale = s.settings.enabled && WatchRules.isStale(s.lastLookMillis, ui.nowMillis)
    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("watch-screen"),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "watch-title") {
                Column {
                    PageTitle(stringResource(if (s.settings.enabled) R.string.watch_title_on else R.string.watch_title_off), size = 40)
                    if (s.settings.enabled) {
                        if (stale) {
                            val days = WatchRules.daysSince(s.lastLookMillis, ui.nowMillis).toInt()
                            Text(pluralStringResource(R.plurals.watch_stale, days, days), color = Ink, fontSize = 16.sp, fontWeight = AppWeights.Title)
                            LinkButton(stringResource(R.string.watch_stale_fix), onClick = actions.openAppInfo)
                        } else {
                            val percent = latest?.usedPercent
                            Text(
                                if (percent != null) {
                                    stringResource(R.string.watch_last_check, lastCheckText(s.lastLookMillis), percentOf(percent))
                                } else {
                                    lastCheckText(s.lastLookMillis)
                                },
                                color = Ink,
                                fontSize = 16.sp,
                                fontWeight = AppWeights.Title,
                            )
                        }
                    }
                }
            }
            if (s.settings.enabled) {
                item(key = "watch-week") { WeekCard(s.growth, ui.isPro, actions.openPaywall, label = stringResource(R.string.watch_week_label)) }
            }
            if (s.settings.enabled && !ui.notificationsOn) {
                item(key = "watch-notifications") {
                    BlackCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        com.mrzekai.depoakilli.ui.common.CardRow(
                            title = stringResource(R.string.watch_notifications_off),
                            detail = stringResource(R.string.watch_notifications_off_sub),
                            onClick = actions.openNotificationSettings,
                        )
                    }
                }
            }
            if (s.settings.enabled && ui.hibernationRestricted) {
                item(key = "watch-hibernation") {
                    BlackCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        com.mrzekai.depoakilli.ui.common.CardRow(
                            title = stringResource(R.string.watch_hibernation_row),
                            detail = stringResource(R.string.watch_hibernation_body),
                            onClick = actions.openHibernationSettings,
                        )
                    }
                }
            }
            if (s.settings.enabled) {
                item(key = "watch-switches") {
                    BlackCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        SwitchRow(
                            title = stringResource(R.string.watch_toggle_threshold),
                            detail = stringResource(
                                R.string.watch_toggle_threshold_sub,
                                percentOf(WatchRules.THRESHOLD_LOW),
                                percentOf(WatchRules.THRESHOLD_HIGH),
                            ),
                            checked = s.settings.thresholdAlerts,
                            pro = false,
                            onChange = actions.setThreshold,
                        )
                        CardDivider()
                        SwitchRow(
                            title = stringResource(R.string.watch_toggle_weekly),
                            detail = stringResource(R.string.watch_toggle_weekly_sub),
                            checked = ui.isPro && s.settings.weeklySummary,
                            pro = true,
                            onChange = if (ui.isPro) actions.setWeekly else { _ -> actions.openPaywall() },
                        )
                        CardDivider()
                        SwitchRow(
                            title = stringResource(R.string.watch_toggle_whatsapp),
                            detail = stringResource(R.string.watch_toggle_whatsapp_sub, ByteFormatter.format(CleaningThresholds.WATCH_WHATSAPP_GROWTH_BYTES)),
                            checked = ui.isPro && s.settings.whatsAppAlerts,
                            pro = true,
                            onChange = if (ui.isPro) actions.setWhatsApp else { _ -> actions.openPaywall() },
                        )
                        CardDivider()
                        // Faz 3A-2: on with video shrinking (WATCH_NEW_VIDEO_ENABLED).
                        if (CleaningThresholds.WATCH_NEW_VIDEO_ENABLED) {
                            SwitchRow(
                                title = stringResource(R.string.watch_toggle_videos),
                                detail = stringResource(R.string.watch_toggle_videos_sub),
                                checked = ui.isPro && s.settings.newVideoAlerts,
                                pro = true,
                                onChange = if (ui.isPro) actions.setNewVideos else { _ -> actions.openPaywall() },
                            )
                        }
                    }
                }
            }
            item(key = "watch-footer") {
                Column {
                    Text(
                        stringResource(R.string.watch_footer),
                        color = InkSoft,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    PillButton(
                        text = stringResource(if (s.settings.enabled) R.string.watch_turn_off else R.string.watch_turn_on),
                        onClick = if (s.settings.enabled) actions.turnOff else actions.turnOn,
                        style = if (s.settings.enabled) PillStyle.INK_OUTLINE else PillStyle.BLACK_LIME,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("watch-main-switch"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    pro: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = OnBlack, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = AppWeights.Title, modifier = Modifier.weight(1f, fill = false))
                if (pro) ProBadge(Modifier.padding(start = 8.dp))
            }
            Text(detail, color = OnBlackSoft, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Black,
                checkedTrackColor = Lime,
                uncheckedThumbColor = OnBlackSoft,
                uncheckedTrackColor = CardLine,
                uncheckedBorderColor = CardLine,
            ),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * "This week X added" with the kinds (Pro), "Space freed this week" when it
 * went down, or "I'll show this once the first week is in" without a base.
 */
@Composable
internal fun WeekCard(
    growth: WatchGrowth?,
    isPro: Boolean,
    onOpenPaywall: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    BlackCard(modifier = modifier.testTag("watch-week-card"), contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp)) {
        Text(label, color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
        when {
            growth == null -> Text(
                stringResource(R.string.watch_week_pending),
                color = OnBlack,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = AppWeights.Title,
                modifier = Modifier.padding(top = 6.dp),
            )
            growth.decreased -> Text(
                stringResource(R.string.watch_week_freed, ByteFormatter.format(-growth.totalBytes)),
                color = OnBlack,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = AppWeights.Giant,
                modifier = Modifier.padding(top = 6.dp),
            )
            else -> {
                Text(
                    stringResource(R.string.watch_week_added, ByteFormatter.format(growth.totalBytes)),
                    color = OnBlack,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    pluralStringResource(R.plurals.watch_week_days, growth.days, growth.days),
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                )
                if (isPro) {
                    KindBars(growth, Modifier.padding(top = 12.dp))
                } else {
                    Row(
                        Modifier
                            .padding(top = 10.dp)
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.Button, onClick = onOpenPaywall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProBadge()
                        Text(
                            stringResource(R.string.watch_breakdown_pro),
                            color = OnBlack,
                            fontSize = 15.sp,
                            fontWeight = AppWeights.Strong,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }
        footer?.invoke()
    }
}

/** Disjoint kinds; the rows (Other included) add up to the headline. */
@Composable
private fun KindBars(growth: WatchGrowth, modifier: Modifier = Modifier) {
    val rows = WatchKind.entries.map { it to growth.bytes(it) }
    val max = rows.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (kind, bytes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(WatchNotifier.kindName(kind)),
                    color = OnBlack,
                    fontSize = 14.sp,
                    fontWeight = AppWeights.Strong,
                    maxLines = 1,
                    modifier = Modifier.width(104.dp),
                )
                LimeProgressBar(
                    progress = (bytes.coerceAtLeast(0L).toFloat() / max).coerceIn(0f, 1f),
                    color = Lime,
                    trackColor = CardLine,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text(
                    (if (bytes >= 0L) "+" else "−") + ByteFormatter.format(kotlin.math.abs(bytes)),
                    color = if (bytes >= 0L) Lime else OnBlackSoft,
                    fontSize = 14.sp,
                    fontWeight = AppWeights.Title,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Z1: one line under Home's giant button. */
@Composable
internal fun WatchHomeLine(ui: WatchUi, onOpen: () -> Unit) {
    val s = ui.snapshot
    val stale = WatchRules.isStale(s.lastLookMillis, ui.nowMillis)
    val text = if (stale) {
        val days = WatchRules.daysSince(s.lastLookMillis, ui.nowMillis).toInt()
        pluralStringResource(R.plurals.watch_stale, days, days)
    } else {
        stringResource(R.string.watch_home_line, lastCheckText(s.lastLookMillis))
    }
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 8.dp)
            .testTag("watch-home-line"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(Ink, CircleShape))
        Text(text, color = Ink, fontSize = 14.sp, fontWeight = AppWeights.Strong, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Z1: this week's card on Home (only with a real week of data; never with the Y1 Pro card). */
@Composable
internal fun WatchHomeCard(ui: WatchUi, actions: WatchActions, modifier: Modifier = Modifier) {
    WeekCard(
        growth = ui.snapshot.growth,
        isPro = ui.isPro,
        onOpenPaywall = actions.openPaywall,
        label = stringResource(R.string.watch_home_label),
        modifier = modifier,
    ) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = stringResource(R.string.watch_home_see_new),
                onClick = actions.lookAtNew,
                style = PillStyle.LIME,
                modifier = Modifier.weight(1f),
            )
            LinkButton(stringResource(R.string.watch_home_details), onClick = actions.openDetails, color = OnBlackSoft)
        }
    }
}

/** After the first successful cleanup, once: "Want a heads-up before it fills?" */
@Composable
internal fun WatchPromoCard(onOpen: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    BlackCard(modifier = modifier.testTag("watch-promo"), contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 8.dp)) {
        Text(stringResource(R.string.watch_promo_title), color = OnBlack, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = AppWeights.Title)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(text = stringResource(R.string.watch_promo_open), onClick = onOpen, style = PillStyle.LIME, modifier = Modifier.weight(1f))
            LinkButton(stringResource(R.string.action_not_now), onClick = onNotNow, color = OnBlackSoft)
        }
    }
}

/**
 * Faz 3A-1: what MainActivity hands CleanerApp for the watch. Every action
 * here runs only from a user tap; [target] is the screen a notice asked for.
 */
data class WatchHost(
    val snapshot: WatchSnapshot = WatchSnapshot(),
    val notificationsOn: Boolean = true,
    val hibernationRestricted: Boolean = false,
    val target: String? = null,
    val enable: () -> Unit = {},
    val disable: () -> Unit = {},
    val updateSettings: ((com.mrzekai.depoakilli.watch.WatchSettings) -> com.mrzekai.depoakilli.watch.WatchSettings) -> Unit = {},
    val markNotificationAsked: () -> Unit = {},
    val markHibernationExplained: () -> Unit = {},
    val markPromoDone: () -> Unit = {},
    val consumeTarget: () -> Unit = {},
    val openSystem: (android.content.Intent) -> Unit = {},
)
