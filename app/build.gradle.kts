plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val sampleAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val sampleBannerId = "ca-app-pub-3940256099942544/6300978111"
val sampleInterstitialId = "ca-app-pub-3940256099942544/1033173712"

val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orElse(sampleAdMobAppId)
val admobBannerId = providers.gradleProperty("ADMOB_BANNER_ID").orElse(sampleBannerId)
val admobInterstitialId = providers.gradleProperty("ADMOB_INTERSTITIAL_ID").orElse(sampleInterstitialId)
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")

android {
    namespace = "com.mrzekai.depoakilli"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mrzekai.depoakilli"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId.get()

        buildConfigField("String", "ADMOB_BANNER_ID", "\"${admobBannerId.get()}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admobInterstitialId.get()}\"")
    }

    signingConfigs {
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xannotation-default-target=param-property"
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val validateReleaseAds by tasks.registering {
    group = "verification"
    description = "Fails release builds that still use Google sample AdMob IDs."
    doLast {
        val values = listOf(admobAppId.get(), admobBannerId.get(), admobInterstitialId.get())
        check(values.none { it.contains("3940256099942544") }) {
            "Release blocked: configure live ADMOB_APP_ID, ADMOB_BANNER_ID and ADMOB_INTERSTITIAL_ID."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseAds)
}
