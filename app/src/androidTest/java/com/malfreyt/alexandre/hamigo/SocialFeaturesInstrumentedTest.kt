package com.malfreyt.alexandre.hamigo

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SocialFeaturesInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val saved=mutableMapOf<String,Map<String,Any?>>()
    private val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var model:AppModel
    private lateinit var seededFriends:List<Friend>
    private val sent=CopyOnWriteArrayList<Intent>()
    private val monitor=object:Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
            if(intent.action==Intent.ACTION_CHOOSER){sent+=intent;return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)}
            return null
        }
    }
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish"))
            listOf("hamigo","hamigo_social","hamigo_secure","hamigo_diagnostics").forEach {file->
                val p=context.getSharedPreferences(file,Context.MODE_PRIVATE);saved[file]=p.all.toMap();check(p.edit().clear().commit())
            }
            context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false).putBoolean("interactionHaptics",false).commit()
            ProgressSyncScheduler.cancel(context);instrumentation.addMonitor(monitor)
        }
        override fun after() {
            instrumentation.removeMonitor(monitor)
            saved.forEach {(file,data)->val e=context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear()
                data.forEach {(key,v)->when(v){is String->e.putString(key,v);is Boolean->e.putBoolean(key,v);is Int->e.putInt(key,v);is Long->e.putLong(key,v);is Float->e.putFloat(key,v);is Set<*>->e.putStringSet(key,v.filterIsInstance<String>().toSet())}}
                e.commit()}
        }
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000){check(model.error==null){model.error!!};model.content!=null&&!model.socialRefreshing}
        val today=LocalDate.now();val daily=JSONObject()
        listOf(12,0,30,12,18,0,24).forEachIndexed {i,x->if(x>0)daily.put(today.minusDays((6-i).toLong()).toString(),x)}
        val completed=JSONArray(model.content!!.lessons.take(1).map {it.id})
        val base=JSONObject().put("xp",350).put("answers",30).put("correct",24).put("completed",completed).put("dailyXp",daily).put("awarded",JSONObject())
        val root=JSONObject().put("schema",2).put("questionIdsVersion",1).put("xp",350).put("answers",30).put("correct",24).put("completed",completed).put("dailyXp",daily).put("awarded",JSONObject()).put("reviews",JSONObject()).put("syncBase",base).put("syncEvents",JSONObject())
        val own=GitHubIdentity("hamigo-demo-own",999999901);val cam=GitHubIdentity("hamigo-demo-cam",999999902);val noa=GitHubIdentity("hamigo-demo-noa",999999903)
        listOf(own to Color.rgb(83,146,173),cam to Color.rgb(193,120,86),noa to Color.rgb(139,121,177)).forEach {(identity,color)->seedAvatar(identity,color)}
        val days=listOf(12,24,0,30,24,60,60).mapIndexed {i,x->DailyPoint(today.minusDays((6-i).toLong()).toString(),x)}
        val historical=JSONObject().also {json->(0L..59L).forEach {json.put(today.minusDays(it).toString(),12)};days.forEach {json.put(it.day,it.xp)}}
        val friends=listOf(Friend(ShareProgress("Camille Démo",1500,5,13,210,dailyXp=days,dailyGoal=120,hourlyDay=today.toString(),hourlyXp=listOf(HourlyPoint(9,30),HourlyPoint(12,30)),hourlyComplete=true,zoneId=ZoneId.systemDefault().id,activityHistory=PublicActivityHistory.fromDailyXp(historical,today),appVersion="0.49+12345678",appVersionCode=49),"https://gist.github.com/"+"1".repeat(32),githubIdentity=cam),
            Friend(ShareProgress("Noa Démo",650,7,10,105,dailyXp=(6L downTo 0L).map {DailyPoint(today.minusDays(it).toString(),15)},dailyGoal=60),"https://gist.github.com/"+"2".repeat(32),githubIdentity=noa))
        seededFriends=friends
        // Keep the emulator-only cache consistent when the Activity resumes after the chooser.
        val friendCache=JSONArray(friends.map {friend->JSONObject().put("gist",friend.gist).put("modifiedAt",friend.modifiedAt)
            .put("progress",JSONObject(friend.progress.toJson())).put("githubIdentity",friend.githubIdentity?.toJson())})
        ui.runOnIdle {
            model.progress.prefs.edit().putString("name","Alex Démo").putString("progress",root.toString()).putString("friends",friendCache.toString()).putInt("dailyGoal",30).commit()
            context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE).edit().putString("ownerLogin",own.login).putLong("ownerId",own.id!!).commit()
            model.progress.reload();model.showWelcome=false;model.revision++
        }
        ui.waitForIdle();ui.runOnIdle {model.friends=friends}
    }
    private fun seedAvatar(identity:GitHubIdentity,color:Int) {
        val url=identity.avatarUrl!!;val digest=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){"%02x".format(it)}
        val dir=File(context.cacheDir,"github-avatar-images").apply {mkdirs()};val image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(color);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=Color.WHITE}
        canvas.drawCircle(64f,46f,19f,paint);canvas.drawOval(29f,73f,99f,139f,paint)
        File(dir,"$digest.img").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    private fun before()=model.progress.prefs.getString("progress",null)
    private fun capture(name:String) {
        ui.waitForIdle();repeat(2) {val latch=CountDownLatch(1);instrumentation.runOnMainSync {
            val view=if(Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews().lastOrNull {it.hasWindowFocus()} else null
            (view ?: ui.activity.window.decorView).let {v->v.viewTreeObserver.registerFrameCommitCallback {latch.countDown()};v.invalidate()}
        };assertTrue(latch.await(5,TimeUnit.SECONDS))}
        val bitmap=instrumentation.uiAutomation.takeScreenshot();val dir=File(context.getExternalFilesDir(null),"social-0.49").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun rankingAndFriendCardsOpenTheSameFullScreenHistoryAndReturnToTheList() {
        val original=before();ui.runOnIdle {model.route="friends"}
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-1"))
        capture("team-ranking-clickable")
        ui.onNodeWithTag("team-rank-1").performClick();ui.onNodeWithTag("friend-progress-screen").assertIsDisplayed()
        ui.onNodeWithText("60 / 120 XP").assertIsDisplayed();capture("friend-goal-calendar")
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasText("Cette semaine"))
        capture("friend-progress-week")
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasText("Aujourd’hui · heure par heure"))
        capture("friend-progress-hours")
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasText("Statistiques"))
        ui.onNodeWithContentDescription("1500 · XP au total").assertExists()
        ui.onNodeWithContentDescription("5 · Jours de série").assertExists()
        ui.onNodeWithContentDescription("13 · Leçons terminées").assertExists()
        capture("friend-statistics")
        // The full-screen profile is a separate Dialog window, not the Activity root.
        androidx.test.espresso.Espresso.pressBackUnconditionally();ui.onNodeWithTag("friend-progress-screen").assertDoesNotExist()
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("friend-card-https://gist.github.com/${"1".repeat(32)}"))
        ui.onNodeWithTag("friend-card-https://gist.github.com/${"1".repeat(32)}").performClick();ui.onNodeWithTag("friend-progress-screen").assertIsDisplayed()
        assertEquals(original,before())
    }
    @Test fun profileShowsTodayGoalAndTheMemoryLinkLocatesReviewsWithoutChangingProgress() {
        val original=before();ui.runOnIdle {model.route="profile"}
        ui.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("daily-goal-summary"))
        ui.onNodeWithText("24 / 60 XP").assertIsDisplayed();capture("profile-goal")
        ui.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("open-reviews-from-profile"))
        // Clear the floating navigation overlay, which does not participate in
        // the LazyColumn's viewport used by performScrollToNode.
        ui.onNodeWithTag("profile-list").performScrollToIndex(6)
        capture("profile-memory-link")
        ui.onNodeWithTag("open-reviews-from-profile").performClick()
        ui.waitUntil(10_000){model.route=="practice"&&model.practiceFocusRequest==null}
        capture("reviews-focused")
        ui.onNodeWithTag("practice-reviews").assertIsDisplayed();ui.onNodeWithText("Lancer les révisions").assertIsDisplayed()
        assertEquals("practice",model.route);assertEquals(original,before())
    }
    @Test fun goalPencilLocatesTheActualSettingWithoutChangingTheSelectedGoal() {
        val original=before();val goal=model.progress.dailyGoal
        ui.runOnIdle {model.route="profile"}
        ui.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("edit-daily-goal"))
        val pencil=ui.onNodeWithTag("edit-daily-goal");val list=ui.onNodeWithTag("profile-list")
        val distance=(pencil.fetchSemanticsNode().boundsInRoot.center.y-list.fetchSemanticsNode().boundsInRoot.center.y).coerceAtLeast(0f)
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){scroll->scroll(0f,distance)}
        capture("profile-goal-pencil");pencil.performClick()
        ui.waitUntil(10_000){model.route=="settings"&&model.settingsGoalFocusRequest==null}
        ui.onNodeWithTag("settings-daily-goal").assertIsDisplayed();ui.onNodeWithText("60 XP").assertIsSelected()
        capture("goal-settings-focused")
        assertEquals(goal,model.progress.dailyGoal);assertEquals(original,before())
    }
    @Test fun friendCalendarAndWeeksBrowseHistoryAndRemovalStillNeedsConfirmation() {
        val original=before();ui.runOnIdle {model.route="friends"}
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-1"));ui.onNodeWithTag("team-rank-1").performClick()
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasContentDescription("Voir le mois précédent"))
        ui.onNodeWithContentDescription("Voir le mois précédent").performClick()
        val french=Locale.FRENCH;val label=YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",french)).replaceFirstChar {it.titlecase(french)}
        ui.onNodeWithText(label).assertIsDisplayed();capture("friend-previous-month")
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasContentDescription("Voir la semaine précédente"))
        ui.onNodeWithContentDescription("Voir la semaine précédente").performClick()
        ui.onNodeWithText("Historique hebdomadaire").assertIsDisplayed();capture("friend-previous-week")
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasText("Retirer cet équipier"))
        ui.onNodeWithText("Ouvrir le profil GitHub").assertExists();ui.onNodeWithText("Ouvrir le Gist social").assertExists();capture("friend-actions")
        ui.onNodeWithText("Retirer cet équipier").performClick();ui.onNodeWithText("Retirer Camille Démo ?").assertIsDisplayed()
        ui.onNodeWithText("Annuler").performClick();ui.onNodeWithTag("friend-progress-screen").assertIsDisplayed()
        assertEquals(2,model.friends.size);assertEquals(original,before());assertTrue(sent.isEmpty())
    }
    @Test fun friendActionsAreGroupedVersionIsVisibleAndEncouragementIncludesTheirStats() {
        val original=before();ui.runOnIdle {model.route="friends"}
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-1"));ui.onNodeWithTag("team-rank-1").performClick()
        val goal=ui.onNodeWithTag("daily-goal-summary").fetchSemanticsNode().boundsInRoot
        val nudge=ui.onNodeWithTag("encourage-friend");nudge.assertIsDisplayed();assertTrue(nudge.fetchSemanticsNode().boundsInRoot.top>=goal.bottom)
        capture("friend-top-encouragement");nudge.performClick();ui.waitUntil(20_000){sent.size==1}
        val target=sent.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertTrue(target.getStringExtra(Intent.EXTRA_TEXT)!!.contains("60 XP aujourd’hui sur ton objectif de 120 XP"))
        assertTrue(target.getStringExtra(Intent.EXTRA_TEXT)!!.contains("cette semaine"))
        copySharedImage(target,"share-friend-contextual",720)
        val list=ui.onNodeWithTag("friend-progress-list")
        list.performScrollToNode(hasText("Ouvrir le Gist social"))
        val first=ui.onNodeWithText("Ouvrir le profil GitHub").fetchSemanticsNode().boundsInRoot
        val second=ui.onNodeWithText("Ouvrir le Gist social").fetchSemanticsNode().boundsInRoot
        assertTrue("Links must not acquire another panel gap",second.top-first.bottom in -1f..2f)
        list.performScrollToNode(hasText("Hamigo 0.49+12345678"));ui.onNodeWithText("Hamigo 0.49+12345678").assertIsDisplayed()
        ui.onNodeWithText("Code de version : 49").assertExists();capture("friend-grouped-actions-version")
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-2"));ui.onNodeWithTag("team-rank-2").performClick()
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasText("Hamigo ≤0.49+8ed1305d"))
        ui.onNodeWithText("Hamigo ≤0.49+8ed1305d").assertIsDisplayed();capture("friend-legacy-version")
        assertEquals(original,before())
    }
    @Test fun friendPullRefreshReadsOnlyThatGistAndKeepsSuccessfulDatesOnFailure() {
        val original=before();val originalSync=model.sync
        val friend=seededFriends.first();val id=GitHubSync.gistId(friend.gist)
        val requests=CopyOnWriteArrayList<String>()
        val gate=kotlinx.coroutines.CompletableDeferred<Unit>()
        var failed=false
        val checked=java.time.Instant.parse("2026-10-12T12:30:00Z")
        val updated="2026-10-11T08:15:00Z"
        val gateway=object:GitHubGateway {
            override suspend fun api(method:String,path:String,token:String?,body:String?):String {
                requests+="$method $path";gate.await()
                if(failed)throw SocialException("Offline")
                return JSONObject().put("updated_at",updated).put("owner",friend.githubIdentity!!.toJson())
                    .put("files",JSONObject().put(GitHubSync.FILE_NAME,JSONObject().put("content",
                        friend.progress.copy(xp=1600,updatedAt=java.time.Instant.now().plusSeconds(10).toString()).toJson()))).toString()
            }
            override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String=error("Unexpected")
        }
        try {
            ui.runOnIdle {model.sync=GitHubSync(context,gateway){checked};model.route="friends"}
            ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-1"));ui.onNodeWithTag("team-rank-1").performClick()
            ui.onNodeWithTag("friend-profile-refresh").performTouchInput {swipeDown(durationMillis=600)}
            ui.waitUntil(10_000){model.refreshingFriend==id&&requests.size==1}
            capture("friend-refresh-loading");gate.complete(Unit)
            ui.waitUntil(10_000){model.refreshingFriend==null&&model.friends.first().progress.xp==1600}
            assertEquals(listOf("GET /gists/$id"),requests.toList())
            assertEquals(friend.modifiedAt,model.friends.first().modifiedAt)
            val status=model.sync.friendReadStatus(friend.gist)
            assertEquals(FriendReadStatus(checked.toString(),updated),status)
            ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasTestTag("friend-technical-info"))
            ui.onNodeWithText("Dernière vérification : ${friendTechnicalDate(checked.toString())}").assertIsDisplayed()
            ui.onNodeWithText("Modification du Gist : ${friendTechnicalDate(updated)}").assertIsDisplayed()
            capture("friend-refresh-technical-dates")
            ui.onNodeWithTag("friend-progress-list").performScrollToIndex(0)
            failed=true
            ui.onNodeWithTag("friend-profile-refresh").performTouchInput {swipeDown(durationMillis=600)}
            ui.waitUntil(10_000){model.friendRefreshError!=null&&model.refreshingFriend==null}
            ui.onNodeWithText("Actualisation impossible. Le dernier profil reste disponible.").assertIsDisplayed()
            assertEquals(status,model.sync.friendReadStatus(friend.gist))
            assertEquals(1600,model.friends.first().progress.xp)
            assertEquals(2,requests.size);assertEquals(original,before())
            capture("friend-refresh-offline")
        } finally {gate.complete(Unit);ui.runOnIdle {model.sync=originalSync}}
    }

    @Test fun offlineDemoFriendWithoutGistHasUnavailableTechnicalDates() {
        val original=before()
        ui.runOnIdle {model.friends=seededFriends.map {it.copy(gist="")};model.route="friends"}
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-1"));ui.onNodeWithTag("team-rank-1").performClick()
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasTestTag("friend-technical-info"))
        ui.onNodeWithText("Dernière vérification : Non disponible").assertIsDisplayed()
        ui.onNodeWithText("Modification du Gist : Non disponible").assertIsDisplayed()
        capture("friend-demo-no-gist")
        assertEquals(original,before())
    }

    @Test fun friendHeaderShadowAppearsAcrossTheViewportAndDisappearsAtTheTop() {
        val original=before();ui.runOnIdle {model.route="friends"}
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("team-rank-1"));ui.onNodeWithTag("team-rank-1").performClick()
        val header=ui.onNodeWithTag("friend-progress-header")
        val root=ui.onNodeWithTag("friend-progress-screen")
        val headerBounds=header.fetchSemanticsNode().boundsInRoot
        val rootOrigin=root.fetchSemanticsNode().boundsInRoot
        fun profileBitmap():Bitmap {
            repeat(2) {
                val frame=CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val view=if(Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews().lastOrNull {it.hasWindowFocus()} else null
                    val target=view ?: ui.activity.window.decorView
                    target.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};target.invalidate()
                }
                assertTrue(frame.await(5,TimeUnit.SECONDS))
            }
            return instrumentation.uiAutomation.takeScreenshot()
        }
        val initialPixels=profileBitmap().asImageBitmap().toPixelMap()
        fun captureProfile(name:String) {
            val image=profileBitmap()
            val dir=File(context.getExternalFilesDir(null),"social-0.49").apply {mkdirs()}
            try {File(dir,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {image.recycle()}
        }
        fun reds():List<Float> {
            val bounds=header.fetchSemanticsNode().boundsInRoot
            val origin=root.fetchSemanticsNode().boundsInRoot
            val pixels=profileBitmap().asImageBitmap().toPixelMap()
            val y=(bounds.bottom-origin.top+with(ui.density){1.dp.toPx()}).toInt()
            return listOf(2,pixels.width/2,pixels.width-3).map {pixels[it,y].red}
        }
        val initial=reds();captureProfile("friend-header-attached")
        val list=ui.onNodeWithTag("friend-progress-list")
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){scroll->scroll(0f,with(ui.density){80.dp.toPx()})}
        ui.waitForIdle();val scrolled=reds()
        scrolled.forEach {assertTrue("Header shadow must reach the full width",it<Cream.red-.08f)}
        assertEquals("Both side gutters have the same shadow",scrolled[0],scrolled[2],.02f)
        assertTrue("The card must not paint over the middle of the shadow",scrolled[1]<=maxOf(scrolled[0],scrolled[2])+.03f)
        header.assertIsDisplayed()
        val afterPixels=profileBitmap().asImageBitmap().toPixelMap()
        var difference=0f;var samples=0
        for(y in (headerBounds.top-rootOrigin.top).toInt() until (headerBounds.bottom-rootOrigin.top).toInt() step 3) {
            for(x in 0 until initialPixels.width step 3) {
                val before=initialPixels[x,y];val after=afterPixels[x,y]
                difference+=kotlin.math.abs(before.red-after.red)+kotlin.math.abs(before.green-after.green)+kotlin.math.abs(before.blue-after.blue);samples++
            }
        }
        assertTrue("Scrolling must preserve the avatar and header text",difference/samples<.02f)
        captureProfile("friend-header-detached")
        list.performScrollToIndex(0);ui.waitForIdle()
        reds().forEachIndexed {i,value->assertEquals("Returning to the top removes the shadow",initial[i],value,.02f)}
        assertEquals(original,before())
    }
    @Test fun settingsActionGroupsHaveCompactRowsAndKeepComfortableTouchAreas() {
        val original=before();val density=context.resources.displayMetrics.density
        ui.runOnIdle {model.route="settings"}
        val list=ui.onNodeWithTag("settings-list")
        list.performScrollToNode(hasTestTag("settings-source-links"))
        val sourceLabels=listOf("Cours F6KGL · CC BY-NC-SA 4.0","Questions communautaires Exam1 · REF","Certificat · informations ANFR")
        val rows=sourceLabels.map {ui.onNodeWithText(it).fetchSemanticsNode().boundsInRoot}
        rows.forEach {assertTrue("Touch row smaller than 48 dp",it.height>=48*density-1f)}
        rows.zipWithNext().forEach {(a,b)->assertTrue("Extra gap in a link group",b.top-a.bottom in -1f..2f)}
        capture("settings-compact-sources")
        list.performScrollToNode(hasTestTag("settings-project-links"));capture("settings-compact-project")
        list.performScrollToNode(hasContentDescription("Afficher les options de sauvegarde"));ui.onNodeWithContentDescription("Afficher les options de sauvegarde").performClick()
        list.performScrollToNode(hasTestTag("settings-backup-links"));capture("settings-compact-backup")
        assertEquals(original,before());assertTrue(sent.isEmpty())
    }
    @Test fun everyCompletedSessionSharesItsOwnResultsAndShowsTheCurrentDailyGoal() {
        val original=before()
        listOf("Cours","Mix","Révisions","Examen blanc","Flashcards").forEach {mode->
            val exam=mode=="Examen blanc";val count=if(exam)40 else 5
            val questions=List(count){i->Question("fixture-$i","Quel appareil commande le contact ?",listOf("Le manipulateur","Le fusible"),0,"Le manipulateur commande la transmission.",kind=if(mode=="Flashcards")"flash" else "choice")}
            ui.runOnIdle {model.session=Session(mode,questions.toMutableList(),lessonId=if(mode=="Cours")model.content!!.lessons.first().id else null,exam=exam,testing=true).apply {index=count;firstCorrect=if(exam)31 else 4;correct=firstCorrect;gain=9;regulationScore=16;techniqueScore=15};model.revision++}
            ui.onNodeWithTag("results-list").performScrollToNode(hasTestTag("daily-goal-summary"))
            ui.onNodeWithText("24 / 60 XP").assertIsDisplayed()
            if(mode=="Cours")capture("course-results-goal")
            ui.onNodeWithTag("results-list").performScrollToNode(hasText("Partager mes résultats"))
            val n=sent.size;ui.onNodeWithText("Partager mes résultats").performClick();ui.waitUntil(20_000){sent.size>n}
            val target=sent.last().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("image/png",target.type);assertTrue(target.getStringExtra(Intent.EXTRA_TEXT)!!.contains(mode))
            assertEquals(original,before())
        }
    }
    @Test fun applicationAndNudgeUseTheShareSheetWithAnImageAndTheRightText() {
        val original=before();ui.runOnIdle {model.route="settings"}
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("share-application"))
        ui.onNodeWithTag("share-application").performClick();ui.waitUntil(20_000){sent.size==1}
        val application=sent.first().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png",application.type);assertTrue(application.getStringExtra(Intent.EXTRA_TEXT)!!.contains(NativeShare.APP_DOWNLOAD_URL))
        assertTrue(application.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        copySharedImage(application,"share-application-poster",1080)
        ui.runOnIdle {model.route="friends";model.friends=seededFriends}
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag("friend-card-https://gist.github.com/${"1".repeat(32)}"))
        val encourage=ui.onNode(hasText("Encourager") and hasAnyAncestor(hasTestTag("friend-card-https://gist.github.com/${"1".repeat(32)}")))
        encourage.performScrollTo()
        val list=ui.onNodeWithTag("friends-list")
        val distance=(encourage.fetchSemanticsNode().boundsInRoot.center.y-list.fetchSemanticsNode().boundsInRoot.center.y).coerceAtLeast(0f)
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){scroll->scroll(0f,distance)}
        capture("team-nudge-action");encourage.performClick()
        ui.waitUntil(20_000){sent.size==2}
        val nudge=sent.last().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png",nudge.type);assertTrue(nudge.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Camille Démo"))
        copySharedImage(nudge,"share-nudge-poster",720);assertEquals(original,before())
    }
    private fun copySharedImage(intent:Intent,name:String,height:Int) {
        val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!;assertEquals(context.packageName+".files",uri.authority)
        val bytes=context.contentResolver.openInputStream(uri)!!.use {it.readBytes()};val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(1080,bitmap.width);assertEquals(height,bitmap.height);bitmap.recycle()
        val directory=File(context.getExternalFilesDir(null),"social-0.49").apply {mkdirs()};File(directory,"$name.png").writeBytes(bytes)
    }
}
