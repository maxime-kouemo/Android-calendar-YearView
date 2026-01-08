pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Yearview"

include(":app")
include(":legacy")
include(":core")
include(":compose")

project(":legacy").projectDir = File("./legacy")
project(":core").projectDir = File("./core")
project(":compose").projectDir = File("./compose")
