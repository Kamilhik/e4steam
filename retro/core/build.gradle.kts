plugins {
    java
}

val steamworksVersion = "1.10.0"

val sharedSteamSources = fileTree(rootProject.file("../common/src/main/java")) {
    include(
        "link/e4steam/HexCodec.java",
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
        "link/e4steam/steam/SteamGuestJoinState.java",
        "link/e4steam/steam/SteamInvitationAuthorizer.java",
        "link/e4steam/steam/SteamLifecycle.java",
        "link/e4steam/steam/SteamLobbyManager.java",
        "link/e4steam/steam/SteamLoopbackAuthentication.java",
        "link/e4steam/steam/SteamMinecraftIdentity.java",
        "link/e4steam/steam/SteamNativeLibraryLoader.java",
        "link/e4steam/steam/SteamNetworkingMessagesTransport.java",
        "link/e4steam/steam/SteamOutboundQueue.java",
        "link/e4steam/steam/SteamProcessGuard.java",
        "link/e4steam/steam/SteamPeerPrivacy.java",
        "link/e4steam/steam/SteamProtocol.java",
        "link/e4steam/steam/SteamResetRetryQueue.java",
        "link/e4steam/steam/SteamRuntime.java",
        "link/e4steam/steam/SteamUdpBridge.java",
        "link/e4steam/steam/SteamworksApi.java",
        "link/e4steam/steam/VoiceChatUdpEndpoint.java"
    )
}

sourceSets.main {
    java.setSrcDirs(listOf("src/main/java"))
}

tasks.compileJava {
    source(sharedSteamSources)
}

dependencies {
    implementation("com.code-disaster.steamworks4j:steamworks4j:$steamworksVersion") {
        isTransitive = false
    }
    compileOnly("net.java.dev.jna:jna:5.10.0")
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.2")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

tasks.jar {
    archiveBaseName.set("e4steam-retro-core")
}
