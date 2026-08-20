pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
        maven("https://repo.essential.gg/repository/maven-releases/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "gg.essential.loom") {
                useModule("gg.essential:architectury-loom:${requested.version}")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "e4steam-retro"

listOf(
    "core",
    "forge-1.7.10", "forge-1.8.9", "forge-1.9.4",
    "forge-1.10.2", "forge-1.11.2", "forge-1.12.2", "forge-1.13.2",
    "forge-1.14.4", "forge-1.15.2", "forge-1.16.5",
    "fabric-1.14.4", "fabric-1.15.2", "fabric-1.16.5"
).forEach {
    include(it)
    project(":$it").projectDir = file(it)
}

val requestedProjects = gradle.startParameter.taskNames
        .mapNotNull { task ->
            if (!task.startsWith(":")) null
            else task.removePrefix(":").substringBefore(':').takeIf { it.isNotEmpty() }
        }
        .toSet()
if (requestedProjects.isNotEmpty()
        && "retroArtifacts" !in gradle.startParameter.taskNames
        && "auditRetroArtifacts" !in gradle.startParameter.taskNames) {
    rootProject.children
            .filter { it.name != "core" && it.name !in requestedProjects }
            .forEach { it.projectDir = file("inactive/${it.name}") }
}
