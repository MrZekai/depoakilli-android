import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val sampleAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val sampleBannerId = "ca-app-pub-3940256099942544/6300978111"
val sampleInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val sampleAppOpenId = "ca-app-pub-3940256099942544/9257395921"

val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orElse(sampleAdMobAppId)
val admobBannerId = providers.gradleProperty("ADMOB_BANNER_ID").orElse(sampleBannerId)
val admobInterstitialId = providers.gradleProperty("ADMOB_INTERSTITIAL_ID").orElse(sampleInterstitialId)
val admobAppOpenId = providers.gradleProperty("ADMOB_APP_OPEN_ID").orElse(sampleAppOpenId)
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
        versionCode = 13
        versionName = "0.5.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId.get()

        buildConfigField("String", "ADMOB_BANNER_ID", "\"${admobBannerId.get()}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admobInterstitialId.get()}\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"${admobAppOpenId.get()}\"")
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
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

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
            admobAppId.get(),
            admobBannerId.get(),
            admobInterstitialId.get(),
            admobAppOpenId.get(),
        )
        check(values.none { it.contains("3940256099942544") }) {
            "Release blocked: configure all four live AdMob IDs."
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
