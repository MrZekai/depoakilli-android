import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
        versionCode = 55
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "SUPPORT_EMAIL", quotedBuildConfig(supportEmail))
    }

    // App language can be changed from Android's app-language settings. Keep
    // every declared locale in the installed base APK/AAB so a user can switch
    // languages without first downloading a Play language split.
    bundle {
        language {
            enableSplit = false
        }
    }

    androidResources {
        localeFilters += listOf("en", "tr", "es", "it", "fr", "de", "ru", "pt-rBR", "in", "ar")
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
            // External-test APK: release-like binary, stable QA signing.
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
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
        create("closedTest") {
            // Play closed testing exercises the real Play package and upload certificate.
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        // T3.2: unused strings, drawables and layouts fail the build.
        error += "UnusedResources"
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

    // Google Play Billing (2.0): Smart Cleaner Pro subscription and lifetime purchase.
    implementation(libs.android.billing.ktx)
    implementation(libs.google.play.review)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // The daily trash purge.
    implementation(libs.androidx.work.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val validateRelease by tasks.registering {
    group = "verification"
    description = "Fails release builds without a support address or the upload keystore."
    doLast {
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
    dependsOn(validateRelease)
}

val validateClosedTestConfiguration by tasks.registering {
    group = "verification"
    description = "Validates Play closed-test signing and support address."
    doLast {
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
