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
        // AndroidX runner, matching the androidx.test dependencies below. The old
        // `android.support.test` runner is not on the classpath at all.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // The sample app is not published, so a lint crash here must never be able to fail a
    // release build. `lintVitalAnalyzeRelease` (which runs as part of `assembleRelease`
    // for application modules) currently dies inside androidx.lifecycle's
    // NonNullableMutableLiveDataDetector, whose lint jar is compiled against a newer
    // Kotlin Analysis API than the lint bundled with AGP 8.7.3. The detector arrives
    // transitively via hilt-navigation-compose and crashes while its jar is being
    // migrated, so it cannot be avoided by changing this module's sources.
    //
    // `jitpack.yml` already keeps `:app` out of release builds; this is the second line
    // of defence for any other CI that runs `assemble` at the root.
    lint {
        checkReleaseBuilds = false
        disable += "NullSafeMutableLiveData"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Matches the 17 used by :core, :legacy and :compose. At 1.8 the Kotlin compiler
    // refuses to inline the libraries' Java 17 bytecode into this module.
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Project dependencies, not published coordinates.
    //
    // This module briefly depended on
    // `com.github.maxime-kouemo.Android-calendar-YearView:{legacy,compose}:1.0.1`,
    // which made the release circular: JitPack builds this repository, and the sample
    // app then tried to download the very artifacts that build was producing. It also
    // meant a fresh clone could not build the demo until a release already existed.
    //
    // `:core` is not declared here — both front-ends expose it with `api`, so it
    // arrives transitively, exactly as it does for a real consumer.
    implementation(project(":legacy"))
    implementation(project(":compose"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.support.appcompat.v7)
    implementation(libs.support.constraint.layout)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.ui)
    implementation(libs.com.google.android.material)
    implementation(libs.androidx.viewpager2)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}