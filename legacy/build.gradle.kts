plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

// Records the public ABI in `api/legacy.api`. `apiCheck` runs as part of `check`, so an
// accidental change to a public signature fails the build instead of silently breaking
// consumers of the published AAR — exactly the class of problem the Phase 4 API rework
// introduced on purpose and must not repeat by accident.
apiValidation {
    // Test sources and the sample app are not part of the published surface.
    ignoredPackages.add("com.mamboa.yearview.legacy.test")
}

val currentGroupId = "com.mamboa.yearview"
val currentVersion = "1.0.0"

// Set on the project (not just on the MavenPublication) so that Gradle can map
// `project(":core")` onto real Maven coordinates when generating this module's POM.
// Without a group, the published POM would declare a dependency on `:core` with an
// empty groupId, which no consumer can resolve.
group = currentGroupId
version = currentVersion

android {
    namespace = "com.mamboa.yearview.legacy"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testOptions.targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // Libraries must not be minified: R8 would rename the public API and strip
            // the @Parcelize CREATOR fields, and consumers cannot un-obfuscate an AAR.
            // Shrinking is the consuming application's responsibility; we only ship the
            // keep rules it needs via `consumer-rules.pro`.
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

    // Guardrails for the exact classes of defect found in review. These are promoted
    // from warning to error rather than suppressed at the call site, because every one
    // of them shipped in a release at some point.
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Lint the library's own sources against its dependencies too, so a problem in
        // :core surfaces here rather than in a consumer's app.
        checkDependencies = true
        error += listOf(
            // A View that forgets `super.onSizeChanged`/`super.onDraw` breaks in ways
            // that only reproduce on some devices.
            "MissingSuperCall",
            // onTouchEvent without a matching performClick() makes the view unusable
            // with a screen reader or a switch device.
            "ClickableViewAccessibility",
            // A custom View published in a library must keep the (Context, AttributeSet)
            // constructor, or it cannot be inflated from XML at all.
            "ViewConstructor",
            // `%1$d` in a translatable string applies locale digit grouping, which is
            // how the year was once announced as "2 026" by TalkBack in French.
            "StringFormatMatches",
            "StringFormatInvalid",
            "StringFormatCount",
            // A library manifest must never declare an <application> block: it merges
            // into every consuming app and silently overrides its backup policy.
            "AllowBackup"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            // Compile interface methods with bodies as real Java default methods.
            //
            // Kotlin still defaults to `-Xjvm-default=disable`, which emits them into a
            // synthetic `DefaultImpls` class and leaves the interface method abstract in
            // the JVM signature. A Java consumer implementing MonthGestureListener would
            // therefore be forced to override *every* callback, including the ones this
            // library documents as optional.
            "-Xjvm-default=all"
        )
    }
}

dependencies {
    // `api` (not `implementation`) because `core` leaks all over this module's public
    // surface: YearView exposes CalendarDate, ICalendarDateTimeProvider, BackgroundShape,
    // FontType, TitleGravity and ImageSource, and LegacyBackgroundStyle implements
    // BackgroundItemStyle. With `implementation` those types are runtime-scoped only in
    // the published POM, so consumers cannot compile against the library at all.
    api(project(":core"))
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.androidx.core)
    // ExploreByTouchHelper: exposes each day cell as an accessibility virtual view.
    // `implementation`, not `api` — the helper is internal to this module.
    implementation(libs.androidx.customview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

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

// ─────────────────────────────────────────────────────────────────────────────
// Publication guardrail
// ─────────────────────────────────────────────────────────────────────────────

// Asserts the generated POM declares `com.mamboa.yearview:core` at compile scope, and
// hooks that assertion into `check`. See the script for why this needs guarding.
apply(from = rootProject.file("gradle/verify-published-pom.gradle.kts"))
