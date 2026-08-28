plugins {
    java
}

val steamworksVersion = "1.10.0"

val sharedSteamSources = fileTree(rootProject.file("../common/src/main/java")) {
    include(
        "link/e4steam/HexCodec.java",
        "link/e4steam/internal/dedicated/DedicatedConfigFile.java",
        "link/e4steam/internal/dedicated/DedicatedServerPropertiesValidator.java",
        "link/e4steam/steam/NativePlatform.java",
        "link/e4steam/steam/SteamAccessMode.java",
        "link/e4steam/steam/SteamAddress.java",
        "link/e4steam/steam/SteamAddonHooks.java",
        "link/e4steam/steam/SteamApi.java",
        "link/e4steam/steam/SteamBridgeRegistry.java",
        "link/e4steam/steam/SteamBridgeRuntime.java",
        "link/e4steam/steam/SteamClientBridge.java",
        "link/e4steam/steam/SteamConnectionBridge.java",
        "link/e4steam/steam/SteamDedicatedAddress.java",
        "link/e4steam/steam/SteamDedicatedClientBridge.java",
        "link/e4steam/steam/RetroDedicatedServerTransport.java",
        "link/e4steam/steam/SteamGameServerRuntimeBackend.java",
        "link/e4steam/steam/SteamGuestJoinState.java",
        "link/e4steam/steam/SteamInvitationAuthorizer.java",
        "link/e4steam/steam/SteamKnownPeerSessionGate.java",
        "link/e4steam/steam/SteamLifecycle.java",
        "link/e4steam/steam/SteamLobbyManager.java",
        "link/e4steam/steam/SteamLoopbackAuthentication.java",
        "link/e4steam/steam/SteamMinecraftIdentity.java",
        "link/e4steam/steam/SteamNativeLibraryLoader.java",
        "link/e4steam/steam/SteamNetworkingMessagesTransport.java",
        "link/e4steam/steam/SteamNetworkingSocketsP2PTransport.java",
        "link/e4steam/steam/SteamOutboundQueue.java",
        "link/e4steam/steam/SteamProcessGuard.java",
        "link/e4steam/steam/SteamPeerPrivacy.java",
        "link/e4steam/steam/SteamProtocol.java",
        "link/e4steam/steam/SteamResetRetryQueue.java",
        "link/e4steam/steam/SteamRuntime.java",
        "link/e4steam/steam/SteamRuntimeBackend.java",
        "link/e4steam/steam/SteamUdpBridge.java",
        "link/e4steam/steam/SteamworksApi.java",
        "link/e4steam/steam/VoiceChatUdpEndpoint.java"
    )
}

sourceSets.main {
    java.setSrcDirs(listOf("src/main/java"))
}

val generatedPreloadResources = layout.buildDirectory.dir("generated/resources/e4steamPreload")

val generateE4steamPreloadList = tasks.register("generateE4steamPreloadList") {
    dependsOn(tasks.named("compileJava"))

    val classesDirectory = layout.buildDirectory.dir("classes/java/main")
    val preloadList = generatedPreloadResources.map { directory ->
        directory.file("e4steam-retro-preload.txt")
    }
    inputs.dir(classesDirectory)
    outputs.file(preloadList)

    doLast {
        val classesRoot = classesDirectory.get().asFile
        val outputFile = preloadList.get().asFile
        val classNames = classesRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "class" }
            .map { file ->
                file.relativeTo(classesRoot).invariantSeparatorsPath
                    .removeSuffix(".class")
                    .replace('/', '.')
            }
            .filter { name -> name.startsWith("link.e4steam.") }
            .distinct()
            .sorted()
            .toList()

        outputFile.parentFile.mkdirs()
        outputFile.writeText(classNames.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

sourceSets.main {
    resources.srcDir(generatedPreloadResources)
}

tasks.processResources {
    dependsOn(generateE4steamPreloadList)
}

tasks.compileJava {
    source(sharedSteamSources)
}

dependencies {
    implementation("com.code-disaster.steamworks4j:steamworks4j:$steamworksVersion") {
        isTransitive = false
    }
    implementation("com.code-disaster.steamworks4j:steamworks4j-server:$steamworksVersion") {
        isTransitive = false
    }
    compileOnly("net.java.dev.jna:jna:5.10.0")
    // Minecraft 1.7.x ships an early Log4j 2 API. Compiling the shared retro
    // runtime against that API prevents linkage to newer fixed-arity overloads
    // that do not exist in the game process.
    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

tasks.jar {
    archiveBaseName.set("e4steam-retro-core")
}
