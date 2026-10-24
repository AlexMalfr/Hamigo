package com.malfreyt.alexandre.hamigo.releaseprobe;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;
import java.time.LocalDate;
import java.time.ZoneId;

/** Platform-only black-box runner: independent of every class/API renamed by production R8. */
public final class ReleaseProbe extends Instrumentation {
    private String mode;
    private String requestedRotation;
    private Context context;
    private volatile Activity resumedActivity;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity){resumedActivity=activity;}
        @Override public void onActivityPaused(Activity activity){if(resumedActivity==activity)resumedActivity=null;}
        @Override public void onActivityCreated(Activity activity,Bundle state){}
        @Override public void onActivityStarted(Activity activity){}
        @Override public void onActivityStopped(Activity activity){}
        @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
        @Override public void onActivityDestroyed(Activity activity){}
    };
    @Override public void onCreate(Bundle arguments) {super.onCreate(arguments);mode=arguments.getString("mode","storage");requestedRotation=arguments.getString("hamigoRotation");start();}
    @Override public void onStart() {
        Bundle output=new Bundle();
        try {
            check(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"),"Probe is reserved for the emulator");
            context=getTargetContext();
            ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(lifecycle);
            check((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0,"Install the signed production release");
            if(mode.equals("identity"))identity();
            if(mode.equals("flow"))flow();
            if(mode.equals("diagnostics"))diagnostics();
            if(mode.equals("catalog"))catalog();
            if(mode.equals("social"))social();
            if(mode.equals("speech"))speech();
            if(mode.equals("narration"))narration();
            if(mode.equals("learning"))learning();
            if(mode.equals("feedback"))feedback();
            if(mode.equals("icons"))icons();
            if(mode.equals("review"))review();
            recordStorage();((Application)context.getApplicationContext()).unregisterActivityLifecycleCallbacks(lifecycle);output.putString("stream","OK: "+mode+" on the actual signed release\n");finish(Activity.RESULT_OK,output);
        } catch(Throwable e) {
            if(context!=null)((Application)context.getApplicationContext()).unregisterActivityLifecycleCallbacks(lifecycle);
            try {capture("release-failure");recordStorage();}catch(Exception ignored){}
            java.io.StringWriter text=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(text));
            output.putString("stream",text.toString());finish(Activity.RESULT_CANCELED,output);
        }
    }
    /** Confirms that installing optimized dev over debug preserved its private data. */
    private void identity() throws Exception {
        String packageName=context.getPackageName();
        check(packageName.equals("com.malfreyt.alexandre.hamigo.dev"),"Identity fixture targets dev only");
        check(context.getApplicationInfo().loadLabel(context.getPackageManager()).toString().equals("Hamigo Dev"),"Distinct dev label");
        String marker=new String(Files.readAllBytes(new File(context.getFilesDir(),"versioning-upgrade-sentinel.txt").toPath()),StandardCharsets.UTF_8);
        check(marker.equals("hamigo-dev-upgrade-fixture-v1"),"Debug-to-R8 update preserves private files");
        android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(packageName,0);
        check(info.versionName.contains("-dev"),"Dev version remains explicit");
        android.content.pm.ProviderInfo provider=context.getPackageManager().resolveContentProvider(packageName+".files",0);
        check(provider!=null&&provider.packageName.equals(packageName),"FileProvider belongs to dev");
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(packageName);
        check(launch!=null&&launch.getComponent().getClassName().equals("com.malfreyt.alexandre.hamigo.MainActivity"),"Launcher resolves the original class namespace");
        JSONObject report=new JSONObject().put("applicationId",packageName).put("versionName",info.versionName)
            .put("versionCode",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode).put("uid",context.getApplicationInfo().uid)
            .put("dataDirectory",context.getApplicationInfo().dataDir).put("privateFilePreserved",true);
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"identity-probe.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
    }
    private void applyRequestedRotation() {
        if(requestedRotation==null)return;
        int rotation=Integer.parseInt(requestedRotation);check(rotation>=0&&rotation<=3,"Rotation must be 0..3");
        check(getUiAutomation().setRotation(rotation),"Requested display rotation accepted");
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until) {
            Bitmap screen=getUiAutomation().takeScreenshot();
            if(screen!=null) {
                boolean matches=(screen.getWidth()>screen.getHeight())==(rotation%2==1);screen.recycle();
                if(matches)return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Display did not reach rotation "+rotation);
    }
    private Activity open() {
        Activity activity=startActivitySync(new Intent().setClassName(context.getPackageName(),"com.malfreyt.alexandre.hamigo.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        applyRequestedRotation();
        // The launcher can stay portrait; rotate only after the app is open and follow its recreation.
        long until=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<until) {
            Activity current=resumedActivity;
            if(current!=null&&!current.isDestroyed()&&!current.isFinishing()) {
                android.view.View decor=current.getWindow().getDecorView();
                if(decor.getWidth()>0&&decor.getHeight()>0&&(requestedRotation==null||(decor.getWidth()>decor.getHeight())==(Integer.parseInt(requestedRotation)%2==1)))return current;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("No resumed activity in the requested format");
    }
    /** Exercise the published reviewer route and normal connected screens against their local demo service. */
    private void review() throws Exception {
        String[] stores={"hamigo","hamigo_social","hamigo_secure","hamigo_diagnostics","hamigo_privacy","hamigo_review_access","hamigo_pending_oauth",
            "play_review_hamigo","play_review_hamigo_social","play_review_hamigo_secure","play_review_hamigo_diagnostics",
            "play_review_hamigo_privacy","play_review_hamigo_pending_oauth","play_review_review_state","play_review_review_cloud"};
        java.util.Map<String,java.util.Map<String,?>> saved=new java.util.LinkedHashMap<>();
        for(String name:stores)saved.put(name,new java.util.HashMap<>(context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll()));
        Activity activity=null;
        try {
            for(String name:stores)check(context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit(),"Clear isolated reviewer fixture");
            context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",false).putInt("onboardingStep",5).putBoolean("autoSync",false)
                .putBoolean("interactionSound",false).putBoolean("interactionHaptics",false).putBoolean("reminderEnabled",false)
                .putString("name","Personal Fixture").putInt("hamigoTourStep",-1).commit();
            try(java.io.InputStream license=context.getAssets().open("licenses/LICENSE")) {
                check(new String(license.readAllBytes(),StandardCharsets.UTF_8).contains("GNU GENERAL PUBLIC LICENSE"),"GPL text is available offline in the actual APK");
            }
            activity=open();waitText("HAMIGO",30000);String original=learningProgress();
            waitText("Retrouve ta progression",10000);click("Se connecter avec GitHub",false);waitText("Avant de rejoindre l’équipe",10000);
            check(!context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getBoolean("welcomed",true),"Opening consent does not finish onboarding");
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getInt("hamigoTourStep",-1)==-1,"Consent does not start the tutorial behind the sheet");
            captureLearning("review-onboarding-team-rules");click("Accepter et continuer",false);waitText("Tes données sur GitHub",10000);
            captureLearning("review-onboarding-data-consent");click("Sans connexion",false);waitText("Retrouve ta progression",10000);
            click("Données et confidentialité",false);reveal("Politique de confidentialité",false);
            check(find("Accès de démonstration",false)==null,"No visible demonstration entry in onboarding");
            click("Commencer sans compte",false);waitText("Passer le tutoriel",10000);click("Passer le tutoriel",false);
            waitText("Passer le tutoriel ?",10000);click("Passer le tutoriel",false);waitText("Le parcours est à toi !",10000);click("Commencer",false);
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getBoolean("welcomed",false),"Ordinary onboarding completed without an account");
            click("Mémo",false);click("Ouvrir la calculatrice",true);waitText("Calculatrice",10000);
            setText("691427083519640782135906");click("=",false);waitText("Accès de démonstration",10000);
            captureLearning("review-calculator-entry");click("Entrer dans la démonstration",false);
            waitText("DÉMONSTRATION",30000);
            long until=SystemClock.uptimeMillis()+30000;
            while(context.getSharedPreferences("play_review_hamigo_social",Context.MODE_PRIVATE).getString("ownGistId",null)==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
            check(context.getSharedPreferences("play_review_hamigo_social",Context.MODE_PRIVATE).getString("ownGistId",null)!=null,"Normal sync creates the demo social Gist");
            check(original.equals(learningProgress()),"Entering demo never changes personal progression");
            reveal("Accepter",false);click("Accepter",false);SystemClock.sleep(500);
            captureLearning("review-team");
            Activity previous=resumedActivity;runOnMainSync(previous::finishAndRemoveTask);waitForIdleSync();activity=open();
            waitText("DÉMONSTRATION",30000);
            check(context.getSharedPreferences("hamigo_review_access",Context.MODE_PRIVATE).getBoolean("active",false),"Demo access persists after a new activity/model");
            click("Moi",false);click("Réglages",true);reveal("Synchroniser maintenant",false);click("Synchroniser maintenant",false);
            waitText("Progressions actualisées.",10000);
            String demoBefore=context.getSharedPreferences("play_review_hamigo",Context.MODE_PRIVATE).getString("progress","");
            reveal("Licences et attributions",false);click("Licences et attributions",false);
            captureLearning("review-licenses-menu");
            click("Périmètre des licences et attribution du cours",false);waitText("Licences de Hamigo",10000);
            reveal("Banque Exam’1 et illustrations téléchargées depuis le REF",false);captureLearning("review-licenses-markdown");
            click("Fermer",false);
            reveal("Mes données",false);click("Mes données",false);click("Supprimer mes données sur GitHub",false);
            waitText("Supprimer les Gists Hamigo ?",10000);captureLearning("review-delete-confirmation");click("Supprimer",false);
            until=SystemClock.uptimeMillis()+15000;
            while(context.getSharedPreferences("play_review_hamigo_social",Context.MODE_PRIVATE).contains("ownerLogin")&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
            check(!context.getSharedPreferences("play_review_hamigo_social",Context.MODE_PRIVATE).contains("ownerLogin"),"Deleting demo Gists disconnects only the demo account");
            check(demoBefore.equals(context.getSharedPreferences("play_review_hamigo",Context.MODE_PRIVATE).getString("progress","")),"Demo deletion preserves demo learning");
            click("Fermer",false);click("Quitter",false);waitText("Salut Personal",30000);
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getBoolean("welcomed",false),"Leaving demo keeps completed onboarding");
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getInt("hamigoTourStep",0)==-1,"Leaving demo keeps the skipped tutorial finished");
            check(!context.getSharedPreferences("hamigo_review_access",Context.MODE_PRIVATE).getBoolean("active",false),"Exit returns to personal data");
            check(original.equals(learningProgress()),"Personal progression restored unchanged");
            check("Personal Fixture".equals(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("name",null)),"Personal profile retained");
            File directory=new File(context.getExternalFilesDir(null),"review-release");directory.mkdirs();
            Files.write(new File(directory,"review-probe.json").toPath(),new JSONObject().put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
                .put("reviewerCodeWorks",true).put("calculatorEntryOnly",true).put("onboardingConsentKeepsPage",true).put("personalPathRestored",true).put("normalSyncAndInbox",true).put("persistentDemo",true).put("demoDeletion",true)
                .put("personalProgressUnchanged",true).put("offlineLicense",true).put("nativeLicenseMarkdown",true).put("realGitHubAccountUsed",false).toString(2).getBytes(StandardCharsets.UTF_8));
        } finally {
            Activity closing=resumedActivity!=null?resumedActivity:activity;
            if(closing!=null)runOnMainSync(closing::finishAndRemoveTask);waitForIdleSync();
            for(String name:stores)restoreLearningPreferences(name,saved.get(name));
        }
    }
    /** Render the packaged adaptive layers through Android, without touching launcher preferences. */
    private void icons() throws Exception {
        android.graphics.drawable.Drawable packaged=context.getPackageManager().getApplicationIcon(context.getPackageName());
        check(packaged instanceof android.graphics.drawable.AdaptiveIconDrawable,"Application icon is adaptive");
        android.graphics.drawable.AdaptiveIconDrawable icon=(android.graphics.drawable.AdaptiveIconDrawable)packaged;
        check(icon.getBackground() instanceof android.graphics.drawable.ColorDrawable,"Separate adaptive background");
        check(android.graphics.Color.alpha(((android.graphics.drawable.ColorDrawable)icon.getBackground()).getColor())==0,"Colour foreground has a transparent background");
        android.graphics.drawable.Drawable mono=icon.getMonochrome();check(mono!=null,"Android 13 monochrome layer is packaged");
        File directory=new File(context.getExternalFilesDir(null),"icons-release");check(directory.isDirectory()||directory.mkdirs(),"Icon output directory");
        String[] names={"colour-transparent","theme-light","theme-dark"};
        for(int index=0;index<names.length;index++) {
            android.graphics.drawable.Drawable artwork=icon;
            if(index>0) {
                android.graphics.drawable.Drawable tinted=mono.getConstantState().newDrawable().mutate();
                tinted.setTint(index==1?0xff173f42:0xff9ae1d8);
                artwork=new android.graphics.drawable.AdaptiveIconDrawable(new android.graphics.drawable.ColorDrawable(index==1?0xffe1f2ef:0xff173f42),tinted);
            }
            for(int size:new int[]{384,48}) {
                Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
                artwork.setBounds(0,0,size,size);artwork.draw(new android.graphics.Canvas(bitmap));
                check(android.graphics.Color.alpha(bitmap.getPixel(0,0))==0,"Android applies its launcher mask");
                try(FileOutputStream stream=new FileOutputStream(new File(directory,names[index]+"-"+size+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}
                bitmap.recycle();
            }
        }
        JSONObject report=new JSONObject().put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
            .put("adaptive",true).put("transparentColourBackground",true).put("monochromePresent",true).put("renderer","Android AdaptiveIconDrawable");
        Files.write(new File(directory,"icons-probe.json").toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    private AccessibilityNodeInfo feedbackCheckbox() {
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until) {
            AccessibilityNodeInfo node=find("Joindre les données de diagnostic",false);
            while(node!=null) { if(node.refresh()&&node.isCheckable())return node;node=node.getParent(); }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing checkable diagnostic row");
    }
    /** Verify the real R8 dialog and outgoing URL while intercepting every browser intent. */
    private void feedback() throws Exception {
        String[] stores={"hamigo","hamigo_social","hamigo_secure","hamigo_diagnostics"};
        java.util.Map<String,java.util.Map<String,?>> saved=new java.util.LinkedHashMap<>();
        for(String file:stores)saved.put(file,new java.util.HashMap<>(context.getSharedPreferences(file,Context.MODE_PRIVATE).getAll()));
        java.util.List<Intent> outgoing=new java.util.concurrent.CopyOnWriteArrayList<>();
        ActivityMonitor monitor=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){
                if(Intent.ACTION_VIEW.equals(intent.getAction())){outgoing.add(new Intent(intent));return new ActivityResult(0,null);}
                return null;
            }
        };
        Activity activity=null;addMonitor(monitor);
        try {
            for(String file:stores)check(context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit(),"Clear emulator feedback fixture");
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false)
                .putBoolean("reminderEnabled",false).putBoolean("interactionSound",false).putBoolean("interactionHaptics",false)
                .putInt("hamigoTourStep",-1).putString("name","Release Fixture").commit(),"Prepare isolated feedback fixture");
            activity=open();waitText("HAMIGO",30000);click("Moi",false);click("Réglages",true);
            reveal("Contacter / faire un retour",false);String before=learningProgress();
            click("Contacter / faire un retour",false);waitText("Faire un retour",10000);
            check(feedbackCheckbox().isChecked(),"Diagnostics checked by default");
            captureLearning("feedback-diagnostics-default");
            click("Joindre les données de diagnostic",false);
            check(!feedbackCheckbox().isChecked(),"Opt-out checkbox changes");
            captureLearning("feedback-diagnostics-opt-out");click("Annuler",false);check(outgoing.isEmpty(),"Cancel never opens the browser");
            click("Contacter / faire un retour",false);waitText("Faire un retour",10000);
            check(feedbackCheckbox().isChecked(),"New report resets to the requested default");
            click("Joindre les données de diagnostic",false);click("Ouvrir le formulaire",false);
            check(outgoing.size()==1,"One opt-out intent intercepted");
            check("https://tally.so/r/9NqlpK".equals(outgoing.get(0).getDataString()),"Opt-out has no URL parameters at all");
            click("Contacter / faire un retour",false);waitText("Faire un retour",10000);click("Ouvrir le formulaire",false);
            check(outgoing.size()==2,"One default report intercepted");
            android.net.Uri uri=outgoing.get(1).getData();
            check("app".equals(uri.getQueryParameter("content"))&&"settings".equals(uri.getQueryParameter("page")),"Settings origin retained with diagnostics");
            check(uri.getQueryParameter("app_version")!=null&&uri.getQueryParameter("device_model")!=null,"Diagnostics present when checked");
            check(before.equals(learningProgress()),"Feedback does not change progression");
            JSONObject report=new JSONObject().put("signedRelease",true).put("checkedByDefault",true).put("cancelDoesNotOpenBrowser",true)
                .put("optOutUrlHasNoParameters",true).put("defaultIncludesContext",true).put("progressUnchanged",true).put("realBrowserOpened",false);
            File dir=new File(context.getExternalFilesDir(null),"feedback-release");dir.mkdirs();
            Files.write(new File(dir,"feedback-probe.json").toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));
        } finally {
            removeMonitor(monitor);Activity closing=resumedActivity!=null?resumedActivity:activity;
            if(closing!=null)runOnMainSync(closing::finishAndRemoveTask);waitForIdleSync();
            for(String file:stores)restoreLearningPreferences(file,saved.get(file));
        }
    }

    /** Asset contracts, native learning and persisted evidence; no app class is reflected or imported. */
    private void learning() throws Exception {
        // The spoken fixture tests the keyboard, without starting audio capture. Prepare its
        // runtime permission so the system prompt cannot cover the diagnostic app window.
        if(context.checkSelfPermission("android.permission.RECORD_AUDIO")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.RECORD_AUDIO");
        String[] stores={"hamigo","hamigo_social","hamigo_secure","hamigo_diagnostics"};
        java.util.Map<String,java.util.Map<String,?>> saved=new java.util.LinkedHashMap<>();
        for(String file:stores)saved.put(file,new java.util.HashMap<>(context.getSharedPreferences(file,Context.MODE_PRIVATE).getAll()));
        android.accessibilityservice.AccessibilityServiceInfo automation=getUiAutomation().getServiceInfo();
        int previousFlags=automation.flags;
        automation.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getUiAutomation().setServiceInfo(automation);
        Activity activity=null;
        try {
            for(String file:stores)check(context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit(),"Clear emulator fixture "+file);
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true)
                .putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false)
                .putBoolean("interactionHaptics",false).putInt("hamigoTourStep",-1).putString("name","Release Fixture").commit(),"Prepare isolated learning fixture");
            check(context.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE).edit().putBoolean("enabled",true).putBoolean("frozen",false).commit(),"Enable inspector without freezing the real fixture lesson");
            JSONObject curriculum=assetJson("curriculum.json"),bank=assetJson("course-questions.json"),skills=assetJson("skills.json");
            JSONArray chapters=curriculum.getJSONArray("chapters"),questions=bank.getJSONArray("questions");
            JSONObject welcome=null,hintLesson=null,spoken=null;
            int lessonCount=0;
            for(int c=0;c<chapters.length();c++) {
                JSONArray lessons=chapters.getJSONObject(c).getJSONArray("lessons");lessonCount+=lessons.length();
                for(int l=0;l<lessons.length();l++) {
                    JSONObject lesson=lessons.getJSONObject(l);
                    if(lesson.getString("id").equals("l-hamigo-bienvenue"))welcome=lesson;
                    if(hintLesson==null&&lesson.optBoolean("studyToolsHint"))hintLesson=lesson;
                }
            }
            for(int i=0;i<questions.length();i++)if(spoken==null&&questions.getJSONObject(i).optString("kind").equals("spoken"))spoken=questions.getJSONObject(i);
            check(lessonCount==173&&questions.length()==1231&&skills.getJSONArray("skills").length()==139,"Expected the reviewed lesson/question/skill assets");
            check(welcome!=null&&spoken!=null,"Welcome and spoken-answer definitions are packaged");
            JSONObject presentation=assetJson("lesson-presentation.json");
            check(presentation.length()==lessonCount,"Every introduction has its editorial presentation");
            check(context.getAssets().list("exam1").length==0,"Learning does not require an embedded Exam1 bank");
            activity=open();waitText("HAMIGO",30000);waitText("Commencer le cours",15000);
            String untouched=learningProgress();
            captureLearning("learning-path-loaded");
            click("Commencer le cours",false);waitText(welcome.getString("title"),10000);waitText("À toi de jouer",10000);
            check(find("Ouvrir la calculatrice",true)==null&&find("Ouvrir le brouillon",true)==null,"Introductions are reading pages without floating tools");
            check(untouched.equals(learningProgress()),"Reading the introduction must not award XP or schedule reviews");
            captureLearning("learning-welcome-introduction");
            click("À toi de jouer",false);
            JSONArray welcomeIds=welcome.getJSONArray("questionIds");
            java.util.Set<String> answered=new java.util.HashSet<>();boolean answeredText=false,answeredChoice=false;
            for(int n=0;n<welcomeIds.length();n++) {
                JSONObject current=visibleAuthoredQuestion(questions,welcomeIds,answered);
                String id=current.getString("id"),kind=current.optString("kind","choice");
                if(kind.equals("text")) {
                    setText(current.getJSONArray("acceptedAnswers").getString(0));hideLearningKeyboard();answeredText=true;
                    captureLearning("learning-welcome-written-answer");
                } else {
                    check(kind.equals("choice")||kind.equals("truefalse"),"Welcome remains a concise choice/text smoke test");
                    String answer=current.getJSONArray("choices").getString(current.optInt("answer",0));reveal(answer,false);click(answer,false);answeredChoice=true;
                }
                reveal("Vérifier",false);click("Vérifier",false);waitText("Continuer",10000);
                JSONObject state=new JSONObject(learningProgress());
                check(state.getJSONObject("reviews").has(id),"The native answer persists its published UUID");
                check(state.optInt("answers")==n+1&&state.optInt("correct")==n+1,"The real welcome answer must be correct, not merely clickable");
                answered.add(id);click("Continuer",false);
            }
            waitText("Leçon validée !",10000);
            JSONObject completed=new JSONObject(learningProgress());
            check(answeredText&&answeredChoice,"Both real choice and free-text widgets were exercised");
            check(completed.getJSONArray("completed").toString().contains("l-hamigo-bienvenue"),"The actual welcome lesson is validated");
            check(completed.optInt("xp")==welcomeIds.length()*3+6,"Exactly one ordinary answer award and one lesson completion award");
            JSONObject skillRecords=completed.getJSONObject("skillReviews");check(skillRecords.length()>0&&completed.getInt("skillReviewsVersion")==2,"R8 persists compact skill evidence");
            String inspectedSkill=skillRecords.keys().next();JSONObject evidence=skillRecords.getJSONObject(inspectedSkill);
            check(evidence.getInt("attempts")>0&&evidence.getInt("repetitions")<=1,"One sitting cannot manufacture spaced mastery");
            captureLearning("learning-welcome-completed");reveal("Revenir au parcours",false);click("Revenir au parcours",false);
            String afterLesson=learningProgress();
            click("Mémo",false);reveal("Alphabet international",false);click("Alphabet international",false);
            learningTools("memo");reveal("Réviser avec les flashcards",false);captureLearning("learning-memo-tools");back();
            click("Défis",false);reveal("Voir l’historique et les statistiques",false);click("Voir l’historique et les statistiques",false);
            waitText("Tes examens blancs",10000);waitText("Ton premier examen ouvre l’historique.",10000);captureLearning("learning-empty-exam-history");back();
            click("Moi",false);reveal("Bilan d’apprentissage",false);
            check(find("Bilan d’apprentissage",false)!=null,"The learning summary is reachable in the optimized profile");
            captureLearning("learning-profile-summary");
            // These panels cannot share one viewport on a short landscape window.
            reveal("Ton parcours",false);reveal("Préparer le certificat",false);
            check(learningProgressBar("Préparer le certificat"),"The certificate progress bar has real range semantics");
            reveal("Explorer la radio",false);check(learningProgressBar("Explorer la radio"),"The exploration progress bar has real range semantics");captureLearning("learning-profile-two-progress-bars");
            reveal("Mieux retenir",false);check(find("Mieux retenir",false)!=null,"The tip remains reachable after the course progress panels");captureLearning("learning-profile-final-tip");
            check(afterLesson.equals(learningProgress()),"Memo tools, empty history and profile browsing preserve progression");
            click("Ouvrir les diagnostics",true);learningTab("Infos");reveal(lessonCount+" cours",false);
            check(find(lessonCount+" cours",false)!=null,"The optimized app actually parsed and exposed all lesson models");captureLearning("learning-runtime-content-count");
            learningTab("Questions");
            int archived=bank.optJSONArray("archivedQuestions")==null?0:bank.getJSONArray("archivedQuestions").length();
            check(find("Hamigo · "+(questions.length()+archived),false)!=null,"The optimized catalog loaded the full native bank, including resolvable archives");
            learningTab("Compétences");setText(inspectedSkill);hideLearningKeyboard();
            JSONArray definitions=skills.getJSONArray("skills");String skillTitle=null;
            for(int i=0;i<definitions.length();i++)if(definitions.getJSONObject(i).getString("id").equals(inspectedSkill))skillTitle=definitions.getJSONObject(i).getString("title");
            check(skillTitle!=null,"Persisted skill belongs to the packaged catalog");reveal(skillTitle,false);
            reveal("Tentatives ≥ "+evidence.getInt("attempts")+" · justes ≥ "+evidence.getInt("successes"),false);
            if(inspectedSkill.equals("hamigo-tools"))check(find("Hors SRS · utilisation de Hamigo",false)!=null,"Onboarding skill is explicitly excluded from SRS");
            captureLearning("learning-real-skill-inspector");
            boolean hintChecked=false;
            if(hintLesson!=null) {
                learningTab("Intros");setText(hintLesson.getString("id"));hideLearningKeyboard();waitText(hintLesson.getString("id"),20000);click(hintLesson.getString("id"),false);
                reveal("Ouvrir l’introduction",false);click("Ouvrir l’introduction",false);
                check(find("Ouvrir la calculatrice",true)==null&&find("Ouvrir le brouillon",true)==null,"The diagnostic introduction uses the same reading layout");
                check(find("Dans Hamigo, les boutons calculatrice et brouillon",true)==null,"The introduction does not point at absent tools");captureLearning("learning-introduction-without-tools");hintChecked=true;
                click("Retour des diagnostics",true);click("Retour des diagnostics",true);
            }
            learningTab("Questions");setText(spoken.getString("id"));hideLearningKeyboard();waitText(spoken.getString("id"),20000);click(spoken.getString("id"),false);
            reveal("Tester cette question",false);click("Tester cette question",false);
            JSONObject effective=learningSessionJson();
            check(effective.getString("id").equals(spoken.getString("id"))&&effective.getString("kind").equals("spoken"),"The actual spoken widget was created in the isolated inspector");
            check(find("Maintenir pour donner ma réponse au microphone",true)!=null || find("Appuyer pour donner ma réponse au microphone",true)!=null || find("Microphone indisponible. Le clavier reste disponible.",true)!=null || find("Microphone non autorisé. Le clavier reste disponible.",true)!=null,"The native microphone control and its actual system availability are exposed in Diagnostics");
            click("Écrire ma réponse au clavier",true);waitLearningIme();setText(effective.getJSONArray("acceptedAnswers").getString(0));captureLearning("learning-spoken-keyboard-fallback");hideLearningKeyboard();
            reveal("Vérifier",false);click("Vérifier",false);waitText("Continuer",10000);
            reveal("Signal reçu !",false);check(find("Signal reçu !",false)!=null&&find("Une occasion de retenir",false)==null,"The fallback keyboard answer receives an actual correct verdict");
            click("Quitter la séance",true);check(afterLesson.equals(learningProgress()),"Diagnostic answers cannot alter real UUID, XP or skill state");
            JSONObject report=new JSONObject().put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
                .put("lessons",lessonCount).put("questions",questions.length()).put("skills",skills.getJSONArray("skills").length())
                .put("realWelcomeAnswers",answered.size()).put("welcomeXp",completed.getInt("xp"))
                .put("realSkillEvidence",skillRecords.length()).put("introductionPresentations",presentation.length()).put("introductionWithoutTools",true).put("memoTools",true).put("emptyHistory",true)
                .put("profileBars",2).put("profileFinalTip",true).put("spokenKeyboardWithIme",true).put("introductionHintRemoved",hintChecked)
                .put("exam1Required",false).put("microphoneAcousticRecording",false).put("diagnosticProgressUnchanged",true);
            File directory=new File(context.getExternalFilesDir(null),"learning-release");directory.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(directory,"learning-probe.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
        } catch(Exception|AssertionError failure) {
            try {captureLearning("learning-failure");}catch(Exception|AssertionError ignored){}
            throw failure;
        } finally {
            try {
                if(activity!=null){Activity finished=activity;runOnMainSync(finished::finish);waitForIdleSync();SystemClock.sleep(300);}
                for(String file:stores)restoreLearningPreferences(file,saved.get(file));
            } finally {
                automation.flags=previousFlags;getUiAutomation().setServiceInfo(automation);
            }
        }
    }
    private JSONObject assetJson(String path) throws Exception {
        try(java.io.InputStream input=context.getAssets().open(path);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[16384];for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private String learningProgress(){return context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress","{}");}
    private JSONObject visibleAuthoredQuestion(JSONArray bank,JSONArray ids,java.util.Set<String> answered) throws Exception {
        long until=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<until) {
            for(int i=0;i<bank.length();i++) {
                JSONObject q=bank.getJSONObject(i);String id=q.getString("id");
                if(!answered.contains(id)&&ids.toString().contains("\""+id+"\"")&&find(q.getString("prompt"),false)!=null)return q;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("No unplayed welcome question is visible");
    }
    private void learningTools(String page) throws Exception {
        click("Ouvrir la calculatrice",true);waitText("Calculatrice",10000);setText("2+3");click("=",false);waitText("5",10000);
        AccessibilityNodeInfo result=find("5",false),cursor=find("Curseur à gauche",true);Rect value=new Rect(),row=new Rect();result.getBoundsInScreen(value);cursor.getBoundsInScreen(row);
        check(value.centerY()>=row.top&&value.centerY()<=row.bottom,"The calculator result, not the 5 key, shows the computed value in its editor area");
        captureLearning("learning-"+page+"-calculator");click("Fermer la calculatrice",true);
        click("Ouvrir le brouillon",true);waitText("Brouillon",10000);
        check(find("Feuille de brouillon",true)!=null,"The native drawing view is constructed in the optimized release");
        click("Texte",true);setText("Essai release "+page);check(editable(freshRoot()).getText().toString().equals("Essai release "+page),"Scratchpad editing works");
        captureLearning("learning-"+page+"-scratchpad");click("Fermer le brouillon",true);
    }
    private JSONObject learningSessionJson() throws Exception {
        click("Inspecter le JSON de la question",true);waitText("Question · JSON",10000);captureLearning("learning-diagnostic-session-json");click("Copier",false);
        android.content.ClipboardManager clipboard=context.getSystemService(android.content.ClipboardManager.class);
        check(clipboard.hasPrimaryClip(),"Explicit JSON copy is available");JSONObject root=new JSONObject(clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        check(root.has("definition")&&root.has("session"),"The inspector exposes source and resolved data without logging them");click("Fermer",false);return root.getJSONObject("session");
    }
    private boolean learningProgressBar(String label) {
        AccessibilityNodeInfo node=find(label,true);if(node==null)return false;
        AccessibilityNodeInfo.RangeInfo range=node.getRangeInfo();return range!=null&&range.getCurrent()>=range.getMin()&&range.getCurrent()<=range.getMax();
    }
    private boolean learningImeVisible() {
        for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows())if(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
        return false;
    }
    private void waitLearningIme() {
        long until=SystemClock.uptimeMillis()+10000;while(!learningImeVisible()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
        check(learningImeVisible(),"The actual IME opens through the spoken-answer keyboard fallback");
    }
    private void hideLearningKeyboard(){if(learningImeVisible())back();}
    private void learningTab(String label) {
        for(int attempt=0;attempt<10;attempt++) {
            AccessibilityNodeInfo root=freshRoot(),tab=clickableLabel(root,label,false,true);
            if(tab!=null&&tab.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(350);return;}
            AccessibilityNodeInfo first=null;
            for(String candidate:new String[]{"Outils","Types","Questions","Compétences","Intros","TTS","Aperçus","Infos"}) {
                AccessibilityNodeInfo current=find(root,candidate,false,true);if(current!=null){first=current;break;}
            }
            check(first!=null,"The diagnostic tab row is visible");Rect bounds=new Rect();first.getBoundsInScreen(bounds);
            float width=context.getResources().getDisplayMetrics().widthPixels;float from=width*(attempt<5?.88f:.12f),to=width*(attempt<5?.12f:.88f);long at=SystemClock.uptimeMillis();
            for(int i=0;i<=16;i++) {
                MotionEvent event=MotionEvent.obtain(at,at+i*18,i==0?MotionEvent.ACTION_DOWN:i==16?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,from+(to-from)*i/16f,bounds.exactCenterY(),0);
                try {getUiAutomation().injectInputEvent(event,true);}finally {event.recycle();}
            }
            SystemClock.sleep(350);
        }
        throw new AssertionError("Cannot select diagnostic tab "+label);
    }
    // Inspect only attached, drawn app windows. A newly opened Dialog must first gain focus.
    @android.annotation.TargetApi(29)
    private android.view.View foregroundLearningWindow() {
        android.view.View top=null;
        for(android.view.View view:android.view.inspector.WindowInspector.getGlobalWindowViews())
            if(view.isAttachedToWindow()&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0)top=view;
        return top!=null&&top.hasWindowFocus()?top:null;
    }
    private void captureLearning(String name) throws Exception {
        waitForIdleSync();
        if(Build.VERSION.SDK_INT>=29) {
            long deadline=SystemClock.uptimeMillis()+15000;
            for(int i=0;i<2;i++) {
                boolean committed=false;
                for(int attempt=0;attempt<3&&!committed&&SystemClock.uptimeMillis()<deadline;attempt++) {
                    java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
                    android.view.View[] selected=new android.view.View[1];
                    while(selected[0]==null&&SystemClock.uptimeMillis()<deadline) {
                        runOnMainSync(()->{
                            android.view.View top=foregroundLearningWindow();
                            if(top!=null) {
                                selected[0]=top;
                                top.getViewTreeObserver().registerFrameCommitCallback(frame::countDown);
                                top.postInvalidateOnAnimation();
                            }
                        });
                        if(selected[0]==null)SystemClock.sleep(50);
                    }
                    // Assertions belong to the instrumentation thread, never the app main thread.
                    // This keeps a missing frame a test failure and lets the fixture restore in finally.
                    check(selected[0]!=null,"The foreground app/dialog window gains focus: "+name);
                    if(frame.await(Math.max(1,Math.min(3000,deadline-SystemClock.uptimeMillis())),java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        boolean[] unchanged=new boolean[1];
                        runOnMainSync(()->unchanged[0]=foregroundLearningWindow()==selected[0]);
                        committed=unchanged[0];
                    }
                }
                check(committed,"Wait for the foreground window frame, not the covered activity: "+name);
            }
        }
        capture(name);
    }
    private void restoreLearningPreferences(String file,java.util.Map<String,?> values) {
        android.content.SharedPreferences.Editor editor=context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear();
        for(java.util.Map.Entry<String,?> entry:values.entrySet()) {
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);
            else if(value instanceof java.util.Set<?>) {java.util.Set<String> strings=new java.util.HashSet<>();for(Object item:(java.util.Set<?>)value)strings.add((String)item);editor.putStringSet(key,strings);}
            else throw new AssertionError("Unsupported emulator preference type in "+file);
        }
        check(editor.commit(),"Restore emulator preferences for "+file);
    }
    private void narration() throws Exception {
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().clear().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false).commit();
        context.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE).edit().clear().putBoolean("enabled",true).putBoolean("frozen",true).commit();
        for(String file:new String[]{"hamigo_social","hamigo_secure"})context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit();
        Activity activity=open();waitText("HAMIGO",30000);
        String before=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress","");
        click("Ouvrir les diagnostics",true);learningTab("Intros");setText("½LI²");hideLearningKeyboard();waitText("c17-l01",20000);
        click("c17-l01",false);reveal("Texte exact envoyé au TTS",false);reveal("l’inductance fois l’intensité au carré",false);
        capture("release-introduction-narration");reveal("Écouter",false);click("Écouter",false);
        AudioManager audio=context.getSystemService(AudioManager.class);waitSpeech(audio);
        click("Arrêter",false);waitAudio(audio,false);check(speechFiles()==0,"Stopping the inspector clears synthesized files");
        click("Retour des diagnostics",true);learningTab("TTS");setText("E = ½CU²");hideLearningKeyboard();
        String expected="e égale un demi fois cé fois u au carré";reveal(expected,false);
        capture("release-speech-lab");
        click("Copier le texte TTS",true);
        android.content.ClipboardManager clipboard=context.getSystemService(android.content.ClipboardManager.class);
        check(clipboard.hasPrimaryClip()&&expected.contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()),"Copy exactly the text sent to speech");
        check(before.equals(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress","")),"Inspecting speech cannot change personal progress");
        runOnMainSync(activity::finish);
    }
    private void speech() throws Exception {
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().clear().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false).commit();
        for(String file:new String[]{"hamigo_social","hamigo_secure","hamigo_diagnostics"})context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit();
        Activity activity=open();waitText("HAMIGO",30000);
        reveal("Le service amateur",false);click("Le service amateur",false);waitText("À toi de jouer",10000);
        String before=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null);
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        click("Écouter le cours",true);waitSpeech(audio);capture("release-course-speaking");
        check(speechFiles()>0,"The production engine synthesizes actual files");
        click("Arrêter la lecture du cours",true);waitAudio(audio,false);
        check(speechFiles()==0,"Cancelling the course removes temporary speech files");
        click("À toi de jouer",false);
        click("Écouter la question",true);waitSpeech(audio);capture("release-question-speaking");
        click("Arrêter la lecture de la question",true);waitAudio(audio,false);
        check(speechFiles()==0,"Cancelling a question removes temporary speech files");
        check(java.util.Objects.equals(before,context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null)),"Narration gives no XP or SRS changes");
        runOnMainSync(activity::finish);
    }
    private int speechFiles() {
        File[] files=context.getCacheDir().listFiles((dir,name)->name.startsWith("hamigo-speech-")&&name.endsWith(".wav"));
        return files==null?0:files.length;
    }
    private void waitSpeech(AudioManager audio) {
        long started=SystemClock.uptimeMillis();
        // Same cold-engine budget as the debug audio tests, distinct from the receiver/stop budget.
        while(!audio.isMusicActive()&&SystemClock.uptimeMillis()-started<30000)SystemClock.sleep(20);
        check(audio.isMusicActive(),"French speech must actually play within 30 seconds");
        System.out.println("Speech playback after "+(SystemClock.uptimeMillis()-started)+" ms");
    }
    private void social() throws Exception {
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().clear().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false).putBoolean("interactionHaptics",false).commit();
        for(String file:new String[]{"hamigo_social","hamigo_secure","hamigo_diagnostics"})context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit();
        Activity activity=open();waitText("HAMIGO",30000);SystemClock.sleep(1000);
        String today=LocalDate.now().toString();JSONObject daily=new JSONObject().put(today,24);
        JSONObject progress=new JSONObject().put("schema",2).put("questionIdsVersion",1).put("xp",350).put("answers",30).put("correct",24).put("completed",new JSONArray()).put("dailyXp",daily).put("reviews",new JSONObject()).put("awarded",new JSONObject()).put("syncEvents",new JSONObject());
        progress.put("syncBase",new JSONObject(progress.toString()));
        JSONArray days=new JSONArray();for(int i=6;i>=0;i--)days.put(new JSONObject().put("day",LocalDate.now().minusDays(i).toString()).put("xp",60));
        JSONObject friend=new JSONObject().put("schema",2).put("name","Camille Démo").put("xp",800).put("streak",5).put("lessons",13).put("weeklyXp",420).put("updatedAt",java.time.Instant.now().toString()).put("dailyXp",days).put("dailyGoal",60).put("hourlyDay",today).put("hourlyXp",new JSONArray().put(new JSONObject().put("hour",9).put("xp",30)).put(new JSONObject().put("hour",12).put("xp",30))).put("hourlyComplete",true).put("zoneId",ZoneId.systemDefault().getId());
        friend.put("dailyGoal",120).put("appVersion","0.49+12345678").put("appVersionCode",49);
        JSONObject months=new JSONObject();LocalDate start=LocalDate.now().minusDays(59);
        for(java.time.YearMonth month=java.time.YearMonth.from(start);!month.isAfter(java.time.YearMonth.now());month=month.plusMonths(1)) {
            StringBuilder values=new StringBuilder();for(int d=1;d<=month.lengthOfMonth();d++){if(d>1)values.append(',');LocalDate day=month.atDay(d);values.append(day.isBefore(start)||day.isAfter(LocalDate.now())?0:60);}months.put(month.toString(),values.toString());
        }
        friend.put("activityHistory",new JSONObject().put("start",start.toString()).put("end",today).put("months",months));
        JSONArray friends=new JSONArray().put(new JSONObject().put("gist","https://gist.github.com/"+"1".repeat(32)).put("modifiedAt",System.currentTimeMillis()).put("progress",friend));
        // Seed after initial loading: the screen uses cached summaries, with no credentials.
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putString("name","Alex Démo").putInt("dailyGoal",30).putString("progress",progress.toString()).putString("friends",friends.toString()).commit();
        SystemClock.sleep(500);String before=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null);
        click("Équipe",false);reveal("Camille Démo",false);click("Camille Démo",false);
        waitText("60 / 120 XP",10000);capture("release-friend-goal-calendar");
        reveal("Voir le mois précédent",true);click("Voir le mois précédent",true);SystemClock.sleep(400);capture("release-friend-previous-month");
        reveal("Cette semaine",false);reveal("Voir la semaine précédente",true);click("Voir la semaine précédente",true);SystemClock.sleep(400);reveal("Historique hebdomadaire",false);capture("release-friend-previous-week");
        reveal("Aujourd’hui · heure par heure",false);capture("release-friend-hours");
        reveal("Retirer cet équipier",false);capture("release-friend-actions");click("Retirer cet équipier",false);waitText("Retirer Camille Démo ?",10000);click("Annuler",false);
        reveal("Hamigo 0.49+12345678",false);reveal("Code de version : 49",false);waitText("Code de version : 49",10000);capture("release-friend-app-version");back();
        click("Moi",false);reveal("24 / 60 XP",false);capture("release-daily-goal");
        click("Modifier l’objectif quotidien",true);waitText("Objectif quotidien",10000);SystemClock.sleep(1000);capture("release-goal-settings-focused");back();
        reveal("Retrouver mes révisions",false);click("Retrouver mes révisions",false);waitText("Lancer les révisions",10000);SystemClock.sleep(1000);capture("release-reviews-focus");
        calculator();
        check(java.util.Objects.equals(before,context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null)),"Social browsing preserves progression");
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putString("friends","[]").commit();
        runOnMainSync(()->activity.startActivity(new Intent().setClassName(context.getPackageName(),"com.malfreyt.alexandre.hamigo.MainActivity").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("hamigo_route","friends")));
        waitText("Sur la même fréquence",10000);
        AppWidgetManager manager=AppWidgetManager.getInstance(context);AppWidgetHost host=new AppWidgetHost(context,2849);int id=-1;
        getUiAutomation().adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET);
        try {
            id=host.allocateAppWidgetId();ComponentName provider=new ComponentName(context.getPackageName(),"com.malfreyt.alexandre.hamigo.platform.SocialWidgetProvider");
            check(manager.bindAppWidgetIdIfAllowed(id,provider),"Social provider binds in the optimized release");
            ComponentName configuration=manager.getAppWidgetInfo(id).configure;check(configuration!=null,"Social widget is configurable");
            startActivitySync(new Intent().setComponent(configuration).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));
            waitText("Comparer",10000);click("Aujourd’hui",false);reveal("Fond transparent",false);
            AccessibilityNodeInfo toggle=firstToggle(find("Fond transparent",false).getParent());
            check(toggle!=null&&toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Transparent switch is operable");
            SystemClock.sleep(300);capture("release-social-widget-settings");click("Appliquer",false);
            check(context.getSharedPreferences("hamigo_widgets",Context.MODE_PRIVATE).getBoolean("socialToday_"+id,false),"Widget day selection persists");
        } finally {if(id>=0)host.deleteAppWidgetId(id);host.deleteHost();getUiAutomation().dropShellPermissionIdentity();}
        runOnMainSync(activity::finish);
    }
    private void calculator() throws Exception {
        click("Mémo",false);click("Ouvrir la calculatrice",true);waitText("Calculatrice",10000);
        setText("2+sin(30)");
        Bundle selection=new Bundle();selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,6);selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,6);
        check(editable(freshRoot()).performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,selection),"Native selection works in R8");
        click("Curseur à gauche",true);
        check(editable(freshRoot()).getTextSelectionStart()==2,"Cursor skips a whole function");
        click("Curseur à droite",true);
        check(editable(freshRoot()).getTextSelectionStart()==6,"Cursor passes the function in the other direction");
        click("C",false);click("sin",false);click("Effacer",true);
        CharSequence empty=editable(freshRoot()).getText();
        check(empty==null||empty.length()==0,"Deleting an empty function also deletes its closing parenthesis");
        setText("20");click("=",false);waitText("20",10000);capture("release-calculator-native");
        click("Fermer la calculatrice",true);
    }
    private AccessibilityNodeInfo firstToggle(AccessibilityNodeInfo node) {
        if(node==null)return null;
        if(node.isCheckable()&&node.isClickable()&&node.isEnabled())return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=firstToggle(node.getChild(i));if(found!=null)return found;}
        return null;
    }
    private void catalog() throws Exception {
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit();
        context.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE).edit().putBoolean("enabled",true).putBoolean("frozen",true).commit();
        Activity activity=open();waitText("HAMIGO",30000);
        File current=new File(context.getNoBackupFilesDir(),"exam-bank/current.json");
        long until=SystemClock.uptimeMillis()+120000;
        while(!current.isFile()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(200);
        check(current.isFile(),"Official Exam1 bank available");SystemClock.sleep(1000);
        String before=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null);
        click("Ouvrir les diagnostics",true);click("Questions",false);click("Exam’1",false);
        setText("2008");waitText("20081",20000);click("20081",false);
        reveal("Toucher pour agrandir",false);capture("release-catalog-detail");
        String[] position=catalogPosition(freshRoot()).split(" / ");
        int page=Integer.parseInt(position[0]);String total=position[1];
        click("Toucher pour agrandir",false);
        check(find("Illustration originale",true)!=null,"Diagnostic enlargement opens the original viewer");
        capture("release-catalog-original");
        tap(8,context.getResources().getDisplayMetrics().heightPixels/2f);check(find("Illustration originale",true)==null,"Outside tap closes the image");
        click("Toucher pour agrandir",false);back();
        check(find("Illustration originale",true)==null&&find("20081",false)!=null,"Back closes only the image");
        swipe(true);waitText((page+1)+" / "+total,10000);
        capture("release-catalog-next");swipe(false);waitText(page+" / "+total,10000);
        click("Question suivante",true);waitText((page+1)+" / "+total,10000);
        click("Question précédente",true);waitText(page+" / "+total,10000);
        back();waitText("Tous les types",10000);
        String after=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null);
        check(java.util.Objects.equals(before,after),"Catalog browsing preserves real progression");
        runOnMainSync(activity::finish);
    }
    private String catalogPosition(AccessibilityNodeInfo node) {
        if(node==null)return null;
        CharSequence text=node.getText();
        if(node.isVisibleToUser()&&text!=null&&text.toString().matches("\\d+ / \\d+"))return text.toString();
        for(int i=0;i<node.getChildCount();i++){String found=catalogPosition(node.getChild(i));if(found!=null)return found;}
        return null;
    }
    private void diagnostics() throws Exception {
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",true).commit();
        context.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE).edit().putBoolean("enabled",true).putBoolean("frozen",true).commit();
        Activity activity=open();waitText("HAMIGO",30000);
        File current=new File(context.getNoBackupFilesDir(),"exam-bank/current.json");
        long bankUntil=SystemClock.uptimeMillis()+120000;
        while(!current.isFile()&&SystemClock.uptimeMillis()<bankUntil)SystemClock.sleep(200);
        check(current.isFile(),"Official Exam1 bank available for the catalog");SystemClock.sleep(1000);
        click("Ouvrir les diagnostics",true);click("Questions",false);
        setText("proc-band-147.0");waitText("proc-band-147.0",20000);
        click("proc-band-147.0",false);click("Tester cette question",false);
        reveal("+ 0,05",false);
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        waitAudio(audio,true);check(audio.isMusicActive(),"Receiver audio starts when the question opens");
        click("+ 0,05",false);waitAudio(audio,true);capture("release-diagnostic-frequency");
        SystemClock.sleep(3000);check(audio.isMusicActive(),"Receiver keeps playing without another gesture");
        click("Écouter la question",true);SystemClock.sleep(300);
        click("Arrêter la lecture",true);SystemClock.sleep(1500);waitAudio(audio,true);
        click("Couper les sons",true);waitAudio(audio,false);
        click("− 0,05",false);SystemClock.sleep(200);check(!audio.isMusicActive(),"Muted diagnostic receiver");
        click("Activer les sons",true);click("+ 0,05",false);waitAudio(audio,true);
        click("Quitter la séance",true);waitAudio(audio,false);
        click("Questions",false);
        click("Exam’1",false);setText("20081");
        long until=SystemClock.uptimeMillis()+120000;
        while(find("20081",false)==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(200);
        click("20081",false);reveal("Hors Parcours",false);capture("release-diagnostic-exam1");
        runOnMainSync(activity::finish);
    }
    private void waitAudio(AudioManager audio,boolean active) {
        long until=SystemClock.uptimeMillis()+5000;
        while(audio.isMusicActive()!=active&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);
        check(audio.isMusicActive()==active,"Expected media playback="+active);
    }
    private void setText(String text) {
        AccessibilityNodeInfo field=null;
        long until=SystemClock.uptimeMillis()+10000;
        // Compose publishes the new tab's editable semantics after the click action returns.
        // Fetch a fresh tree until that field exists, rather than reading a detached old tab.
        while(field==null&&SystemClock.uptimeMillis()<until) {
            field=editable(freshRoot());
            if(field==null)SystemClock.sleep(100);
        }
        check(field!=null,"Missing diagnostic search field after the tab settled");
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"Search input failed");SystemClock.sleep(500);
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node) {
        if(node==null||!node.refresh())return null;
        if(node.isVisibleToUser()&&(node.isEditable()||node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=editable(node.getChild(i));if(child!=null)return child;}
        return null;
    }
    private void flow() throws Exception {
        check(context.getAssets().list("exam1").length==0,"Bank must not be bundled");
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",false).putInt("onboardingStep",0).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit();
        Activity activity=open();waitText("Bienvenue dans Hamigo",30000);capture("release-onboarding");
        check(find("Banque Exam",false)==null,"No download UI during onboarding");
        setText("Release Fixture");click("Continuer",false);waitText("Un objectif à ton rythme",10000);
        check(context.getSharedPreferences("hamigo_exam_download",Context.MODE_PRIVATE).getBoolean("confirmed",false),"The first Continue confirms the background download");
        File bank=new File(context.getNoBackupFilesDir(),"exam-bank");File current=new File(bank,"current.json");
        long until=SystemClock.uptimeMillis()+180000;
        while(!current.isFile()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(200);
        check(current.isFile(),"Bank download did not complete during onboarding");
        JSONObject metadata=new JSONObject(new String(Files.readAllBytes(current.toPath()),StandardCharsets.UTF_8));
        File generation=new File(bank,metadata.getString("generation"));
        JSONObject raw=new JSONObject(new String(Files.readAllBytes(new File(generation,"questions.json").toPath()),StandardCharsets.UTF_8));
        check(raw.getJSONArray("questions").length()==raw.getInt("nbQuestions"),"Downloaded JSON is complete");
        check(new File(generation,"images.zip").length()>0,"Compressed archive retained");
        for(File f:generation.listFiles())check(!f.getName().endsWith(".png"),"No extracted PNG tree");
        check(find("Banque Exam",false)==null,"Onboarding stays transparent after the download");
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).commit();
        runOnMainSync(activity::finish);activity=open();waitText("HAMIGO",30000);
        click("Défis",false);waitText("Lancer un examen blanc",20000);capture("release-download-complete");
        click("Lancer un examen blanc",false);reveal("Commencer la réglementation",false);click("Commencer la réglementation",false);reveal("Toucher pour agrandir",false);waitText("Toucher pour agrandir",15000);capture("release-exam-image");
        click("Toucher pour agrandir",false);SystemClock.sleep(400);capture("release-original-overlay");
        Bitmap screen=getUiAutomation().takeScreenshot();int middle=screen.getHeight()/2;screen.recycle();tap(8,middle);
        click("Quitter la séance",true);click("Quitter",false);
        click("Mémo",false);reveal("Alphabet international",false);click("Alphabet international",false);
        reveal("Réviser avec les flashcards",false);capture("release-native-reference");back();
        click("Moi",false);click("Réglages",true);reveal("Banque Exam",false);capture("release-bank-settings");
        click("Vérifier les mises à jour",true);waitText("La banque Exam’1 est à jour.",30000);
        for(String name:new String[]{"ExamBankWorker","ProgressSyncWorker"}) {
            Class.forName("com.malfreyt.alexandre.hamigo.platform."+name).getConstructor(Context.class,androidxWorkerParameters());
        }
        // The framework, not app internals, provides the fresh disk-size measurement below.
    }
    private Class<?> androidxWorkerParameters() throws Exception {return Class.forName("androidx.work.WorkerParameters");}
    private AccessibilityNodeInfo freshRoot() {
        // Rich inline content changes virtual node IDs. Never act on a cached parent
        // from the preceding question or on its former enabled/disabled state.
        if(Build.VERSION.SDK_INT>=33)getUiAutomation().clearCache();
        return getUiAutomation().getRootInActiveWindow();
    }
    private AccessibilityNodeInfo find(String label,boolean description) {
        AccessibilityNodeInfo root=freshRoot();
        AccessibilityNodeInfo exact=find(root,label,description,true);
        return exact!=null?exact:find(root,label,description,false);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String label,boolean description,boolean exact) {
        if(node==null)return null;
        // Pager-derived titles can otherwise remain cached after the visible text changes.
        if(!node.refresh())return null;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),label,description,exact);if(found!=null)return found;}
        CharSequence text=description?node.getContentDescription():node.getText();
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        boolean onscreen=!bounds.isEmpty()&&bounds.intersects(0,0,context.getResources().getDisplayMetrics().widthPixels,context.getResources().getDisplayMetrics().heightPixels);
        return onscreen&&node.isVisibleToUser()&&!node.isEditable()&&text!=null&&((exact||label.equals("Quitter"))?text.toString().equals(label):text.toString().contains(label))?node:null;
    }
    private void waitText(String label,long timeout) {
        long until=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<until){if(find(label,false)!=null)return;SystemClock.sleep(100);}
        throw new AssertionError("Missing text: "+label);
    }
    private void click(String label,boolean description) {
        long until=SystemClock.uptimeMillis()+20000;
        while(SystemClock.uptimeMillis()<until) {
            AccessibilityNodeInfo root=freshRoot();
            AccessibilityNodeInfo node=clickableLabel(root,label,description,true);
            if(node==null)node=clickableLabel(root,label,description,false);
            if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(250);return;}
            SystemClock.sleep(100);
        }
        dumpClickFailure(label);
        throw new AssertionError("Cannot click: "+label);
    }
    private void dumpClickFailure(String label) {
        try {
            StringBuilder trace=new StringBuilder("Unreachable action: ").append(label).append('\n');
            appendClickNode(freshRoot(),trace,0);
            File file=new File(context.getExternalFilesDir(null),"learning-release/click-failure.txt");file.getParentFile().mkdirs();
            try(FileOutputStream out=new FileOutputStream(file)){out.write(trace.toString().getBytes(StandardCharsets.UTF_8));}
        } catch(Exception ignored) { }
    }
    private void appendClickNode(AccessibilityNodeInfo node,StringBuilder trace,int depth) {
        if(node==null||depth>18)return;
        node.refresh();Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        for(int i=0;i<depth;i++)trace.append("  ");
        trace.append(node.getClassName()).append(" text=").append(node.getText()).append(" enabled=").append(node.isEnabled())
            .append(" clickable=").append(node.isClickable()).append(" visible=").append(node.isVisibleToUser())
            .append(" bounds=").append(bounds).append(" actions=").append(node.getActionList()).append('\n');
        for(int i=0;i<node.getChildCount();i++)appendClickNode(node.getChild(i),trace,depth+1);
    }
    private AccessibilityNodeInfo clickableLabel(AccessibilityNodeInfo node,String label,boolean description,boolean exact) {
        if(node==null||!node.refresh())return null;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo match=clickableLabel(node.getChild(i),label,description,exact);
            if(match!=null)return match;
        }
        CharSequence text=description?node.getContentDescription():node.getText();
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        if(node.isEditable()||!node.isVisibleToUser()||bounds.isEmpty()||!bounds.intersects(0,0,context.getResources().getDisplayMetrics().widthPixels,context.getResources().getDisplayMetrics().heightPixels)||text==null)return null;
        if(!(exact?text.toString().equals(label):text.toString().contains(label)))return null;
        for(AccessibilityNodeInfo parent=node;parent!=null;parent=parent.getParent())if(parent.refresh()&&parent.isClickable()&&parent.isEnabled())return parent;
        return null;
    }
    private boolean scroll(AccessibilityNodeInfo node) {
        if(node==null)return false;
        for(int i=0;i<node.getChildCount();i++)if(scroll(node.getChild(i)))return true;
        if(!node.isScrollable())return false;
        if(node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN))return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId());
        // Revealing content is vertical; do not page the calendar/week horizontally.
        if(node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)||node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT))return false;
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }
    private void reveal(String text,boolean description) {
        for(int i=0;i<35;i++){if(find(text,description)!=null)return;scroll(freshRoot());SystemClock.sleep(200);}
        throw new AssertionError("Cannot reveal: "+text);
    }
    private void back() {sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);}
    private void tap(float x,float y) {
        long time=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);MotionEvent up=MotionEvent.obtain(time,time+70,MotionEvent.ACTION_UP,x,y,0);
        try {getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);}finally{down.recycle();up.recycle();}
        SystemClock.sleep(400);
    }
    private void swipe(boolean left) {
        Bitmap screen=getUiAutomation().takeScreenshot();float width=screen.getWidth(),y=screen.getHeight()*.60f;screen.recycle();
        float from=width*(left?.85f:.15f),to=width*(left?.15f:.85f);long time=SystemClock.uptimeMillis();
        for(int i=0;i<=20;i++) {
            MotionEvent event=MotionEvent.obtain(time,time+i*15,i==0?MotionEvent.ACTION_DOWN:i==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,from+(to-from)*i/20f,y,0);
            try {getUiAutomation().injectInputEvent(event,true);}finally {event.recycle();}
        }
        SystemClock.sleep(600);
    }
    private void capture(String name) throws Exception {
        File directory=new File(context.getExternalFilesDir(null),mode.equals("review")?"review-release":mode.equals("learning")?"learning-release":mode.equals("social")?"social-0.49-release":"storage-0.48-release");directory.mkdirs();
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot available");
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        if(requestedRotation!=null)check((width>height)==(Integer.parseInt(requestedRotation)%2==1),"Captured display matches requested rotation "+requestedRotation+": "+width+"x"+height);
    }
    private void recordStorage() throws Exception {
        StorageStats stats=context.getSystemService(StorageStatsManager.class).queryStatsForUid(StorageManager.UUID_DEFAULT,Process.myUid());
        JSONObject report=new JSONObject().put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName)
            .put("appBytes",stats.getAppBytes()).put("dataBytes",stats.getDataBytes()).put("cacheBytes",stats.getCacheBytes())
            .put("apkBytes",new File(context.getApplicationInfo().sourceDir).length());
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"storage-probe.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
