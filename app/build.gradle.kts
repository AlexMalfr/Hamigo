import java.util.Properties
import java.io.File
import java.security.MessageDigest
import hamigo.version.HamigoVersion

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun git(vararg args: String): String {
    val process = ProcessBuilder(listOf("git", "-c", "safe.directory=${rootDir.invariantSeparatorsPath}") + args)
        .directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor() == 0) { output }
    return output
}
require(!providers.gradleProperty("hamigoVersionCommit").isPresent) {
    "hamigoVersionCommit belongs to the historical 0.x versioning. Use version.properties."
}
val versionProperties = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val channel = providers.gradleProperty("hamigoChannel").getOrElse("dev")
val revision = runCatching {git("rev-parse", "HEAD")}.getOrDefault("local").ifEmpty { "local" }
val gitStatus = runCatching {git("status", "--porcelain")}.getOrNull()
val dirty = gitStatus == null || gitStatus.isNotEmpty()
val appVersion = HamigoVersion.create(versionProperties.getProperty("version"),
    versionProperties.getProperty("releaseOrdinal").toInt(),
    providers.gradleProperty("hamigoBuildNumber").getOrElse(versionProperties.getProperty("buildNumber")).toInt(),
    channel, versionProperties.getProperty("prereleaseNumber").toInt(), revision.take(8), dirty)
val isDev = !appVersion.isPublic
val appId = "com.malfreyt.alexandre.hamigo" + if (isDev) ".dev" else ""
val legacyLinkPath = if(isDev) "/hamigo-dev/" else "/hamigo/"
val linkPath = if(isDev) "/projects/Hamigo-dev/" else "/projects/Hamigo/"
val linkScheme = if(isDev) "hamigo-dev" else "hamigo"

// Native GitHub OAuth uses PKCE. GitHub still requires this app-wide identifier at exchange;
// it is extractible from the APK, never a user token, and is kept out of version control.
val oauthFile = rootProject.file(if(isDev) ".tools/oauth.dev.properties" else ".tools/oauth.properties")
val oauthProperties = Properties().apply {
    if (oauthFile.exists()) oauthFile.inputStream().use { load(it) }
}
val gitHubClientSecret = oauthProperties.getProperty("githubClientSecret", "").trim()
val gitHubClientId = if(isDev) oauthProperties.getProperty("githubClientId", "").trim() else "Ov23lihnAxjWv1FWyk5D"
require(gitHubClientId.isEmpty() || gitHubClientId.matches(Regex("[A-Za-z0-9._-]{4,100}"))) { "Invalid GitHub client ID." }
require(gitHubClientSecret.isEmpty() || gitHubClientSecret.matches(Regex("[A-Za-z0-9_\\-]{32,128}"))) {
    "Invalid GitHub application credential in .tools/oauth.properties."
}

// Windows builds live outside OneDrive, with a distinct directory for each checkout.
val buildRootOverride=providers.gradleProperty("hamigoBuildRoot").orNull
if(buildRootOverride!=null) layout.buildDirectory.set(rootProject.file("$buildRootOverride/app"))
else if(System.getProperty("os.name").startsWith("Windows",ignoreCase=true)) {
    val checkout=MessageDigest.getInstance("SHA-256").digest(rootDir.canonicalPath.toByteArray()).joinToString(""){"%02x".format(it)}.take(12)
    layout.buildDirectory.set(File(System.getenv("LOCALAPPDATA"),"Hamigo/build/$checkout/app"))
}
tasks.register("printBuildDirectory"){doLast{println(layout.buildDirectory.get().asFile)}}

// Ship the legal texts with the APK so they remain readable without a browser or network.
val licenseAssets=layout.buildDirectory.dir("generated/hamigo-licenses")
val prepareLicenseAssets=tasks.register<Sync>("prepareLicenseAssets") {
    into(licenseAssets.map {it.dir("licenses")})
    from(rootProject.file("LICENSE"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("LICENSES/README.md")){rename {"PERIMETERS.md"}}
    from(rootProject.file("LICENSES")){include("*.txt")}
}

android {
    namespace = "com.malfreyt.alexandre.hamigo"
    compileSdk = 36
    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.code()
        versionName = appVersion.name()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_CLIENT_SECRET", "\"$gitHubClientSecret\"")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$gitHubClientId\"")
        buildConfigField("String", "GITHUB_CALLBACK", "\"https://alexandre.malfre.yt${linkPath}oauth/\"")
        buildConfigField("String", "INVITE_BASE", "\"https://alexandre.malfre.yt${linkPath}rejoindre.html\"")
        buildConfigField("String", "LINK_SCHEME", "\"$linkScheme\"")
        buildConfigField("String", "SOURCE_REVISION", "\"$revision\"")
        buildConfigField("String", "CHANNEL", "\"$channel\"")
        manifestPlaceholders["hamigoLabel"] = if(isDev) "Hamigo Dev" else "Hamigo"
        manifestPlaceholders["hamigoLinkPath"] = linkPath
        manifestPlaceholders["hamigoLegacyLinkPath"] = legacyLinkPath
        manifestPlaceholders["hamigoScheme"] = linkScheme
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").assets.directories.add(licenseAssets.get().asFile.absolutePath)
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if(isDev) signingConfig=signingConfigs.getByName("debug")
            else signingConfigs.findByName("localRelease")?.let { signingConfig = it }
        }
    }
}
// Public channels use the same optimized variant. Debug builds always belong to dev.
androidComponents.beforeVariants(androidComponents.selector().withBuildType("debug")) { it.enable = isDev }
val verifyPublicBuild = tasks.register("verifyPublicBuild") {
    doLast {
        if(appVersion.isPublic) {
            check(git("rev-parse", "--is-shallow-repository") == "false") { "Public builds require complete Git history." }
            check(!dirty) { "Public builds require a clean commit; use the dev channel while editing." }
            check(revision != "local") { "Public builds require a source commit." }
            check(android.signingConfigs.findByName("localRelease") != null) { "Configure the existing upload key before a public build." }
            check(gitHubClientSecret.isNotBlank()) { "Configure public OAuth before a public build." }
            // Preserve the existing delivery guard until the separate cleanup removes this migration.
            check(!rootProject.file("app/src/main/java/com/malfreyt/alexandre/hamigo/StreakMigration52.kt").exists()) {
                "Before 1.0: complete the separately scheduled StreakMigration52 cleanup."
            }
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyPublicBuild) }
tasks.named("preBuild"){dependsOn(prepareLicenseAssets)}
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
    implementation("org.commonmark:commonmark:0.31.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.31.0")
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
tasks.register("writeVersionMetadata") {
    val target = layout.buildDirectory.file("outputs/version.json")
    outputs.file(target)
    // Git state changes without an input file changing.
    outputs.upToDateWhen { false }
    doLast {
        target.get().asFile.apply { parentFile.mkdirs(); writeText("""
            {"versionName":"${appVersion.name()}","versionCode":${appVersion.code()},"channel":"$channel",
             "applicationId":"$appId","sourceRevision":"$revision","dirty":$dirty,
             "releaseOrdinal":${versionProperties.getProperty("releaseOrdinal")},"buildNumber":${appVersion.code()%10000}}
        """.trimIndent()) }
        println(target.get().asFile)
    }
}
