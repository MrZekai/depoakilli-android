pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Fail fast, with an actionable message, when the Gradle daemon runs on a JVM
// this build cannot use.
//
// gradle/gradle-daemon-jvm.properties already pins the daemon to Java 17. This
// guard is the second line of defence for environments where that file is
// removed or ignored: Gradle 8.13 embeds Kotlin 2.0.21, whose bundled IntelliJ
// JavaVersion parser rejects Java 25+ ("IllegalArgumentException: 25.0.4") while
// compiling this very script, and AGP 8.13.2 does not support Java 16 or older.
run {
    val current = JavaVersion.current()
    val feature = current.majorVersion.toIntOrNull() ?: 0
    val javaHome = System.getProperty("java.home").orEmpty()
    check(feature in 17..24) {
        """
        Smart Cleaner must be built with JDK 17.

        Gradle is currently running on Java ${current.majorVersion} ($javaHome).
        Java 25 and newer cannot compile this build's Kotlin DSL scripts with
        Gradle 8.13, and Java 16 and older cannot run AGP 8.13.2.

        Fix: install a Java 17 JDK (Temurin 17, or Android Studio's bundled JBR 17)
        and either point JAVA_HOME at it or let gradle/gradle-daemon-jvm.properties
        discover it. See docs/BUILD_ENVIRONMENT_JDK17.md.
        """.trimIndent()
    }
    if (feature != 17) {
        println(
            "Smart Cleaner: Gradle is running on Java ${current.majorVersion}; " +
                "the supported and CI-verified build JDK is 17.",
        )
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DepoAkilli"
include(":app")
