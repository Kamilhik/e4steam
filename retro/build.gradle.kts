import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import java.util.zip.ZipFile

plugins {
    java
    id("xyz.wagyourtail.unimined") version "1.4.2-SNAPSHOT" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

group = "link.e4steam"
version = "0.3.0"

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://jitpack.io")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
}

val runtimeProjects = subprojects.filter { it.name != "core" }

configure(runtimeProjects) {
    val loader = name.substringBefore('-')
    val minecraftVersion = name.substringAfter('-')
    apply(plugin = "java")
    apply(plugin = "com.github.johnrengelman.shadow")

    val shadowBundle = configurations.create("shadowBundle")
    dependencies.add("implementation", project(":core"))
    dependencies.add(shadowBundle.name, project(":core"))
    dependencies.add("implementation", "com.code-disaster.steamworks4j:steamworks4j:1.10.0") {
        isTransitive = false
    }
    dependencies.add(shadowBundle.name, "com.code-disaster.steamworks4j:steamworks4j:1.10.0") {
        isTransitive = false
    }
    dependencies.add("compileOnly", "net.java.dev.jna:jna:5.10.0")
    dependencies.add("implementation", "com.code-disaster.steamworks4j:steamworks4j-server:1.10.0") {
        isTransitive = false
    }
    dependencies.add(shadowBundle.name, "com.code-disaster.steamworks4j:steamworks4j-server:1.10.0") {
        isTransitive = false
    }

    tasks.named<ShadowJar>("shadowJar") {
        configurations = listOf(shadowBundle)
        archiveClassifier.set("all")
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        exclude("META-INF/maven/**", "META-INF/versions/**", "META-INF/native-image/**")
        // e4steam supports only 64-bit desktop runtimes. Do not ship legacy
        // 32-bit or encrypted-app-ticket natives that this mod never loads.
        exclude(
            "steam_api.dll",
            "steamworks4j.dll",
            "steamworks4j-server.dll",
            "steamworks4j-encryptedappticket.dll",
            "steamworks4j-encryptedappticket64.dll",
            "libsteamworks4j-encryptedappticket.so",
            "libsteamworks4j-encryptedappticket.dylib"
        )
        mergeServiceFiles()
        from(rootProject.file("../LICENSE")) {
            into("META-INF")
            rename { "LICENSE-e4steam.txt" }
        }
        from(rootProject.file("../NOTICE")) {
            into("META-INF")
            rename { "NOTICE-e4steam.txt" }
        }
        from(rootProject.file("../THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    }

    tasks.named<ProcessResources>("processResources") {
        from(rootProject.file("runtime-template/e4steam-retro.properties")) {
            expand("minecraftVersion" to minecraftVersion)
        }
    }

    plugins.withId("xyz.wagyourtail.unimined") {
        afterEvaluate {
            tasks.named<RemapJarTask>("remapJar") {
                dependsOn(tasks.named("shadowJar"))
                asJar {
                    inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
                    archiveFileName.set("e4steam-${loader}-mc${minecraftVersion}-v${project.version}.jar")
                }
            }
            tasks.named("build") { dependsOn(tasks.named("remapJar")) }
        }
    }
}

tasks.register("retroArtifacts") {
    group = "build"
    description = "Builds every explicit e4steam retro loader/Minecraft artifact."
    dependsOn(runtimeProjects.map { "${it.path}:build" })
}

tasks.register("auditRetroArtifacts") {
    group = "verification"
    dependsOn("retroArtifacts")
    doLast {
        runtimeProjects.forEach { project ->
            val jars = project.layout.buildDirectory.dir("libs").get().asFile
                .listFiles()
                ?.filter { file ->
                    file.isFile && file.name == "e4steam-${if (project.name.startsWith("forge-")) "forge" else "fabric"}-mc${project.name.substringAfter('-')}-v${project.version}.jar"
                }
                .orEmpty()
            check(jars.size == 1) { "Missing or ambiguous retro artifact for ${project.name}" }
            jars.forEach { jar ->
                check(jar.name.contains(project.name.removePrefix("forge-").removePrefix("fabric-")))
                check(jar.name.contains(if (project.name.startsWith("forge-")) "forge" else "fabric"))
                check(jar.length() > 1_000_000L) { "Retro artifact is not the shaded runtime: ${jar.name}" }
                ZipFile(jar).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toSet()
                    check("link/e4steam/steam/SteamRuntime.class" in names)
                    check("link/e4steam/retro/RetroBootstrap.class" in names)
                    check("e4steam-retro.properties" in names)
                    listOf(
                        "META-INF/LICENSE-e4steam.txt",
                        "META-INF/NOTICE-e4steam.txt",
                        "META-INF/THIRD_PARTY_NOTICES.md"
                    ).forEach { legalResource ->
                        check(legalResource in names) {
                            "Missing legal resource $legalResource from ${jar.name}"
                        }
                    }
                    val mixinConfig = checkNotNull(zip.getEntry("e4steam.retro.mixins.json")) {
                        "Missing client Mixin configuration from ${jar.name}"
                    }
                    val mixinText = zip.getInputStream(mixinConfig).use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                    check(Regex("\\\"client\\\"\\s*:").containsMatchIn(mixinText)) {
                        "Retro runtime hooks must be scoped to the physical client: ${jar.name}"
                    }
                    check(!Regex("\\\"mixins\\\"\\s*:").containsMatchIn(mixinText)) {
                        "Retro runtime hooks must not load on a physical dedicated server: ${jar.name}"
                    }
                    val requiredNatives = setOf(
                        "steam_api64.dll", "steamworks4j64.dll", "steamworks4j-server64.dll",
                        "libsteam_api.so", "libsteamworks4j.so", "libsteamworks4j-server.so",
                        "libsteam_api.dylib", "libsteamworks4j.dylib", "libsteamworks4j-server.dylib"
                    )
                    requiredNatives.forEach { native ->
                        check(native in names) { "Missing $native from ${jar.name}" }
                    }
                    val packagedNatives = names.filter { name ->
                        name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")
                    }.toSet()
                    check(packagedNatives == requiredNatives) {
                        "Unexpected retro native set in ${jar.name}: ${packagedNatives - requiredNatives}"
                    }
                    if (project.name == "forge-1.7.10") {
                        check("META-INF/licenses/module-common/LICENSE" in names) {
                            "UniMixins module licenses were not preserved in ${jar.name}"
                        }
                    }
                    check(names.none { it.contains("e4mc", ignoreCase = true) })
                    check(names.none { it.contains("cloudflare", ignoreCase = true) || it.contains("quiclime", ignoreCase = true) })
                    if (project.name.startsWith("forge-")) {
                        val minecraftVersion = project.name.removePrefix("forge-")
                        val entrypoint = when (minecraftVersion) {
                            "1.6.4" -> "link/e4steam/retro/forge/E4steamForge164.class"
                            "1.7.10" -> "link/e4steam/retro/forge/E4steamForgeLegacy.class"
                            "1.8.9" -> "link/e4steam/retro/forge/E4steamForge.class"
                            "1.9.4", "1.10.2", "1.11.2", "1.12.2" ->
                                "link/e4steam/retro/forge/E4steamForge112.class"
                            "1.13.2" -> "link/e4steam/retro/forge/E4steamForge113.class"
                            "1.14.4", "1.15.2", "1.16.5" ->
                                "link/e4steam/retro/forge/E4steamForgeModern.class"
                            else -> error("No audited Forge entrypoint for $minecraftVersion")
                        }
                        val entry = checkNotNull(zip.getEntry(entrypoint)) {
                            "Missing Forge entrypoint $entrypoint from ${jar.name}"
                        }
                        val constantPool = zip.getInputStream(entry).use { input ->
                            input.readBytes().toString(Charsets.ISO_8859_1)
                        }
                        check("net/minecraft/client" !in constantPool) {
                            "Physical-server Forge entrypoint references Minecraft client classes: ${jar.name}"
                        }
                        check("link/e4steam/retro/RetroBootstrap" !in constantPool) {
                            "Physical-server Forge entrypoint eagerly starts the client runtime: ${jar.name}"
                        }
                        check("link/e4steam/steam/SteamRuntime" !in constantPool) {
                            "Physical-server Forge entrypoint eagerly references the client Steam runtime: ${jar.name}"
                        }
                    }
                }
            }
        }
    }
}
