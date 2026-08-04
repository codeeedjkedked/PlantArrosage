pluginManagement {
    repositories {
        // Maven Central en premier : le module :core n'a besoin que de lui.
        mavenCentral()
        gradlePluginPortal()
        google {
            // Restreindre google() aux groupes qu'il est seul à héberger. Sans ce filtre,
            // une recherche de métadonnées pour un artefact kotlinx peut interroger le miroir
            // Google et faire échouer la résolution au lieu de passer au dépôt suivant.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "PlantArrosage"

include(":core")

// Le module :app exige l'Android Gradle Plugin et le SDK Android. Sur un environnement qui n'y a
// pas accès, `-PskipAndroid=true` permet de compiler et tester :core seul.
if (providers.gradleProperty("skipAndroid").orNull != "true") {
    include(":app")
}
