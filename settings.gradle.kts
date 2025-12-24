import java.net.URI

pluginManagement {
    includeBuild("build-magic")
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = URI.create("https://jitpack.io") }
    }
    versionCatalogs {
        create("magicLibs") {
            from(files("build-magic/magic.versions.toml"))
        }
    }
}

rootProject.name = "Subtitle Edit"
include(":app")
