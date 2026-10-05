package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.NativeShare
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.sqrt

private val GoalOrange = Color(0xFFD97827)
private val French = Locale.FRENCH

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(model: AppModel, content: Content) {
    val p = model.displayedProgress ?: model.progress
    val context = LocalContext.current
    val backAnchors = LocalBackMotionAnchors.current
    val today = LocalDate.now()
    val earliest = p.activeDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { !it.isAfter(today) }.minOrNull() ?: today
    val weeks = maxOf(4, (ChronoUnit.DAYS.between(earliest, today) / 7).toInt() + 1)
    val months = maxOf(4, ChronoUnit.MONTHS.between(YearMonth.from(earliest), YearMonth.from(today)).toInt() + 1)
    val listState = rememberLazyListState()
    val avatarDrop by animateDpAsState(if(stickyHeaderDetached(listState))16.dp else 0.dp,tween(180),label="profile-avatar-overhang")
    LazyColumn(
        Modifier.fillMaxSize().testTag("profile-list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 56.dp + LocalNavigationContentOverlap.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stickyHeader(key = "profile-header") {
            Row(Modifier.fillMaxWidth().testTag("profile-header").stickyHeaderShadow(listState).background(Cream).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GitHubAvatar(model.sync.accountIdentity, p.name, Modifier.align(Alignment.Bottom).size(72.dp)
                    .offset(y=avatarDrop).border(3.dp,Cream,CircleShape))
                Column(Modifier.weight(1f)) { BigTitle(p.name, "Ta progression au fil des jours.") }
                IconButton({ model.route = "settings" },Modifier.onGloballyPositioned { backAnchors?.settings=it.boundsInWindow() }) { Icon(Icons.Rounded.Settings, "Réglages") }
            }
        }
        item(key = "profile-level") {
            Panel(color = Mist) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pico(Modifier.size(80.dp), mood = if (p.streak > 0) MascotMood.CELEBRATE else MascotMood.HAPPY,
                        pose = if (p.streak > 0) MascotPose.JUMP else MascotPose.WAVE)
                    Column(Modifier.weight(1f)) {
                        Text("Niveau ${1 + p.xp / 250}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${p.xp} XP · 🔥 ${p.streak} jours", color = Teal, fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(progress = { (p.xp % 250) / 250f }, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Color.White)
                Text("${250 - p.xp % 250} XP avant le prochain niveau", fontSize = 12.sp, color = Muted)
                FilledTonalButton(
                    onClick = { NativeShare.progressImage(context, p.snapshot()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Teal)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Partager mon bilan", fontWeight = FontWeight.Bold)
                }
            }
        }
        item(key = "profile-calendar") { ActivityCalendar(p, today, months) }
        item(key = "profile-weekly-chart") { WeeklyProgress(p, today, weeks) }
        item(key = "profile-learning-summary") {
            Panel {
                Text("Bilan d’apprentissage", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LearningStat("${p.completed.size} / ${content.lessons.size}", "Leçons terminées", Modifier.weight(1f))
                    LearningStat("${p.reviews.values.count { it.repetitions >= 3 }}", "Notions consolidées", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LearningStat("${p.totalAnswers}", "Réponses données", Modifier.weight(1f))
                    LearningStat(if (p.totalAnswers == 0) "—" else "${p.totalCorrect * 100 / p.totalAnswers} %", "Bonnes réponses", Modifier.weight(1f))
                }
            }
        }
        item(key = "profile-memory-tip") {
            Panel(color = Color(0xFFFFF2CD)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = Color(0xFF936411), modifier = Modifier.size(18.dp))
                    Text("Astuce", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF936411))
                }
                Text("Mieux retenir", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Après une bonne réponse, la notion revient après 1 jour, puis 6 jours. Les rappels s’espacent ensuite selon ta facilité.", fontSize = 14.sp, color = Ink, lineHeight = 21.sp)
                Text("Une erreur ? Un nouveau rappel arrive après 10 minutes.\n\nAvec les flashcards, indique la difficulté pour adapter le prochain rappel.", fontSize = 14.sp, color = Ink, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun LearningStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Mist.copy(alpha = .6f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
        Text(label, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
    }
}

@Composable
private fun ActivityCalendar(p: Progress, today: LocalDate, months: Int) {
    val history = rememberPagerState(pageCount = { months })
    val scope = rememberCoroutineScope()
    val currentMonth = YearMonth.from(today)
    val goal = p.dailyGoal.coerceAtLeast(1)
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Calendrier d’activité", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("🔥 ${p.streak} jours de série", fontSize = 12.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ scope.launch { history.animateScrollToPage(history.currentPage + 1) } }, enabled = history.currentPage < months - 1) {
                Icon(Icons.Rounded.ChevronLeft, "Voir le mois précédent")
            }
            val monthLabel = currentMonth.minusMonths(history.currentPage.toLong()).format(DateTimeFormatter.ofPattern("MMMM yyyy", French))
            Text(monthLabel.replaceFirstChar { it.titlecase(French) }, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton({ scope.launch { history.animateScrollToPage(history.currentPage - 1) } }, enabled = history.currentPage > 0) {
                Icon(Icons.Rounded.ChevronRight, "Voir le mois suivant")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 11.sp) }
        }
        HorizontalPager(history, Modifier.fillMaxWidth().testTag("activity-calendar"), reverseLayout = true) { offset ->
            val month = currentMonth.minusMonths(offset.toLong())
            val firstOffset = month.atDay(1).dayOfWeek.value - 1
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(6) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val number = row * 7 + column - firstOffset + 1
                            Box(Modifier.weight(1f).height(43.dp), contentAlignment = Alignment.Center) {
                                if (number in 1..month.lengthOfMonth()) {
                                    CalendarDay(month.atDay(number), today, p.dayXp(month.atDay(number)), goal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, today: LocalDate, xp: Int, goal: Int) {
    val future = date.isAfter(today)
    val ratio = if (future) 0f else (xp.toFloat() / goal).coerceIn(0f, 1f)
    val isToday = date == today
    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", French))
    Column(Modifier.clearAndSetSemantics {
        contentDescription = if (future) "$dateLabel, à venir" else "$dateLabel${if (isToday) ", aujourd’hui" else ""}, $xp XP, objectif actuel $goal XP${if (ratio >= 1f) " atteint" else ""}"
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.size(27.dp)) {
            val radius = size.minDimension / 2f - 2.dp.toPx()
            drawCircle(if (future) Color(0xFFF0F2F0) else Mist, radius = radius)
            if (ratio > 0f) drawCircle(Teal, radius = radius * sqrt(ratio))
            if (isToday) drawCircle(GoalOrange, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        Text("${date.dayOfMonth}", fontSize = 10.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (future) Muted.copy(alpha = .45f) else if (isToday) GoalOrange else Muted)
    }
}

@Composable
private fun WeeklyProgress(p: Progress, today: LocalDate, weeks: Int) {
    val history = rememberPagerState(pageCount = { weeks })
    val scope = rememberCoroutineScope()
    val visibleWeekXp=(0L..6L).sumOf { p.dayXp(today.minusDays(history.currentPage*7L+it)) }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (history.currentPage == 0) "Cette semaine" else "Ton historique", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$visibleWeekXp XP",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Teal)
            }
            IconButton({ scope.launch { history.animateScrollToPage(history.currentPage + 1) } }, enabled = history.currentPage < weeks - 1) {
                Icon(Icons.Rounded.History, "Voir la semaine précédente")
            }
            IconButton({ scope.launch { history.animateScrollToPage(history.currentPage - 1) } }, enabled = history.currentPage > 0) {
                Icon(Icons.Rounded.Update, "Voir la semaine suivante")
            }
        }
        HorizontalPager(history, Modifier.fillMaxWidth(), reverseLayout = true) { week ->
            val days = (6L downTo 0L).map { today.minusDays(week * 7L + it) }
            val goal = p.dailyGoal.coerceAtLeast(1)
            val maximum = maxOf(days.maxOf { p.dayXp(it) }, goal) * 1.15f
            val weeklyXp = days.sumOf { p.dayXp(it) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${days.first().format(DateTimeFormatter.ofPattern("d MMM", French))} – ${days.last().format(DateTimeFormatter.ofPattern("d MMM yyyy", French))}", fontSize = 12.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    days.forEach { Text("${p.dayXp(it)}", Modifier.weight(1f), fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center) }
                }
                Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        days.forEach { day ->
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().fillMaxHeight((p.dayXp(day) / maximum).coerceIn(0f, 1f))
                                    .background(if (day == today) Coral else Teal, RoundedCornerShape(5.dp)))
                            }
                        }
                    }
                    Canvas(Modifier.fillMaxSize().testTag("weekly-goal-line").clearAndSetSemantics { contentDescription = "Objectif journalier : $goal XP" }) {
                        val y = size.height * (1f - goal / maximum)
                        drawLine(GoalOrange, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    }
                    if (weeklyXp == 0) Text("Pas de progressions cette semaine.", Modifier.padding(horizontal = 12.dp),
                        fontSize = 14.sp, lineHeight = 20.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    days.forEach { day ->
                        Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, French), Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
