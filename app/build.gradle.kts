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
// User-requested cleanup after five versions: block delivery until temporary 0.47 code is removed.
check(commitCount < 52 || !rootProject.file("app/src/main/java/com/malfreyt/alexandre/hamigo/CourseQuestionMigration47.kt").exists()) {
    "0.52: remove CourseQuestionMigration47, CourseQuestionAliases47 and their calls. Keep shipped UUIDs and the authoring registry."
}
// Explicitly requested revisions of an existing release keep its number; normal builds follow Git.
val versionCommit = providers.gradleProperty("hamigoVersionCommit").orNull?.let {
    it.toInt().also { value -> require(value > 0) { "hamigoVersionCommit must be positive." } }
} ?: commitCount
val revision = git("rev-parse", "--short=8", "HEAD").ifEmpty { "local" }
val dirty = git("status", "--porcelain").isNotEmpty()

// Native GitHub OAuth uses PKCE. GitHub still requires this app-wide identifier at exchange;
// it is extractible from the APK, never a user token, and is kept out of version control.
val oauthFile = rootProject.file(".tools/oauth.properties")
val oauthProperties = Properties().apply {
    if (oauthFile.exists()) oauthFile.inputStream().use { load(it) }
}
val gitHubClientSecret = oauthProperties.getProperty("githubClientSecret", "").trim()
require(gitHubClientSecret.isEmpty() || gitHubClientSecret.matches(Regex("[A-Za-z0-9_\\-]{32,128}"))) {
    "Invalid GitHub application credential in .tools/oauth.properties."
}

// Optional fresh output directory when Windows/OneDrive locks an earlier generated package.
providers.gradleProperty("hamigoBuildRoot").orNull?.let { buildRoot ->
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("$buildRoot/app"))
}

android {
    namespace = "com.malfreyt.alexandre.hamigo"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.malfreyt.alexandre.hamigo"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCommit
        versionName = "0.$versionCommit+$revision" + if (dirty) "-dev" else ""
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_CLIENT_SECRET", "\"$gitHubClientSecret\"")
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
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
tasks.register("printAppVersion") {
    doLast { println("Hamigo ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})") }
}
