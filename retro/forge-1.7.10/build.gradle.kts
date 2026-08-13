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
    implementation("com.github.LegacyModdingMC.UniMixins:unimixins-all-1.7.10:0.1.20") { isTransitive = false }
    add("shadowBundle", "com.github.LegacyModdingMC.UniMixins:unimixins-all-1.7.10:0.1.20") { isTransitive = false }
}
tasks.withType<Jar>().configureEach {
    manifest.attributes(mapOf("FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "MixinConfigs" to "e4steam.retro.mixins.json"))
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching(listOf("mcmod.info", "e4steam.retro.mixins.json")) { expand(inputs.properties) }
}
