plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.naivetomcat.hrt_tracker.wear"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debugkeystore.jks")
            storePassword = "DEBUG1"
            keyAlias = "DEBUG"
            keyPassword = "DEBUG1"
        }
    }

    defaultConfig {
        applicationId = "cn.naivetomcat.hrt_tracker"
        minSdk = 30
        targetSdk = 36
        versionCode = 10060
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            applicationIdSuffix = ".release"
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)
    testImplementation(libs.junit)
}
