package com.malfreyt.alexandre.hamigo.releaseprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
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
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;

/** Platform-only black-box runner: independent of every class/API renamed by production R8. */
public final class ReleaseProbe extends Instrumentation {
    private String mode;
    private Context context;
    @Override public void onCreate(Bundle arguments) {super.onCreate(arguments);mode=arguments.getString("mode","storage");start();}
    @Override public void onStart() {
        Bundle output=new Bundle();
        try {
            check(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"),"Probe is reserved for the emulator");
            context=getTargetContext();
            check((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0,"Install the signed production release");
            if(mode.equals("flow"))flow();
            if(mode.equals("diagnostics"))diagnostics();
            recordStorage();output.putString("stream","OK: "+mode+" on the actual signed release\n");finish(Activity.RESULT_OK,output);
        } catch(Throwable e) {
            try {capture("release-failure");recordStorage();}catch(Exception ignored){}
            java.io.StringWriter text=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(text));
            output.putString("stream",text.toString());finish(Activity.RESULT_CANCELED,output);
        }
    }
    private Activity open() {return startActivitySync(new Intent().setClassName(context.getPackageName(),context.getPackageName()+".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
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
        SystemClock.sleep(1500);check(!audio.isMusicActive(),"No spontaneous receiver audio");
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
        AccessibilityNodeInfo field=editable(getUiAutomation().getRootInActiveWindow());
        check(field!=null,"Missing diagnostic search field");
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"Search input failed");SystemClock.sleep(500);
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node) {
        if(node==null)return null;
        if(node.isVisibleToUser()&&node.isEditable())return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=editable(node.getChild(i));if(child!=null)return child;}
        return null;
    }
    private void flow() throws Exception {
        check(context.getAssets().list("exam1").length==0,"Bank must not be bundled");
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",false).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit();
        Activity activity=open();waitText("Bienvenue dans Hamigo",30000);capture("release-onboarding");
        check(find("Banque Exam",false)==null,"No download UI during onboarding");
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
        click("Lancer un examen blanc",false);click("Commencer la réglementation",false);waitText("Toucher pour agrandir",15000);capture("release-exam-image");
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
    private AccessibilityNodeInfo find(String label,boolean description) {
        AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        AccessibilityNodeInfo exact=find(root,label,description,true);
        return exact!=null?exact:find(root,label,description,false);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String label,boolean description,boolean exact) {
        if(node==null)return null;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),label,description,exact);if(found!=null)return found;}
        CharSequence text=description?node.getContentDescription():node.getText();
        return node.isVisibleToUser()&&!node.isEditable()&&text!=null&&((exact||label.equals("Quitter"))?text.toString().equals(label):text.toString().contains(label))?node:null;
    }
    private void waitText(String label,long timeout) {
        long until=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<until){if(find(label,false)!=null)return;SystemClock.sleep(100);}
        throw new AssertionError("Missing text: "+label);
    }
    private void click(String label,boolean description) {
        long until=SystemClock.uptimeMillis()+20000;
        while(SystemClock.uptimeMillis()<until) {
            AccessibilityNodeInfo node=find(label,description);
            while(node!=null){if(node.isClickable()&&node.isEnabled()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(250);return;}node=node.getParent();}
            SystemClock.sleep(100);
        }
        throw new AssertionError("Cannot click: "+label);
    }
    private boolean scroll(AccessibilityNodeInfo node) {
        if(node==null)return false;
        for(int i=0;i<node.getChildCount();i++)if(scroll(node.getChild(i)))return true;
        return node.isScrollable()&&node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }
    private void reveal(String text,boolean description) {
        for(int i=0;i<35;i++){if(find(text,description)!=null)return;scroll(getUiAutomation().getRootInActiveWindow());SystemClock.sleep(200);}
        throw new AssertionError("Cannot reveal: "+text);
    }
    private void back() {sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);}
    private void tap(float x,float y) {
        long time=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);MotionEvent up=MotionEvent.obtain(time,time+70,MotionEvent.ACTION_UP,x,y,0);
        try {getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);}finally{down.recycle();up.recycle();}
        SystemClock.sleep(400);
    }
    private void capture(String name) throws Exception {
        File directory=new File(context.getExternalFilesDir(null),"storage-0.48-release");directory.mkdirs();
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot available");
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
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
