import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import java.util.Properties
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

// Keep the 1.6.4 source port available for reference, but do not build or
// publish it as part of the 0.3.0 runtime matrix.
val runtimeProjects = subprojects.filter {
    it.name != "core" && it.name != "forge-1.6.4"
}

val legacyLangProjects = setOf(
    "forge-1.7.10", "forge-1.8.9", "forge-1.9.4",
    "forge-1.10.2", "forge-1.11.2", "forge-1.12.2"
)

val componentLangProjects = setOf(
    "forge-1.13.2", "forge-1.14.4", "forge-1.15.2", "forge-1.16.5",
    "fabric-1.14.4", "fabric-1.15.2", "fabric-1.16.5"
)

val legacyConnectProjects = setOf(
    "forge-1.7.10", "forge-1.8.9", "forge-1.9.4",
    "forge-1.10.2", "forge-1.11.2", "forge-1.12.2"
)

val modernRetroConnectProjects = setOf(
    "forge-1.14.4", "forge-1.15.2", "forge-1.16.5",
    "fabric-1.14.4", "fabric-1.15.2", "fabric-1.16.5"
)

// ModLauncher-era Forge loads Minecraft's JNA before regular mods. Bundling a
// second com.sun.jna copy then pairs newer Java classes with Forge's already
// loaded native dispatcher and fails during startup. These versions use the
// JNA runtime that Minecraft already places on the classpath instead.
val minecraftProvidedJnaProjects = setOf(
    "forge-1.13.2", "forge-1.14.4", "forge-1.15.2", "forge-1.16.5"
)

val resourcePackFormats = mapOf(
    "forge-1.13.2" to 4,
    "forge-1.14.4" to 4,
    "fabric-1.14.4" to 4,
    "forge-1.15.2" to 5,
    "fabric-1.15.2" to 5,
    "forge-1.16.5" to 6,
    "fabric-1.16.5" to 6
)

fun retroMinecraftBranch(minecraftVersion: String): String =
    if (minecraftVersion == "1.6.4") minecraftVersion
    else "${minecraftVersion.substringBeforeLast('.')}.x"

fun retroForgeMinecraftRange(minecraftBranch: String): String {
    if (minecraftBranch == "1.6.4") return "[1.6.4]"
    val parts = minecraftBranch.removeSuffix(".x").split('.')
    check(parts.size == 2) { "Invalid retro Minecraft branch: $minecraftBranch" }
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    return "[$major.$minor,$major.${minor + 1})"
}

configure(runtimeProjects) {
    val loader = name.substringBefore('-')
    val minecraftVersion = name.substringAfter('-')
    val minecraftBranch = retroMinecraftBranch(minecraftVersion)
    apply(plugin = "java")
    apply(plugin = "com.github.johnrengelman.shadow")

    extensions.configure<SourceSetContainer> {
        named("main") {
            when {
                project.name in legacyConnectProjects -> java.srcDir(
                    rootProject.file("adapters/minecraft-1.7-1.12-connect/src/main/java")
                )
                project.name in modernRetroConnectProjects -> java.srcDir(
                    rootProject.file("adapters/minecraft-1.14-1.16-connect/src/main/java")
                )
            }
        }
    }

    if (name in legacyLangProjects) {
        extensions.configure<SourceSetContainer> {
            named("main") {
                resources.srcDir(rootProject.file("adapters/forge-legacy/src/main/resources"))
            }
        }
    }

    if (loader == "forge") {
        val generatedSourceRoot = layout.buildDirectory.dir(
            "generated/sources/e4steamRetroMetadata/main/java")
        val generatedMetadata = generatedSourceRoot.map {
            it.file("link/e4steam/retro/RetroBuildMetadata.java")
        }
        val generateRetroMetadata = tasks.register("generateRetroMetadata") {
            inputs.property("minecraftBranch", minecraftBranch)
            inputs.property("acceptedForgeRange", retroForgeMinecraftRange(minecraftBranch))
            outputs.file(generatedMetadata)
            doLast {
                val output = generatedMetadata.get().asFile
                output.parentFile.mkdirs()
                output.writeText(
                    """package link.e4steam.retro;

/** Generated loader metadata for this branch-scoped retro artifact. */
public final class RetroBuildMetadata {
    public static final String MINECRAFT_BRANCH = "$minecraftBranch";
    public static final String ACCEPTED_FORGE_RANGE = "${retroForgeMinecraftRange(minecraftBranch)}";

    private RetroBuildMetadata() {
    }
}
""",
                    Charsets.UTF_8
                )
            }
        }
        extensions.configure<SourceSetContainer> {
            named("main") { java.srcDir(generatedSourceRoot) }
        }
        tasks.named<JavaCompile>("compileJava") {
            dependsOn(generateRetroMetadata)
        }
    }

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
    if (name !in minecraftProvidedJnaProjects) {
        dependencies.add(shadowBundle.name, "net.java.dev.jna:jna:5.10.0")
    }
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
        // JNA is required by Steam Networking Messages on old Minecraft, but
        // e4steam only supports the same 64-bit desktop targets as its Steam
        // native bundle.
        exclude(
            "com/sun/jna/win32-x86/**",
            "com/sun/jna/win32-aarch64/**",
            "com/sun/jna/linux-x86/**",
            "com/sun/jna/linux-arm/**",
            "com/sun/jna/linux-armel/**",
            "com/sun/jna/linux-aarch64/**",
            "com/sun/jna/linux-ppc/**",
            "com/sun/jna/linux-ppc64le/**",
            "com/sun/jna/linux-mips64el/**",
            "com/sun/jna/linux-s390x/**",
            "com/sun/jna/linux-riscv64/**",
            "com/sun/jna/sunos-*/**",
            "com/sun/jna/freebsd-*/**",
            "com/sun/jna/openbsd-*/**"
        )
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
        if (project.name in legacyLangProjects) {
            // Windows cannot materialize en_US.lang and en_us.lang (or the
            // Russian pair) in one directory. A ZIP can, so add the lowercase
            // aliases directly while creating the shaded runtime JAR.
            from(rootProject.file(
                "adapters/forge-legacy/src/main/resources/assets/e4steam/lang/en_US.lang")) {
                into("assets/e4steam/lang")
                rename { "en_us.lang" }
            }
            from(rootProject.file(
                "adapters/forge-legacy/src/main/resources/assets/e4steam/lang/ru_RU.lang")) {
                into("assets/e4steam/lang")
                rename { "ru_ru.lang" }
            }
        }
    }

    tasks.named<ProcessResources>("processResources") {
        inputs.property("minecraftBranch", minecraftBranch)
        from(rootProject.file("runtime-template/e4steam-retro.properties")) {
            expand(mapOf(
                "minecraftVersion" to minecraftVersion,
                "minecraftBranch" to minecraftBranch
            ))
        }
        if (project.name in componentLangProjects) {
            from(rootProject.file("../common/src/main/resources/assets")) {
                into("assets")
            }
            inputs.property("resourcePackFormat", checkNotNull(resourcePackFormats[project.name]))
            from(rootProject.file("runtime-template/pack.mcmeta")) {
                expand(mapOf(
                    "resourcePackFormat" to checkNotNull(resourcePackFormats[project.name])
                ))
            }
        }
    }

    plugins.withId("xyz.wagyourtail.unimined") {
        afterEvaluate {
            tasks.named<RemapJarTask>("remapJar") {
                dependsOn(tasks.named("shadowJar"))
                asJar {
                    inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
                    archiveFileName.set("e4steam-${loader}-mc${minecraftBranch}-v${project.version}.jar")
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
    dependsOn("retroArtifacts", ":core:test")
    doLast {
        runtimeProjects.forEach { project ->
            val minecraftVersion = project.name.substringAfter('-')
            val minecraftBranch = retroMinecraftBranch(minecraftVersion)
            val loader = if (project.name.startsWith("forge-")) "forge" else "fabric"
            val expectedName = "e4steam-${loader}-mc${minecraftBranch}-v${project.version}.jar"
            val jars = project.layout.buildDirectory.dir("libs").get().asFile
                .listFiles()
                ?.filter { file ->
                    file.isFile && file.name == expectedName
                }
                .orEmpty()
            check(jars.size == 1) { "Missing or ambiguous retro artifact for ${project.name}" }
            jars.forEach { jar ->
                check(jar.name.contains(minecraftBranch))
                check(jar.name.contains(loader))
                check(jar.length() > 1_000_000L) { "Retro artifact is not the shaded runtime: ${jar.name}" }
                ZipFile(jar).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toSet()
                    check("link/e4steam/steam/SteamRuntime.class" in names)
                    check("link/e4steam/retro/RetroBootstrap.class" in names)
                    val preloadEntry = checkNotNull(zip.getEntry("e4steam-retro-preload.txt")) {
                        "Missing the old-ModLauncher preload inventory from ${jar.name}"
                    }
                    val preloadClasses = zip.getInputStream(preloadEntry).bufferedReader(Charsets.UTF_8).use {
                        it.readLines().toSet()
                    }
                    setOf(
                        "link.e4steam.E4steamClient\$2",
                        "link.e4steam.E4steamClient\$4",
                        "link.e4steam.steam.SteamDedicatedClientBridge\$Pending",
                        "link.e4steam.steam.SteamClientBridge",
                        "link.e4steam.steam.SteamConnectionBridge"
                    ).forEach { requiredClass ->
                        check(requiredClass in preloadClasses) {
                            "Missing $requiredClass from the old-ModLauncher preload inventory in ${jar.name}"
                        }
                    }
                    if (project.name in minecraftProvidedJnaProjects) {
                        check(names.none { it.startsWith("com/sun/jna/") }) {
                            "${jar.name} must use Forge/Minecraft's already-loaded JNA runtime"
                        }
                    } else {
                        check("com/sun/jna/Pointer.class" in names) {
                            "Missing the Java 8 Steam transport JNA runtime from ${jar.name}"
                        }
                        check("com/sun/jna/Native.class" in names) {
                            "Missing the Java 8 Steam transport JNA loader from ${jar.name}"
                        }
                    }
                    check("e4steam-retro.properties" in names)
                    val retroProperties = Properties()
                    zip.getInputStream(checkNotNull(zip.getEntry("e4steam-retro.properties"))).use {
                        retroProperties.load(it)
                    }
                    check(retroProperties.getProperty("minecraftVersion") == minecraftVersion) {
                        "${jar.name} does not record its ${minecraftVersion} build baseline"
                    }
                    check(retroProperties.getProperty("minecraftBranch") == minecraftBranch) {
                        "${jar.name} does not record its ${minecraftBranch} compatibility branch"
                    }
                    val loaderMetadata = when (project.name) {
                        "forge-1.13.2" -> "META-INF/mods.toml" to "versionRange=\"[1.13,1.14)\""
                        "forge-1.14.4" -> "META-INF/mods.toml" to "versionRange=\"[1.14,1.15)\""
                        "forge-1.15.2" -> "META-INF/mods.toml" to "versionRange=\"[1.15,1.16)\""
                        "forge-1.16.5" -> "META-INF/mods.toml" to "versionRange=\"[1.16,1.17)\""
                        "fabric-1.14.4" -> "fabric.mod.json" to "\"minecraft\": \">=1.14 <1.15\""
                        "fabric-1.15.2" -> "fabric.mod.json" to "\"minecraft\": \">=1.15 <1.16\""
                        "fabric-1.16.5" -> "fabric.mod.json" to "\"minecraft\": \">=1.16 <1.17\""
                        else -> null
                    }
                    if (loaderMetadata != null) {
                        val metadataEntry = checkNotNull(zip.getEntry(loaderMetadata.first)) {
                            "Missing ${loaderMetadata.first} from ${jar.name}"
                        }
                        val metadataText = zip.getInputStream(metadataEntry).use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }
                        check(loaderMetadata.second in metadataText) {
                            "${jar.name} does not advertise its full ${minecraftBranch} loader range"
                        }
                        if (project.name.startsWith("fabric-")) {
                            check(Regex("\\\"fabric\\\"\\s*:").containsMatchIn(metadataText)) {
                                "${jar.name} must depend on the legacy Fabric API mod id 'fabric'"
                            }
                            check(!Regex("\\\"fabric-api\\\"\\s*:").containsMatchIn(metadataText)) {
                                "${jar.name} uses the modern Fabric API mod id on a retro runtime"
                            }
                        }
                    }
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
                    val requiredUiMixins = when (project.name) {
                        "forge-1.7.10", "forge-1.8.9", "forge-1.9.4",
                        "forge-1.10.2", "forge-1.11.2", "forge-1.12.2" -> setOf(
                            "GuiConnectingAddressMixin",
                            "NetHandlerLoginServerMixin"
                        )
                        "forge-1.13.2" -> setOf(
                            "GuiChatCommandSuggestions113Mixin",
                            "GuiScreenCommand113Mixin",
                            "NetworkSystemEndpointMixin"
                        )
                        "fabric-1.14.4" -> setOf(
                            "ConnectScreenAddressRetroMixin",
                            "ChatScreenCommandSuggestionsMixin",
                            "PauseScreenStringMixin",
                            "ScreenCommandMixin",
                            "ServerGamePacketListenerRetroMixin",
                            "ServerLoginPacketListenerRetroMixin",
                            "ShareToLanScreenStringMixin"
                        )
                        "forge-1.14.4" -> setOf(
                            "ChatScreenCommandSuggestionsMixin",
                            "PauseScreenStringMixin",
                            "ScreenCommandMixin",
                            "ShareToLanScreenStringMixin"
                        )
                        "forge-1.15.2", "fabric-1.15.2" -> setOf(
                            "ConnectScreenAddressRetroMixin",
                            "ChatScreenCommandSuggestionsMixin",
                            "PauseScreenStringMixin",
                            "ScreenCommandMixin",
                            "ServerGamePacketListenerRetroMixin",
                            "ServerLoginPacketListenerRetroMixin",
                            "ShareToLanScreenStringMixin"
                        )
                        "forge-1.16.5", "fabric-1.16.5" -> setOf(
                            "ConnectScreenAddressRetroMixin",
                            "ChatScreenCommandSuggestionsMixin",
                            "PauseScreenComponentMixin",
                            "ScreenCommandMixin",
                            "ServerGamePacketListenerRetroMixin",
                            "ServerLoginPacketListenerRetroMixin",
                            "ShareToLanScreenComponentMixin"
                        )
                        else -> emptySet()
                    }
                    requiredUiMixins.forEach { mixin ->
                        check("\"$mixin\"" in mixinText) {
                            "${jar.name} does not enable required UI/command hook $mixin"
                        }
                        check("link/e4steam/retro/mixin/$mixin.class" in names) {
                            "${jar.name} is missing required UI/command class $mixin"
                        }
                    }
                    val protocolEntry = checkNotNull(
                        zip.getEntry("link/e4steam/steam/SteamProtocol.class")
                    ) { "Missing Steam protocol from ${jar.name}" }
                    val protocolConstants = zip.getInputStream(protocolEntry).use { input ->
                        input.readBytes().toString(Charsets.ISO_8859_1)
                    }
                    check("encodeOpenAck" in protocolConstants &&
                            "encodeBridgeReady" in protocolConstants) {
                        "${jar.name} is missing the first-join Steam handshake"
                    }
                    if (project.name == "forge-1.13.2") {
                        listOf(
                            "META-INF/coremods.json",
                            "coremods/e4steam_login_113.js",
                            "link/e4steam/retro/forge/E4steamForge113Hooks.class"
                        ).forEach { coremodResource ->
                            check(coremodResource in names) {
                                "Missing Forge 1.13 authentication hook $coremodResource from ${jar.name}"
                            }
                        }
                        check("\"NetHandlerLoginServer113Mixin\"" !in mixinText) {
                            "Forge 1.13 login must be owned by its native coremod"
                        }
                        check("\"GuiConnectingAddress113Mixin\"" !in mixinText) {
                            "Forge 1.13 direct addresses must be owned by its native coremod"
                        }
                        val coremodScript = zip.getInputStream(
                            checkNotNull(zip.getEntry("coremods/e4steam_login_113.js"))
                        ).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        listOf(
                            "e4steam_forge_113_login",
                            "bindSteamIdentity",
                            "e4steam_forge_113_direct_address",
                            "acceptDirectSteamAddress"
                        ).forEach { hook ->
                            check(hook in coremodScript) {
                                "Forge 1.13 coremod is missing $hook"
                            }
                        }
                    }
                    if (project.name == "forge-1.14.4") {
                        listOf(
                            "META-INF/coremods.json",
                            "coremods/e4steam_login_114.js",
                            "link/e4steam/retro/forge/E4steamForge114Hooks.class"
                        ).forEach { coremodResource ->
                            check(coremodResource in names) {
                                "Missing Forge 1.14 authentication hook $coremodResource from ${jar.name}"
                            }
                        }
                        check("\"ServerLoginPacketListenerRetroMixin\"" !in mixinText) {
                            "Forge 1.14 login must be owned by its native coremod"
                        }
                        check("\"ConnectScreenAddressRetroMixin\"" !in mixinText) {
                            "Forge 1.14 direct addresses must be owned by its native coremod"
                        }
                        check("link/e4steam/retro/mixin/ServerLoginPacketListenerRetroMixin.class" !in names) {
                            "Forge 1.14 JAR must not package the conflicting login Mixin"
                        }
                        check("link/e4steam/retro/mixin/ConnectScreenAddressRetroMixin.class" !in names) {
                            "Forge 1.14 JAR must not package the conflicting address Mixin"
                        }
                        val coremodScript = zip.getInputStream(
                            checkNotNull(zip.getEntry("coremods/e4steam_login_114.js"))
                        ).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        listOf(
                            "e4steam_forge_114_direct_address",
                            "acceptDirectSteamAddress",
                            "e4steam_forge_114_keep_alive",
                            "keepAliveIntervalMillis"
                        ).forEach { hook ->
                            check(hook in coremodScript) {
                                "Forge 1.14 coremod is missing required hook $hook"
                            }
                        }
                    }

                    val clientAdapter = when (project.name) {
                        "forge-1.7.10" ->
                            "link/e4steam/retro/forge/E4steamForgeLegacyClient.class"
                        "forge-1.8.9" ->
                            "link/e4steam/retro/forge/E4steamForgeClient.class"
                        "forge-1.9.4", "forge-1.10.2", "forge-1.11.2", "forge-1.12.2" ->
                            "link/e4steam/retro/forge/E4steamForge112Client.class"
                        "forge-1.13.2" ->
                            "link/e4steam/retro/forge/E4steamForge113Client.class"
                        "forge-1.14.4" ->
                            "link/e4steam/retro/forge/E4steamForge114Client.class"
                        "forge-1.15.2", "forge-1.16.5" ->
                            "link/e4steam/retro/forge/E4steamForgeModernClient.class"
                        "fabric-1.14.4" ->
                            "link/e4steam/retro/fabric/E4steamFabric.class"
                        "fabric-1.15.2", "fabric-1.16.5" ->
                            "link/e4steam/retro/fabric/E4steamFabric.class"
                        else -> error("No audited client adapter for ${project.name}")
                    }
                    val clientAdapterEntry = checkNotNull(zip.getEntry(clientAdapter)) {
                        "Missing client adapter $clientAdapter from ${jar.name}"
                    }
                    val clipboardAdapter = when (project.name) {
                        "fabric-1.14.4", "fabric-1.15.2", "fabric-1.16.5" ->
                            "link/e4steam/retro/fabric/E4steamFabric.class"
                        else -> clientAdapter
                    }
                    val clipboardAdapterEntry = checkNotNull(zip.getEntry(clipboardAdapter)) {
                        "Missing clipboard adapter $clipboardAdapter from ${jar.name}"
                    }
                    val clipboardConstants = zip.getInputStream(clipboardAdapterEntry).use { input ->
                        input.readBytes().toString(Charsets.ISO_8859_1)
                    }
                    val clipboardMarkers = when (project.name) {
                        "forge-1.7.10", "forge-1.8.9", "forge-1.9.4",
                        "forge-1.10.2", "forge-1.11.2", "forge-1.12.2",
                            -> setOf("func_146275_d", "func_146277_j")
                        "forge-1.13.2", "forge-1.14.4", "forge-1.15.2",
                        "forge-1.16.5" -> setOf("func_197960_a", "func_197965_a")
                        "fabric-1.14.4", "fabric-1.15.2", "fabric-1.16.5" ->
                            setOf("method_1455", "method_1460")
                        else -> error("No clipboard audit markers for ${project.name}")
                    }
                    clipboardMarkers.forEach { clipboardMarker ->
                        check(clipboardMarker in clipboardConstants) {
                            "${jar.name} does not use Minecraft's native clipboard API ($clipboardMarker)"
                        }
                    }
                    check("java/awt/Toolkit" !in clipboardConstants) {
                        "${jar.name} still uses the unreliable AWT clipboard path"
                    }
                    if (project.name in setOf(
                            "forge-1.7.10", "forge-1.8.9", "forge-1.9.4",
                            "forge-1.10.2", "forge-1.11.2", "forge-1.12.2")) {
                        val adapterConstants = zip.getInputStream(clientAdapterEntry).use { input ->
                            input.readBytes().toString(Charsets.ISO_8859_1)
                        }
                        listOf(
                            "ClientCommandHandler",
                            "GuiShareToLan",
                            "/e4steam",
                            "inviteFriends"
                        ).forEach { marker ->
                            check(marker in adapterConstants) {
                                "${jar.name} client adapter is missing UI/command marker $marker"
                            }
                        }
                    }
                    val requiredNatives = setOf(
                        "steam_api64.dll", "steamworks4j64.dll", "steamworks4j-server64.dll",
                        "libsteam_api.so", "libsteamworks4j.so", "libsteamworks4j-server.so",
                        "libsteam_api.dylib", "libsteamworks4j.dylib", "libsteamworks4j-server.dylib"
                    )
                    val requiredJnaNatives = setOf(
                        "com/sun/jna/win32-x86-64/jnidispatch.dll",
                        "com/sun/jna/linux-x86-64/libjnidispatch.so",
                        "com/sun/jna/darwin-x86-64/libjnidispatch.jnilib",
                        "com/sun/jna/darwin-aarch64/libjnidispatch.jnilib"
                    )
                    requiredNatives.forEach { native ->
                        check(native in names) { "Missing $native from ${jar.name}" }
                    }
                    if (project.name !in minecraftProvidedJnaProjects) {
                        requiredJnaNatives.forEach { native ->
                            check(native in names) { "Missing $native from ${jar.name}" }
                        }
                    }
                    val packagedNatives = names.filter { name ->
                        name.endsWith(".dll") || name.endsWith(".so") ||
                                name.endsWith(".dylib") || name.endsWith(".jnilib")
                    }.toSet()
                    val expectedNatives = requiredNatives + if (
                        project.name in minecraftProvidedJnaProjects
                    ) emptySet() else requiredJnaNatives
                    check(packagedNatives == expectedNatives) {
                        "Unexpected retro native set in ${jar.name}: ${packagedNatives - expectedNatives}"
                    }
                    if (project.name == "forge-1.7.10") {
                        check("META-INF/licenses/module-common/LICENSE" in names) {
                            "UniMixins module licenses were not preserved in ${jar.name}"
                        }
                    }
                    if (project.name in legacyLangProjects) {
                        listOf(
                            "assets/e4steam/lang/en_US.lang",
                            "assets/e4steam/lang/en_us.lang",
                            "assets/e4steam/lang/ru_RU.lang",
                            "assets/e4steam/lang/ru_ru.lang"
                        ).forEach { languageResource ->
                            check(languageResource in names) {
                                "Missing legacy language resource $languageResource from ${jar.name}"
                            }
                        }
                    }
                    if (project.name in componentLangProjects) {
                        check("pack.mcmeta" in names) {
                            "Missing resource-pack metadata from ${jar.name}"
                        }
                        listOf(
                            "assets/e4steam_minecraft/lang/en_us.json",
                            "assets/e4steam_minecraft/lang/ru_ru.json"
                        ).forEach { languageResource ->
                            check(languageResource in names) {
                                "Missing component language resource $languageResource from ${jar.name}"
                            }
                        }
                    }
                    check(names.none { it.contains("e4mc", ignoreCase = true) })
                    check(names.none { it.contains("cloudflare", ignoreCase = true) || it.contains("quiclime", ignoreCase = true) })
                    if (project.name.startsWith("forge-")) {
                        check("link/e4steam/retro/RetroBuildMetadata.class" in names) {
                            "Missing generated Forge branch metadata from ${jar.name}"
                        }
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
                        val acceptedMinecraftVersions = when (minecraftVersion) {
                            "1.6.4" -> "[1.6.4]"
                            "1.7.10" -> "[1.7,1.8)"
                            "1.8.9" -> "[1.8,1.9)"
                            "1.9.4" -> "[1.9,1.10)"
                            "1.10.2" -> "[1.10,1.11)"
                            "1.11.2" -> "[1.11,1.12)"
                            "1.12.2" -> "[1.12,1.13)"
                            else -> null
                        }
                        if (acceptedMinecraftVersions != null) {
                            check("acceptedMinecraftVersions" in constantPool) {
                                "${jar.name} does not declare a Forge Minecraft compatibility range"
                            }
                            check(acceptedMinecraftVersions in constantPool) {
                                "${jar.name} does not declare ${acceptedMinecraftVersions}"
                            }
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
