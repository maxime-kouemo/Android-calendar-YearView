plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

// `core` is the shared API surface of both the View and Compose front-ends, so an
// unintended signature change here breaks two published artifacts at once. The recorded
// ABI lives in `api/core.api`; `apiCheck` runs as part of `check`.
apiValidation {}

val currentGroupId = "com.mamboa.yearview"
val currentVersion = "1.0.0"

// `core` is an `api` dependency of both :legacy and :compose, so it appears in their
// published POMs. It must therefore be published under resolvable coordinates, and the
// project group/version must be set here so Gradle maps `project(":core")` correctly.
group = currentGroupId
version = currentVersion

android {
    namespace = "com.mamboa.yearview.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    publishing {
        singleVariant("release") {}
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        error += listOf(
            // A library manifest must never declare an <application> block: it merges
            // into every consuming app and silently overrides its backup policy.
            "AllowBackup",
            "StringFormatMatches",
            "StringFormatInvalid",
            "StringFormatCount"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(libs.joda.time)
    api(libs.kotlinx.datetime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.com.google.android.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Publishing configuration
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = currentGroupId
                artifactId = "core"
                version = currentVersion

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
