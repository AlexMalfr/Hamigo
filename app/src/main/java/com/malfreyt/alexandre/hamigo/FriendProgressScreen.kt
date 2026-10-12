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
    val earliest=SocialActivity.earliest(profile)?.coerceAtMost(date)
    val weeks=maxOf(1,earliest?.let {(ChronoUnit.DAYS.between(it,date)/7).toInt()+1} ?: 1)
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
                                Text(if(weekHistory.currentPage==0)"Les 7 derniers jours" else "Historique hebdomadaire",fontSize=20.sp,fontWeight=FontWeight.Bold)
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
                                PublicXpChart(series.map {it.second},series.map {it.first.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.FRENCH).take(2)},profile.dailyGoal,"Pas de progression cette semaine.")
                                if(series.any {it.second==null})Text("— : journée sans données partagées",fontSize=11.sp,color=Muted)
                            }
                        }
                    }}
                    item {Panel {
                        Text("Aujourd’hui · heure par heure",fontSize=19.sp,fontWeight=FontWeight.Bold)
                        val current=profile.hourlyDay==date.toString()
                        if(current&&(profile.hourlyComplete||profile.hourlyXp.isNotEmpty())) {
                            val hours=profile.hourlyXp.associate {it.hour to it.xp}
                            PublicXpChart((0..23).map {hours[it] ?: if(profile.hourlyComplete)0 else null},(0..23).map {if(it%6==0)"${it}h" else ""},null,"Pas de progression aujourd’hui.")
                            if(!profile.hourlyComplete)Text("Historique partiel : ${profile.hourlyXp.sumOf {it.xp}} XP horodatés. Le total du jour reste affiché au-dessus.",fontSize=12.sp,color=Muted)
                        } else Text(if(current)"Le total du jour est connu, mais sa répartition horaire n’a pas été enregistrée." else "La courbe horaire sera disponible après une synchronisation depuis une version récente de Hamigo.",color=Muted,fontSize=13.sp,lineHeight=19.sp)
                    }}
                    item {Panel {
                        Text("${profile.lessons} leçon${if(profile.lessons==1)" terminée" else "s terminées"}",fontWeight=FontWeight.Bold)
                    }}
                    item {Panel(spacing=4.dp) {
                        Text("Sur GitHub",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        FriendActionItems(model,friend,actions,group=FriendActionGroup.GITHUB)
                    }}
                    item {Panel(spacing=4.dp) {
                        Text("Dans ton équipe",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        FriendActionItems(model,friend,actions,group=FriendActionGroup.TEAM)
                    }}
                    item {Column(Modifier.padding(horizontal=4.dp,vertical=4.dp).testTag("friend-technical-info"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("Infos techniques",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Muted)
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

internal fun friendTechnicalDate(value:String?):String = value?.let {
    runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm",Locale.FRENCH)) }.getOrNull()
} ?: "Non disponible"

@Composable internal fun PublicXpChart(values:List<Int?>,labels:List<String>,goal:Int?,emptyLabel:String="Pas de progression sur cette période.") {
    val maximum=maxOf(1,values.filterNotNull().maxOrNull() ?: 0,goal ?: 0)
    val hasActivity=values.any {it!=null&&it>0};val emptyText=if(values.any {it!=null})emptyLabel else "Historique non disponible"
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {contentDescription=if(!hasActivity)emptyText else values.mapIndexed {i,x->"${labels.getOrElse(i){i.toString()}} : ${x?.let {"$it XP"} ?: "sans données"}"}.joinToString(", ")}) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(146.dp)) {
            val step=size.width/values.size.coerceAtLeast(1);val plotHeight=size.height-12.dp.toPx();val scaleHeight=plotHeight-8.dp.toPx();val width=step*.58f
            drawLine(Mist,Offset(0f,plotHeight),Offset(size.width,plotHeight),1.dp.toPx())
            values.forEachIndexed {i,x->
                val h=if(x==null)2.dp.toPx() else if(x==0)0f else maxOf(3.dp.toPx(),scaleHeight*x/maximum)
                if(h>0)drawRoundRect(if(x==null)Muted.copy(alpha=.35f) else if(goal!=null&&x>=goal)Color(0xFFEBC45E) else Teal,Offset(step*(i+.5f)-width/2,plotHeight-h),Size(width,h),CornerRadius(3.dp.toPx()))
            }
            goal?.let {val y=plotHeight-scaleHeight*it/maximum;drawLine(Color(0xFFD97827),Offset(0f,y),Offset(size.width,y),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx())))}
        }
        if(!hasActivity)Text(emptyText,color=Muted,fontSize=12.sp)
        }
        Row(Modifier.fillMaxWidth()){(if(values.size>12)labels.filter {it.isNotBlank()} else labels).forEach {Text(it,Modifier.weight(1f),fontSize=10.sp,color=Muted,textAlign=if(values.size>12)TextAlign.Start else TextAlign.Center,maxLines=1)}}
        if(hasActivity)Text("${maximum} XP",fontSize=10.sp,color=Muted,modifier=Modifier.align(Alignment.End))
    }
}
