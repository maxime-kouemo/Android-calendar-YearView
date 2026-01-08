plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.mamboa.yearview.legacy"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testOptions.targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    publishing {
        singleVariant("release") {}
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.9.0"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.joda.time)
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.androidx.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

val currentGroupId = "com.mamboa.yearview"
val currentVersion = "1.0.0"

// Publishing configuration
afterEvaluate { // Using afterEvaluate is common for publishing Android components
    publishing {
        publications {
            create<MavenPublication>("release") { // It's good practice to explicitly name the publication type
                groupId = currentGroupId
                artifactId = "legacy"
                version = currentVersion

                // This tells Gradle to publish the outputs of the 'release' component
                // (typically the AAR file for an Android library)
                from(components["release"])
            }
        }

        repositories {
            maven {
                name = "JitPack"
                url = uri("https://jitpack.io")
                credentials {
                    username =
                        (project.findProperty("jitpackUsername") ?: System.getenv("jitpackUsername")
                        ?: "").toString()
                    password =
                        (project.findProperty("jitpackToken") ?: System.getenv("jitpackToken")
                        ?: "").toString()
                }
            }
        }
    }
}