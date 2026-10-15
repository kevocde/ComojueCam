plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.polyrics.mondafono"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.polyrics.mondafono"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

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
