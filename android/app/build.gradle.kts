import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val localConfig = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun quotedConfig(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "sg.foodbridge.navigator"
    compileSdk = 35
    defaultConfig {
        applicationId = "sg.foodbridge.navigator"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", quotedConfig(providers.environmentVariable("MAPBOX_ACCESS_TOKEN").orNull ?: localConfig.getProperty("mapboxAccessToken", localConfig.getProperty("mapbox.accessToken", ""))))
        buildConfigField("String", "MAPBOX_STYLE_URI", quotedConfig(localConfig.getProperty("mapboxStyleUri", "")))
        buildConfigField("String", "API_URL", "\"${providers.gradleProperty("apiUrl").getOrElse("http://10.0.2.2:8000")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    } }
}
dependencies {
    implementation("com.mapbox.maps:android-ndk27:11.32.0")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
