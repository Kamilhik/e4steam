plugins { id("xyz.wagyourtail.unimined") }

val minecraftVersion = "1.7.10"
unimined.minecraft {
    version(minecraftVersion)
    minecraftForge {
        loader("10.13.4.1614-1.7.10")
        mixinConfig("e4steam.retro.mixins.json")
    }
    mappings { searge(); mcp("stable", "12-1.7.10") }
    defaultRemapJar = true
}
sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-legacy/src/main/java"))
    java.srcDir(rootProject.file("adapters/netty-lan/src/main/java"))
    resources.srcDir(rootProject.file("runtime-template/forge-lan"))
}
dependencies {
    // UniMixins is a Forge mod suite, not a private implementation library.
    // Keep it on the development/runtime classpath, but never merge it into
    // e4steam: modpacks must be able to provide one shared compatible copy.
    implementation("com.github.LegacyModdingMC.UniMixins:unimixins-all-1.7.10:0.1.20") { isTransitive = false }
}
tasks.withType<Jar>().configureEach {
    manifest.attributes(mapOf("FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
            "MixinConfigs" to "e4steam.retro.mixins.json"))
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching(listOf("mcmod.info", "e4steam.retro.mixins.json")) { expand(inputs.properties) }
}
