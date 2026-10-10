# WorkManager persists class names across app updates and constructs workers reflectively.
-keep class com.malfreyt.alexandre.hamigo.platform.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# ViewModelProvider uses the public no-argument constructor.
-keepclassmembers class com.malfreyt.alexandre.hamigo.AppModel { public <init>(); }
# Room creates WorkManager's generated database through its no-argument constructor.
-keep class androidx.work.impl.WorkDatabase_Impl { public <init>(); }
