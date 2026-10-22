import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

fun readLocalTelemetryUrl(): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    val props = Properties()
    file.inputStream().use { props.load(it) }
    return props.getProperty("telemetryUrl")
}

val telemetryUrl = providers.environmentVariable("TELEMETRY_URL").orNull
    ?: providers.gradleProperty("telemetryUrl").orNull
    ?: readLocalTelemetryUrl()
    ?: "ws://10.0.2.2:8080"

android {
    namespace = "dev.polyrics.comojuecam"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.polyrics.comojuecam"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
