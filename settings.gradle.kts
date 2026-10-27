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
        maven("https://jitpack.io")
        exclusiveContent {
            forRepository {
                ivy {
                    name = "blockadsTunnelReleases"
                    url = uri("https://github.com/pass-with-high-score/blockads-android/releases/download")
                    patternLayout {
                        artifact("tunnel-[revision]/[artifact]-[revision](-[classifier]).[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("app.pwhs", "tunnel") }
        }
    }
}

rootProject.name = "AdBlocker"
include(":app")
 