import java.util.Properties
plugins { id("com.android.application") version "9.0.1" }
val targetPackage=providers.gradleProperty("hamigoTargetPackage").getOrElse("com.malfreyt.alexandre.hamigo")
require(targetPackage in setOf("com.malfreyt.alexandre.hamigo","com.malfreyt.alexandre.hamigo.dev"))
android {
    namespace="com.malfreyt.alexandre.hamigo.releaseprobe"
    compileSdk=36
    defaultConfig {
        applicationId=namespace + if(targetPackage.endsWith(".dev")) ".dev" else ""
        minSdk=26;targetSdk=36;versionCode=1;versionName="1"
        manifestPlaceholders["hamigoTargetPackage"]=targetPackage
    }
    compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
    if(targetPackage.endsWith(".dev")) {
        buildTypes {release {signingConfig=signingConfigs.getByName("debug")}}
    } else {
        val credentials=Properties().apply {file("../../.tools/signing.properties").inputStream().use {load(it)}}
        signingConfigs.create("hamigo") {
            storeFile=file("../../.tools/hamigo-release.jks");keyAlias="hamigo"
            storePassword=credentials.getProperty("storePassword");keyPassword=credentials.getProperty("keyPassword")
        }
        buildTypes {release {signingConfig=signingConfigs.getByName("hamigo")}}
    }
}
tasks.withType<JavaCompile>().configureEach {options.encoding="UTF-8"}
