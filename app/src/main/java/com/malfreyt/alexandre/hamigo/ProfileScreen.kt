package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
private val GoalCompleted = Color(0xFFFFD166)
private val GoalMetal = listOf(Color(0xFFF2C354), GoalCompleted, Color(0xFFFFE4A0), GoalCompleted)
private val French = Locale.FRENCH

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(model: AppModel, content: Content) {
    val p = model.displayedProgress ?: model.progress
    val context = LocalContext.current
    val backAnchors = LocalBackMotionAnchors.current
    val today = StudyDay.date()
    val earliest = p.activeDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { !it.isAfter(today) }.minOrNull() ?: today
    val weeks = maxOf(4, ChronoUnit.WEEKS.between(CalendarWeek.days(earliest).first(),CalendarWeek.days(today).first()).toInt() + 1)
    val months = maxOf(4, ChronoUnit.MONTHS.between(YearMonth.from(earliest), YearMonth.from(today)).toInt() + 1)
    val listState = rememberLazyListState()
    var headerSurfaceHeight by remember { mutableIntStateOf(0) }
    var avatarBounds by remember { mutableStateOf<Rect?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("profile-list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 56.dp + LocalNavigationContentOverlap.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stickyHeader(key = "profile-header") {
            Box(Modifier.fillMaxWidth().testTag("profile-header")
                .profileHeaderShadow(listState,headerSurfaceHeight.toFloat(),avatarBounds)) {
                Column {
                    Row(Modifier.fillMaxWidth().testTag("profile-header-surface")
                        .onSizeChanged { headerSurfaceHeight=it.height }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.width(72.dp))
                        Column(Modifier.weight(1f)) { BigTitle(p.name, "Ta progression au fil des jours.") }
                        IconButton(feedbackClick { model.route = "settings" },Modifier.onGloballyPositioned { backAnchors?.settings=it.boundsInWindow() }) { Icon(Icons.Rounded.Settings, "Réglages") }
                    }
                    // The header's surface ends above the avatar, which stays in the same layout position.
                    Spacer(Modifier.height(8.dp))
                }
                GitHubAvatar(model.sync.accountIdentity, p.name, Modifier.align(Alignment.BottomStart).size(72.dp)
                    .onGloballyPositioned { avatarBounds=it.boundsInParent() }
                    .border(3.dp,Cream,CircleShape))
            }
        }
        item(key = "profile-level") {
            Panel(color = Mist) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val levelPicoSize=if(maxWidth<330.dp)60.dp else 80.dp
                val metricFont=if(maxWidth<300.dp)16.sp else 18.sp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pico(Modifier.size(levelPicoSize), mood = if (p.streak > 0) MascotMood.CELEBRATE else MascotMood.HAPPY,
                        pose = if (p.streak > 0) MascotPose.JUMP else MascotPose.WAVE)
                    Text("Niveau ${1 + p.xp / 250}",Modifier.weight(1f).padding(horizontal=8.dp),fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
                    Column(Modifier.testTag("profile-level-stats"),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        StatMetric(dayCount(p.streak),Icons.Rounded.LocalFireDepartment,Coral,fontSize=metricFont,iconSize=23.dp)
                        StatMetric("${p.xp} XP",Icons.Rounded.Bolt,fontSize=metricFont,iconSize=23.dp)
                    }
                }
                }
                LinearProgressIndicator(progress = { (p.xp % 250) / 250f }, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Color.White)
                Text("${250 - p.xp % 250} XP avant le prochain niveau", fontSize = 12.sp, color = Muted)
                FilledTonalButton(
                    onClick = { NativeShare.progressImage(context, p.snapshot(),model.sync.accountIdentity) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Teal)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Partager mon bilan", fontWeight = FontWeight.Bold)
                }
            }
        }
        item(key = "profile-goal") { DailyGoalSummary(p.todayXp,p.dailyGoal,onEdit={model.settingsGoalFocusRequest=System.nanoTime();model.route="settings"}) }
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
                TextButton(feedbackClick {model.practiceFocusRequest=System.nanoTime();model.route="practice"},contentPadding=PaddingValues(0.dp),modifier=Modifier.testTag("open-reviews-from-profile")) {
                    Text("Retrouver mes révisions");Spacer(Modifier.width(5.dp));Icon(Icons.Rounded.ChevronRight,null,Modifier.size(18.dp))
                }
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
    ActivityCalendar(today,months,p.dailyGoal,{p.dayXp(it)})
}

@Composable
internal fun ActivityCalendar(today:LocalDate,months:Int,goal:Int?,xp:(LocalDate)->Int?,tag:String="activity-calendar") {
    val history = rememberPagerState(pageCount = { months })
    val scope = rememberCoroutineScope()
    val currentMonth = YearMonth.from(today)
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Calendrier d’activité", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(feedbackClick { scope.launch { history.animateScrollToPage(0) } }, enabled=history.currentPage!=0) {
                Icon(Icons.Rounded.CalendarToday,"Revenir au mois en cours")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(feedbackClick { scope.launch { history.animateScrollToPage(history.currentPage + 1) } }, enabled = history.currentPage < months - 1) {
                Icon(Icons.Rounded.ChevronLeft, "Voir le mois précédent")
            }
            val monthLabel = currentMonth.minusMonths(history.currentPage.toLong()).format(DateTimeFormatter.ofPattern("MMMM yyyy", French))
            Text(monthLabel.replaceFirstChar { it.titlecase(French) }, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(feedbackClick { scope.launch { history.animateScrollToPage(history.currentPage - 1) } }, enabled = history.currentPage > 0) {
                Icon(Icons.Rounded.ChevronRight, "Voir le mois suivant")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            CalendarWeek.days(today).forEach {Text(it.dayOfWeek.getDisplayName(TextStyle.NARROW,French),Modifier.weight(1f),textAlign=TextAlign.Center,color=Muted,fontSize=11.sp)}
        }
        HorizontalPager(history, Modifier.fillMaxWidth().testTag(tag), reverseLayout = true) { offset ->
            val month = currentMonth.minusMonths(offset.toLong())
            val firstOffset = ChronoUnit.DAYS.between(CalendarWeek.days(month.atDay(1)).first(),month.atDay(1)).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(6) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val number = row * 7 + column - firstOffset + 1
                            Box(Modifier.weight(1f).heightIn(min=43.dp), contentAlignment = Alignment.Center) {
                                if (number in 1..month.lengthOfMonth()) {
                                    CalendarDay(month.atDay(number), today, xp(month.atDay(number)), goal)
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
private fun CalendarDay(date: LocalDate, today: LocalDate, xp: Int?, goal: Int?) {
    val future = date.isAfter(today)
    val ratio = if (future||xp==null) 0f else if(goal==null)if(xp>0)1f else 0f else (xp.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val isToday = date == today
    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", French))
    Column(Modifier.clearAndSetSemantics {
        contentDescription = if (future) "$dateLabel, à venir" else if(xp==null)"$dateLabel, données non partagées" else "$dateLabel${if (isToday) ", aujourd’hui" else ""}, $xp XP${goal?.let {", objectif actuel $it XP${if(ratio>=1f)" atteint" else ""}"} ?: ", objectif non partagé"}"
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.size(27.dp)) {
            val radius = size.minDimension / 2f - 2.dp.toPx()
            drawCircle(if (future) Color(0xFFF0F2F0) else Mist, radius = radius)
            if (ratio >= 1f&&goal!=null) drawCircle(Brush.linearGradient(GoalMetal), radius = radius)
            else if (ratio > 0f) drawCircle(Teal, radius = radius * sqrt(ratio))
            if(xp==null&&!future)drawLine(Muted.copy(alpha=.5f),Offset(center.x-3.dp.toPx(),center.y),Offset(center.x+3.dp.toPx(),center.y),strokeWidth=1.dp.toPx())
            if (ratio >= 1f&&goal!=null) {
                val c = center
                drawLine(Ink, Offset(c.x-radius*.4f,c.y), Offset(c.x-radius*.1f,c.y+radius*.28f), strokeWidth=1.6.dp.toPx())
                drawLine(Ink, Offset(c.x-radius*.1f,c.y+radius*.28f), Offset(c.x+radius*.43f,c.y-radius*.32f), strokeWidth=1.6.dp.toPx())
            }
            if (isToday) drawCircle(GoalOrange, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        Text("${date.dayOfMonth}", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (future) Muted.copy(alpha = .45f) else if (isToday) GoalOrange else Muted)
    }
}

@Composable
private fun WeeklyProgress(p: Progress, today: LocalDate, weeks: Int) {
    val history = rememberPagerState(pageCount = { weeks })
    val scope = rememberCoroutineScope()
    val visibleWeekXp=CalendarWeek.days(today.minusWeeks(history.currentPage.toLong())).sumOf {p.dayXp(it)}
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (history.currentPage == 0) "Cette semaine" else "Ton historique", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$visibleWeekXp XP",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Teal)
            }
            IconButton(feedbackClick { scope.launch { history.animateScrollToPage(history.currentPage + 1) } }, enabled = history.currentPage < weeks - 1) {
                Icon(Icons.Rounded.History, "Voir la semaine précédente")
            }
            IconButton(feedbackClick { scope.launch { history.animateScrollToPage(history.currentPage - 1) } }, enabled = history.currentPage > 0) {
                Icon(Icons.Rounded.Update, "Voir la semaine suivante")
            }
        }
        HorizontalPager(history, Modifier.fillMaxWidth(), reverseLayout = true) { week ->
            val days=CalendarWeek.days(today.minusWeeks(week.toLong()))
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(days.first().format(DateTimeFormatter.ofPattern("d MMM",French))+" – "+days.last().format(DateTimeFormatter.ofPattern("d MMM yyyy",French)),fontSize=12.sp,color=Muted)
                PublicXpChart(days.map {if(it>today)0 else p.dayXp(it)},days.map {it.dayOfWeek.getDisplayName(TextStyle.NARROW,French)},
                    p.dailyGoal,"Pas de progressions cette semaine.",todayIndex=days.indexOf(today).takeIf {it>=0})
            }
        }
    }
}
