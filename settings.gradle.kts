// AutoTap Greenfield Architecture — Settings Script (v34.0)
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
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        mavenCentral()
    }
}

rootProject.name = "AutoTap"
include(":app")
