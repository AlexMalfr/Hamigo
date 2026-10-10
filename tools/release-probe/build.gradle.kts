import java.util.Properties
plugins { id("com.android.application") version "9.0.1" }
android {
    namespace="com.malfreyt.alexandre.hamigo.releaseprobe"
    compileSdk=36
    defaultConfig {applicationId=namespace;minSdk=26;targetSdk=36;versionCode=1;versionName="1"}
    compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
    val credentials=Properties().apply {file("../../.tools/signing.properties").inputStream().use {load(it)}}
    signingConfigs.create("hamigo") {
        storeFile=file("../../.tools/hamigo-release.jks");keyAlias="hamigo"
        storePassword=credentials.getProperty("storePassword");keyPassword=credentials.getProperty("keyPassword")
    }
    buildTypes {release {signingConfig=signingConfigs.getByName("hamigo")}}
}
tasks.withType<JavaCompile>().configureEach {options.encoding="UTF-8"}
