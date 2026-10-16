plugins {
    alias(libs.plugins.android.application)
}

val telemetryUrl = providers.environmentVariable("TELEMETRY_URL").orNull
    ?: providers.gradleProperty("telemetryUrl").orNull
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
