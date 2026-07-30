import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://libraries.minecraft.net/")
        maven("https://jitpack.io")
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    }
}

rootProject.name = "OmniPet"

include("omnipet-core", "omnipet-paper")
