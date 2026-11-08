plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.example.screenwindow"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.example.screenwindow"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    dependencies {\n        implementation("com.squareup.okhttp3:okhttp:4.12.0")\n    }\n\n    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
