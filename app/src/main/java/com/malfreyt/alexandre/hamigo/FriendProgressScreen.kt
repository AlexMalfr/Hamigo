package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.malfreyt.alexandre.hamigo.platform.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Opens the cached public summary, with an explicit refresh that never reads a friend's backup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FriendProgressScreen(model:AppModel,friend:Friend,dismiss:()->Unit) {
    val profile=friend.progress;val date=SocialActivity.date(profile);val context=LocalContext.current
    val ownProgress=model.displayedProgress ?: model.progress
    val own=remember(model.revision,ownProgress){ownProgress.snapshot()}
    val earliest=SocialActivity.earliest(profile)?.coerceAtMost(date)
    val weeks=maxOf(1,earliest?.let {ChronoUnit.WEEKS.between(CalendarWeek.days(it).first(),CalendarWeek.days(date).first()).toInt()+1} ?: 1)
    val months=maxOf(1,earliest?.let {ChronoUnit.MONTHS.between(YearMonth.from(it),YearMonth.from(date)).toInt()+1} ?: 1)
    val weekHistory=rememberPagerState(pageCount={weeks})
    val listState=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val actions=remember(friend.gist){FriendActionState()}
    val gistId=remember(friend.gist){runCatching {GitHubSync.gistId(friend.gist)}.getOrNull()}
    val refreshing=gistId!=null&&model.refreshingFriend==gistId
    val pullState=rememberPullToRefreshState()
    val readStatus=remember(friend.gist,model.revision,refreshing){if(gistId==null)FriendReadStatus() else model.sync.friendReadStatus(friend.gist)}
    Dialog(dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(color=Cream,modifier=Modifier.fillMaxSize().testTag("friend-progress-screen")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().testTag("friend-progress-header").zIndex(1f).stickyHeaderShadow(listState).padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    IconButton(feedbackClick(dismiss)){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Revenir à l’équipe")}
                    GitHubAvatar(friend.githubIdentity,profile.name,Modifier.size(52.dp))
                    Column(Modifier.weight(1f)){Text(profile.name,fontWeight=FontWeight.ExtraBold,fontSize=22.sp);Text("${profile.xp} XP · ${dayCount(profile.streak)} de série",fontSize=12.sp,color=Muted)}
                }
                PullToRefreshBox(isRefreshing=refreshing,onRefresh={if(gistId!=null)model.refreshFriend(friend.gist)},
                    state=pullState,modifier=Modifier.fillMaxSize().testTag("friend-profile-refresh"),indicator={
                        PullToRefreshDefaults.Indicator(state=pullState,isRefreshing=refreshing,
                            modifier=Modifier.align(Alignment.TopCenter),containerColor=Mist,color=Teal)
                    }) {
                LazyColumn(Modifier.fillMaxSize().testTag("friend-progress-list"),state=listState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    model.friendRefreshError?.takeIf {it.first==gistId}?.let { error -> item {Text(error.second,color=Muted,fontSize=12.sp)} }
                    item {
                        val today=SocialActivity.todayXp(profile,date)
                        if(today!=null&&profile.dailyGoal!=null)DailyGoalSummary(today,profile.dailyGoal)
                        else Panel {Text("Aujourd’hui",fontWeight=FontWeight.Bold,fontSize=19.sp);Text(today?.let {"$it XP"} ?: "Données du jour non disponibles",color=Teal);if(profile.dailyGoal==null)Text("L’objectif n’est pas encore partagé par cette version de Hamigo.",color=Muted,fontSize=12.sp)}
                    }
                    item {
                        OutlinedButton(feedbackClick {NativeShare.nudge(context,profile.name,model.progress.name,model.sync.accountIdentity,friend.githubIdentity,profile)},Modifier.fillMaxWidth().testTag("encourage-friend")) {
                            Icon(Icons.Rounded.WavingHand,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Encourager")
                        }
                    }
                    if(earliest!=null)item {ActivityCalendar(date,months,profile.dailyGoal,{SocialActivity.dayXp(profile,it)},tag="friend-calendar")}
                    item {Panel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if(weekHistory.currentPage==0)"Cette semaine" else "Historique hebdomadaire",fontSize=20.sp,fontWeight=FontWeight.Bold)
                                val visible=SocialActivity.week(profile,date.minusDays(weekHistory.currentPage*7L))
                                Text(if(visible.any {it.second!=null})"${visible.sumOf {it.second?.toLong() ?: 0L}} XP" else "— XP",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
                            }
                            IconButton(feedbackClick {scope.launch {weekHistory.animateScrollToPage(weekHistory.currentPage+1)}},enabled=weekHistory.currentPage<weeks-1){Icon(Icons.Rounded.History,"Voir la semaine précédente")}
                            IconButton(feedbackClick {scope.launch {weekHistory.animateScrollToPage(weekHistory.currentPage-1)}},enabled=weekHistory.currentPage>0){Icon(Icons.Rounded.Update,"Voir la semaine suivante")}
                        }
                        HorizontalPager(weekHistory,Modifier.fillMaxWidth().testTag("friend-week-history"),reverseLayout=true) {offset->
                            val series=SocialActivity.week(profile,date.minusDays(offset*7L))
                            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                val format=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.FRENCH)
                                Text("${series.first().first.format(format)} – ${series.last().first.format(format)}",fontSize=12.sp,color=Muted)
                                PublicXpChart(series.map {it.second},series.map {it.first.dayOfWeek.getDisplayName(TextStyle.NARROW,Locale.FRENCH)},profile.dailyGoal,"Pas de progression cette semaine.",
                                    todayIndex=series.indexOfFirst {it.first==date}.takeIf {it>=0},
                                    comparison=series.map {if(it.first>LocalDate.now())0 else ownProgress.dayXp(it.first)})
                                if(series.any {it.second==null})Text("— : journée sans données partagées",fontSize=11.sp,color=Muted)
                            }
                        }
                    }}
                    item {Panel {
                        Text("Aujourd’hui · heure par heure",fontSize=19.sp,fontWeight=FontWeight.Bold)
                        val current=profile.hourlyDay==date.toString()
                        if(current&&(profile.hourlyComplete||profile.hourlyXp.isNotEmpty())) {
                            val hours=profile.hourlyXp.associate {it.hour to it.xp}
                            val comparable=own.hourlyDay==profile.hourlyDay&&own.zoneId==profile.zoneId
                            val ownHours=own.hourlyXp.associate {it.hour to it.xp}
                            PublicXpChart((0..23).map {hours[it] ?: if(profile.hourlyComplete)0 else null},(0..23).map {if(it%6==0)"${it}h" else ""},null,"Pas de progression aujourd’hui.",
                                comparison=if(comparable)(0..23).map {ownHours[it] ?: if(own.hourlyComplete)0 else null} else null)
                            if(!comparable)Text("Comparaison horaire indisponible : jours ou fuseaux différents.",color=Muted,fontSize=11.sp)
                            if(!profile.hourlyComplete)Text("Historique partiel : ${profile.hourlyXp.sumOf {it.xp}} XP horodatés. Le total du jour reste affiché au-dessus.",fontSize=12.sp,color=Muted)
                        } else Text(if(current)"Le total du jour est connu, mais sa répartition horaire n’a pas été enregistrée." else "La courbe horaire sera disponible après une synchronisation depuis une version récente de Hamigo.",color=Muted,fontSize=13.sp,lineHeight=19.sp)
                    }}
                    item {Panel {
                        Text("Statistiques",fontWeight=FontWeight.Bold,fontSize=19.sp)
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                FriendStatistic(profile.xp,"XP au total",Icons.Rounded.Bolt,Color(0xFFBA861C),Modifier.weight(1f))
                                FriendStatistic(profile.streak,if(profile.streak==1)"Jour de série" else "Jours de série",Icons.Rounded.LocalFireDepartment,Coral,Modifier.weight(1f))
                            }
                            Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                FriendStatistic(profile.lessons,if(profile.lessons==1)"Leçon terminée" else "Leçons terminées",Icons.Rounded.School,Teal,Modifier.weight(1f))
                                FriendStatistic(SocialActivity.weeklyXp(profile,date),if(profile.activityHistory==null&&profile.dailyXp.isEmpty())"Dernier bilan hebdo" else "XP cette semaine",Icons.Rounded.DateRange,Purple,Modifier.weight(1f))
                            }
                        }
                    }}
                    item {Panel(spacing=4.dp) {
                        Text("Dans ton équipe",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        FriendActionItems(model,friend,actions,group=FriendActionGroup.TEAM)
                    }}
                    item {Panel(spacing=4.dp) {
                        Text("Sur GitHub",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        FriendActionItems(model,friend,actions,group=FriendActionGroup.GITHUB)
                    }}
                    item {Column(Modifier.padding(horizontal=4.dp,vertical=4.dp).testTag("friend-technical-info"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("Hamigo ${profile.appVersion ?: "≤0.49+8ed1305d"}",fontSize=12.sp,lineHeight=16.sp,color=Muted)
                            profile.appVersionCode?.let {Text("Code de version : $it",fontSize=12.sp,lineHeight=16.sp,color=Muted)}
                            Text("Dernière vérification : ${friendTechnicalDate(readStatus.checkedAt)}",fontSize=12.sp,lineHeight=16.sp,color=Muted)
                            Text("Modification du Gist : ${friendTechnicalDate(readStatus.updatedAt)}",fontSize=12.sp,lineHeight=16.sp,color=Muted)
                    }}
                }
                }
            }
            FriendActionDialogs(model,friend,actions,onRemoved=dismiss)
        }
    }
}

@Composable private fun FriendStatistic(value:Int,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color,modifier:Modifier) {
    val number=when {
        value>=1_000_000_000->String.format(Locale.FRENCH,"%.1f Md",value/1_000_000_000.0)
        value>=1_000_000->String.format(Locale.FRENCH,"%.1f M",value/1_000_000.0)
        else->String.format(Locale.FRENCH,"%,d",value)
    }
    Surface(modifier=modifier.fillMaxHeight().heightIn(min=74.dp).clearAndSetSemantics {contentDescription="$value · $label"},shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color=tint.copy(alpha=.055f),border=androidx.compose.foundation.BorderStroke(1.dp,tint.copy(alpha=.22f))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(icon,null,Modifier.size(22.dp),tint=tint)
                Text(number,fontSize=if(number.length>6)16.sp else 21.sp,fontWeight=FontWeight.ExtraBold,color=Ink,maxLines=1)
            }
            Text(label,fontSize=12.sp,lineHeight=16.sp,color=Muted)
        }
    }
}

internal fun friendTechnicalDate(value:String?):String = value?.let {
    runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm",Locale.FRENCH)) }.getOrNull()
} ?: "Non disponible"
