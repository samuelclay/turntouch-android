import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

// Keep the upload key and passwords outside the checkout. CI can point at its own file.
val releaseSigningFile = file(providers.environmentVariable("TURNTOUCH_SIGNING_PROPERTIES")
    .orElse("${System.getProperty("user.home")}/.config/turntouch/android-release.properties").get())
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
android {
    namespace = "com.turntouch.android"
    compileSdk = 36
    defaultConfig { applicationId = "com.turntouch.android"; minSdk = 26; targetSdk = 36; versionCode = 3; versionName = "1.2"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        if (releaseSigningFile.isFile) create("upload") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")
    implementation("androidx.camera:camera-video:1.5.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.3")
}
