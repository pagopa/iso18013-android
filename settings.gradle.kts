import java.io.FileInputStream
import java.util.Properties

val localProperties = Properties()
val localPropFile = rootDir.listFiles().filter {
    it.name == "local.properties"
}[0]
localProperties.load(FileInputStream(localPropFile))
val mavenUrl: String? = localProperties["git_hub_pushing_url"] as? String
val gitHubUser: String? = localProperties["git_hub_user"] as? String
val gitHubToken: String? = localProperties["git_hub_token"] as? String
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
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (mavenUrl == null)
            return@repositories
        maven(mavenUrl) {
            credentials {
                username = gitHubUser ?: System.getenv("GITHUB_USER")
                password = gitHubToken ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
rootProject.name = "iso18013"
include(":app")
include(":proximity")
include(":cbor")
