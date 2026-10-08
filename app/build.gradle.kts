plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.deliveryday"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.deliveryday"
        minSdk = 26
        targetSdk = 34
        // Version comes from the release tag (vX.Y.Z → -Pdeliveryday.version=X.Y.Z in CI).
        // versionCode must always increase for Android to accept an update: X*10000 + Y*100 + Z.
        val version = providers.gradleProperty("deliveryday.version").orNull ?: "0.0.0"
        val (major, minor, patch) = version.split(".").map { it.toInt() }.let { it + List(3 - it.size) { 0 } }
        require(minor < 100 && patch < 100) { "Version $version: minor and patch must be < 100" }
        versionCode = maxOf(1, major * 10000 + minor * 100 + patch)
        versionName = version
    }
    // Release signing key is never part of the repository: its location and passwords come from
    // the maintainer's ~/.gradle/gradle.properties (deliveryday.storeFile, …). Without them,
    // only debug builds (signed with Android's local debug key) can be made.
    val releaseKey = providers.gradleProperty("deliveryday.storeFile").orNull
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = file(releaseKey)
            storePassword = providers.gradleProperty("deliveryday.storePassword").get()
            keyAlias = providers.gradleProperty("deliveryday.keyAlias").get()
            keyPassword = providers.gradleProperty("deliveryday.keyPassword").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKey != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    // org.json is only a stub in Android unit tests: use the real implementation.
    testImplementation("org.json:json:20260814")
}
