plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

// Records the public ABI in `api/compose.api`; `apiCheck` runs as part of `check`.
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

// Required so Gradle can map the `api(project(":core"))` dependency below onto real
// Maven coordinates when generating this module's POM.
group = currentGroupId
version = currentVersion

android {
    namespace = "com.mamboa.yearview.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // Libraries must not be minified — same reasoning as :legacy.
            //
            // This module was shipping with minification on, so R8 ran over the
            // published AAR with an empty `consumer-rules.pro` and no keep rules for the
            // public surface: it renamed `YearView`, `YearViewState`, `MonthConfig`,
            // `DayConfig` and the `Saver` objects, and a consumer cannot un-obfuscate an
            // AAR. Shrinking is the consuming application's job; a library only ships
            // the keep rules that application needs.
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

        // Scoped rather than full lint, deliberately.
        //
        // The lint checks shipped inside the Compose BOM (2025.04) are compiled against
        // a newer Kotlin Analysis API than the lint bundled with AGP 8.7.3, so detectors
        // such as RememberInCompositionDetector and FrequentlyChangingValueDetector abort
        // the entire analysis with IncompatibleClassChangeError / NoSuchMethodError.
        // Disabling them one by one is whack-a-mole; restricting the run to the issues we
        // actually want to enforce skips the broken third-party detectors outright while
        // still failing the build on the defects this project has shipped before.
        //
        // Drop `checkOnly` and go back to a full run once AGP and the Compose BOM agree
        // on a lint API version.
        checkOnly += listOf(
            // A library manifest must never declare an <application> block: it merges
            // into every consuming app and silently overrides its backup policy.
            "AllowBackup",
            "StringFormatMatches",
            "StringFormatInvalid",
            "StringFormatCount"
        )
        error += listOf(
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

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

composeCompiler {
    // `core` is compiled without the Compose compiler, so its types (BackgroundShape,
    // ImageSource, TitleGravity, ICalendarDateTimeProvider, …) are inferred as unstable,
    // which in turn makes YearViewState unstable and YearView non-skippable.
    // Declaring them stable here lets Compose skip recomposition when nothing changed.
    stabilityConfigurationFiles.add(
        layout.projectDirectory.file("compose_stability_config.conf")
    )
}

dependencies {
    // `api` (not `implementation`) for everything that leaks into the public surface of
    // this library: YearView / YearViewState / MonthConfig / DayConfig / ComposeBackgroundStyle
    // expose ICalendarDateTimeProvider, BackgroundShape, ImageSource, TitleGravity,
    // TextStyle, Dp, Color and Modifier. With `implementation` these land in the published
    // POM as runtime-scoped only, and consumers cannot compile against the library at all.
    api(project(":core"))
    api(libs.androidx.ui.text.android)
    api(libs.androidx.ui.android)
    api(libs.foundation.android)
    api(libs.androidx.compose.ui.graphics)
    // Preview tooling is only needed by the @Preview harness in src/debug, which is never
    // published (see `publishing { singleVariant("release") }`).
    debugImplementation(libs.androidx.ui.tooling.preview.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Publishing configuration
afterEvaluate { // Using afterEvaluate is common for publishing Android components
    publishing {
        publications {
            create<MavenPublication>("release") { // It's good practice to explicitly name the publication type
                groupId = currentGroupId
                artifactId = "compose"
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

// Asserts the generated POM declares `com.mamboa.yearview:core` at compile scope, and
// hooks that assertion into `check`. See the script for why this needs guarding.
apply(from = rootProject.file("gradle/verify-published-pom.gradle.kts"))