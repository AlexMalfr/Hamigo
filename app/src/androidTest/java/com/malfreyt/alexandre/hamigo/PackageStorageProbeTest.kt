package com.malfreyt.alexandre.hamigo

import android.app.usage.StorageStatsManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Platform-only probe, also works on an older release. Does not read private progression. */
class PackageStorageProbeTest {
    @Test fun recordOwnPackageStorage() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val stats=context.getSystemService(StorageStatsManager::class.java).queryStatsForUid(StorageManager.UUID_DEFAULT,Process.myUid())
        val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
        val result=JSONObject().put("version",version).put("appBytes",stats.appBytes).put("dataBytes",stats.dataBytes).put("cacheBytes",stats.cacheBytes)
            .put("apkBytes",File(context.applicationInfo.sourceDir).length())
        File(context.getExternalFilesDir(null),"storage-probe.json").writeText(result.toString(2))
    }
}
