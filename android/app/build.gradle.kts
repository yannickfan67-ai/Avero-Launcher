plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val averoMicrosoftClientId = System.getenv("AVERO_MS_CLIENT_ID") ?: ""

android {
    namespace = "io.yannickfan.avero"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.yannickfan.avero"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "String",
            "MICROSOFT_CLIENT_ID",
            "\"" + averoMicrosoftClientId.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
