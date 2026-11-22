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
        val couiRepo = providers.gradleProperty("couiLocalRepo").orElse("../coui/build/repository/local")
        if (file(couiRepo.get()).exists()) {
            maven(file(couiRepo.get())) {
                content { includeGroup("io.github.suqi8.coui.kmp") }
            }
        }
        val couiSnapshotRepo = providers.gradleProperty("couiSnapshotRepo").orElse("../coui-local-repo")
        if (file(couiSnapshotRepo.get()).exists()) {
            maven(file(couiSnapshotRepo.get())) {
                content { includeGroup("io.github.suqi8.coui.kmp") }
            }
        }
        google()
        mavenCentral {
            content { excludeGroup("io.github.suqi8.coui.kmp") }
        }
        maven("https://api.xposed.info/")
    }
}
rootProject.name = "FluidCloudRedirect"
include(":app")
