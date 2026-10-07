// AutoTap Greenfield Architecture — Settings Script (v34.0)
import java.net.HttpURLConnection
import java.net.URI

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
        maven { url = uri("https://nexus-external.rustore.ru/repository/maven-rustore-exposed/") }
        mavenCentral()
    }
}

rootProject.name = "AutoTap"
include(":app")


