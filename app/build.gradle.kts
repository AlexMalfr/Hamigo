import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun git(vararg args: String): String = runCatching {
    val process = ProcessBuilder(listOf("git", "-c", "safe.directory=${rootDir.invariantSeparatorsPath}") + args)
        .directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor() == 0) { output }; output
}.getOrDefault("")
val commitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val revision = git("rev-parse", "--short=8", "HEAD").ifEmpty { "local" }
val dirty = git("status", "--porcelain").isNotEmpty()

android {
    namespace = "com.malfreyt.alexandre.hamigo"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.malfreyt.alexandre.hamigo"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount
        versionName = "0.$commitCount+$revision" + if (dirty) "-dev" else ""
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    val signingFile = rootProject.file(".tools/signing.properties")
    if (signingFile.exists()) {
        val secrets = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("localRelease") {
            storeFile = rootProject.file(".tools/hamigo-release.jks")
            storePassword = secrets.getProperty("storePassword")
            keyAlias = "hamigo"
            keyPassword = secrets.getProperty("keyPassword")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("localRelease")?.let { signingConfig = it }
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
tasks.register("printAppVersion") {
    doLast { println("Hamigo ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})") }
}
