import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val sampleAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val sampleBannerId = "ca-app-pub-3940256099942544/6300978111"
val sampleInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val sampleResultNativeVideoId = "ca-app-pub-3940256099942544/1044960115"

// AdMob ad-unit IDs are public identifiers embedded in the APK/AAB.
// Keep QA/debug on Google's official sample IDs and bind production only
// to Smart Cleaner's own live AdMob application/ad units.
val liveAdMobAppId = "ca-app-pub-1380972808968213~9043355268"
val liveBannerId = "ca-app-pub-1380972808968213/2118175647"
val liveInterstitialId = "ca-app-pub-1380972808968213/8492012303"
// Optional production Native unit. Empty means use live MREC fallback.
val liveResultNativeId = providers.environmentVariable("ADMOB_RESULT_NATIVE_ID")
    .orNull
    ?.trim()
    .orEmpty()
val sentryDsn = providers.environmentVariable("SENTRY_DSN")
    .orNull
    ?.trim()
    .orEmpty()
val supportEmail = providers.environmentVariable("SUPPORT_EMAIL")
    .orNull
    ?.trim()
    .orEmpty()

private fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val qaKeystore = rootProject.file("keystore/depoakilli-ci-qa.jks")

android {
    namespace = "com.mrzekai.depoakilli"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mrzekai.depoakilli"
        minSdk = 30
        targetSdk = 36
        versionCode = 39
        versionName = "0.5.18-closedtest1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Safe default for local/debug builds.
        manifestPlaceholders["ADMOB_APP_ID"] = sampleAdMobAppId

        buildConfigField("String", "ADMOB_BANNER_ID", "\"$sampleBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$sampleInterstitialId\"")
        buildConfigField("String", "ADMOB_RESULT_NATIVE_ID", "\"$sampleResultNativeVideoId\"")
        buildConfigField("String", "SENTRY_DSN", quotedBuildConfig(sentryDsn))
        buildConfigField("String", "SUPPORT_EMAIL", quotedBuildConfig(supportEmail))
    }

    signingConfigs {
        getByName("debug") {
            storeFile = qaKeystore
            storePassword = "depoakilli-qa"
            keyAlias = "depoakilliQa"
            keyPassword = "depoakilli-qa"
        }
        if (
            releaseKeystorePath.isPresent &&
            releaseKeystorePassword.isPresent &&
            releaseKeyAlias.isPresent &&
            releaseKeyPassword.isPresent
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = releaseKeystorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            // Local development surface. It must NOT share an applicationId with
            // the `qa` external-test build: both are signed with the same stable
            // QA certificate, so an identical id let the two replace each other
            // on a device and let a debuggable APK masquerade as the verified
            // slim QA APK in a manual install.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        create("qa") {
            // External-test APK: release-like binary, Google sample ads, stable QA signing.
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            manifestPlaceholders["ADMOB_APP_ID"] = sampleAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$sampleBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$sampleInterstitialId\"")
            buildConfigField("String", "ADMOB_RESULT_NATIVE_ID", "\"$sampleResultNativeVideoId\"")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            // Production AAB always uses Smart Cleaner's own AdMob IDs.
            manifestPlaceholders["ADMOB_APP_ID"] = liveAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$liveBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$liveInterstitialId\"")
            buildConfigField("String", "ADMOB_RESULT_NATIVE_ID", "\"$liveResultNativeId\"")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        create("closedTest") {
            // Play closed testing must exercise the real Play package and upload
            // certificate without generating live ad traffic.
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            manifestPlaceholders["ADMOB_APP_ID"] = sampleAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$sampleBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$sampleInterstitialId\"")
            buildConfigField("String", "ADMOB_RESULT_NATIVE_ID", "\"$sampleResultNativeVideoId\"")
            signingConfig = signingConfigs.findByName("release")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
        textReport = true
        textOutput = file("build/reports/lint-results-debug.txt")
        htmlReport = true
        xmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    // Enforced platform prevents transitive dependencies from upgrading this
    // API 36 build to Compose 1.12, which requires API 37 and AGP 9.x.
    val composeBom = enforcedPlatform(libs.androidx.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Activity Result APIs require Fragment 1.3.0 or newer when an older
    // Fragment arrives transitively through another Android dependency.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    // Crash-only diagnostics. Use Android core to avoid Session Replay/NDK
    // modules that Smart Cleaner does not use.
    implementation("io.sentry:sentry-android-core:8.53.0")

    implementation(libs.google.play.services.ads)
    implementation(libs.google.ump)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

val validateReleaseAds by tasks.registering {
    group = "verification"
    description = "Fails release builds that still use Google sample AdMob IDs."
    doLast {
        val values = listOf(
            liveAdMobAppId,
            liveBannerId,
            liveInterstitialId,
        )
        check(values.none { it.contains("3940256099942544") }) {
            "Release blocked: Google sample AdMob IDs cannot be used in production."
        }
        check(values.all { it.startsWith("ca-app-pub-1380972808968213") }) {
            "Release blocked: Smart Cleaner AdMob publisher IDs are inconsistent."
        }
        if (liveResultNativeId.isNotBlank()) {
            check(!liveResultNativeId.contains("3940256099942544")) {
                "Release blocked: Google sample Native ad ID cannot be used in production."
            }
            check(liveResultNativeId.startsWith("ca-app-pub-1380972808968213/")) {
                "Release blocked: Result Native ad unit must belong to Smart Cleaner."
            }
        }
        check(sentryDsn.isNotBlank()) {
            "Release blocked: configure SENTRY_DSN so closed-test/production crashes are observable."
        }
        check(supportEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            "Release blocked: configure a valid public SUPPORT_EMAIL."
        }
        check(
            releaseKeystorePath.isPresent &&
                releaseKeystorePassword.isPresent &&
                releaseKeyAlias.isPresent &&
                releaseKeyPassword.isPresent,
        ) {
            "Release blocked: configure the upload keystore environment variables."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseAds)
}

val validateClosedTestConfiguration by tasks.registering {
    group = "verification"
    description = "Validates Play closed-test signing, diagnostics, support and sample ads."
    doLast {
        val sampleIds = listOf(
            sampleAdMobAppId,
            sampleBannerId,
            sampleInterstitialId,
            sampleResultNativeVideoId,
        )
        check(sampleIds.all { it.contains("3940256099942544") }) {
            "Closed test blocked: every ad format must use an official Google sample ID."
        }
        check(sampleIds.none { it.contains("1380972808968213") }) {
            "Closed test blocked: production AdMob IDs cannot be used."
        }
        check(sentryDsn.isNotBlank()) {
            "Closed test blocked: configure SENTRY_DSN for crash observability."
        }
        check(supportEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            "Closed test blocked: configure a valid public SUPPORT_EMAIL."
        }
        check(
            releaseKeystorePath.isPresent &&
                releaseKeystorePassword.isPresent &&
                releaseKeyAlias.isPresent &&
                releaseKeyPassword.isPresent,
        ) {
            "Closed test blocked: configure the Play upload keystore environment variables."
        }
    }
}

tasks.matching { it.name == "preClosedTestBuild" }.configureEach {
    dependsOn(validateClosedTestConfiguration)
}
