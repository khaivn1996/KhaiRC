pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Khai RC"
include(":app")

include(":core:ir")
project(":core:ir").projectDir = file("core/ir")

include(":core:network")
project(":core:network").projectDir = file("core/network")

include(":feature:fan")
project(":feature:fan").projectDir = file("feature/fan")

include(":feature:wol")
project(":feature:wol").projectDir = file("feature/wol")
