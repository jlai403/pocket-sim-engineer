import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = providers.gradleProperty("buildNumber")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .orElse("local")
    .get()

val commitCount = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.get()
}.getOrDefault(1)

val updateChannel = providers.gradleProperty("updateChannel")
    .orElse(providers.environmentVariable("UPDATE_CHANNEL"))
    .orElse("preview")
    .get()

val gitSha = runCatching {
    providers.gradleProperty("gitSha")
        .orElse(providers.environmentVariable("GITHUB_SHA"))
        .map { it.take(7) }
        .orElse(
            providers.exec {
                commandLine("git", "rev-parse", "--short=7", "HEAD")
            }.standardOutput.asText.map { it.trim().ifEmpty { "dev" } },
        )
        .get()
}.getOrDefault("dev")

android {
    namespace = "io.github.jlai403.lapcraft"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jlai403.lapcraft"
        minSdk = 29
        targetSdk = 36
        versionCode = 1000 + commitCount
        versionName = "0.2.0"
        buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "UPDATE_CHANNEL", "\"$updateChannel\"")
    }

    // A checked-in debug key keeps GitHub Actions APKs updatable over each other.
    // Release builds must use their own key.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("ci/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")
}
