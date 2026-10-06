// AutoTap Greenfield Architecture — Settings Script (v34.0)
import java.net.HttpURLConnection
import java.net.URI

pluginManagement {
    val isPrimaryFast = try {
        val conn = java.net.URI("https://repo.maven.apache.org/maven2/").toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        val code = conn.responseCode
        conn.disconnect()
        code in 200..399
    } catch (_: Exception) {
        false
    }

    repositories {
        google()
        if (!isPrimaryFast) {
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val isPrimaryFast = try {
        val conn = java.net.URI("https://repo.maven.apache.org/maven2/").toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        val code = conn.responseCode
        conn.disconnect()
        code in 200..399
    } catch (_: Exception) {
        false
    }

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://nexus-external.rustore.ru/repository/maven-rustore-exposed/") }
        // Если оригинал Maven Central отвечает быстро (Cloudflare доступен) -> используем его первым
        // Если оригинал тормозит или блокируется (timeout > 1500ms) -> зеркало Aliyun идёт первым
        if (!isPrimaryFast) {
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        mavenCentral()
        if (isPrimaryFast) {
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
    }
}

rootProject.name = "AutoTap"
include(":app")


