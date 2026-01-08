plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    compileSdk = 35
    namespace = "com.mamboa"

    defaultConfig {
        applicationId = "com.mamboa"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.9.0"
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":legacy"))
    implementation(project(":compose"))
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.support.appcompat.v7)
    implementation(libs.support.constraint.layout)
    implementation(libs.joda.time)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.ui)
    implementation(libs.com.google.android.material)
    implementation(libs.androidx.viewpager2)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}