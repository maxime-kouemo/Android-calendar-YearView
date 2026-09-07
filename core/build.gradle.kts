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

// Published Maven coordinates, single-sourced in `gradle.properties`. See the comments
// there for why the group has to be the JitPack serving coordinate.
//
// A `-Pversion=` on the command line takes precedence, because that is how JitPack
// injects the tag being built; `VERSION_NAME` is the fallback for local builds.
val currentGroupId = providers.gradleProperty("GROUP_ID").get()
val currentVersion = project.version.toString()
    .takeUnless { it.isBlank() || it == "unspecified" }
    ?: providers.gradleProperty("VERSION_NAME").get()

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
        freeCompilerArgs += listOf(
            // Compile interface methods with bodies as real Java default methods —
            // the same flag, and the same reason, as :legacy.
            //
            // `ICalendarDateTimeProvider` is this library's public extension point and
            // deliberately gives some members bodies (`isWeekendDay(Int, Set<Int>)`,
            // for example) so an implementor only has to override what it cares about.
            // Kotlin 2.1 still defaults to `-Xjvm-default=disable`, which emits those
            // bodies into a synthetic `DefaultImpls` class and leaves the interface
            // method abstract in the JVM signature — so a Java consumer implementing
            // the provider would be forced to override every single member.
            //
            // The recorded ABI in `api/core.api` already describes them as non-abstract,
            // because the module was until now being compiled by a stray Kotlin 2.2.0-RC
            // that the parcelize plugin dragged onto the build classpath, and 2.2 turns
            // JVM default methods on by default. Setting the flag explicitly makes the
            // published ABI independent of the compiler version.
            "-Xjvm-default=all"
        )
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
