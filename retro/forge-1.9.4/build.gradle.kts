plugins { id("xyz.wagyourtail.unimined") }

val minecraftVersion = "1.9.4"
unimined.minecraft {
    version(minecraftVersion)
    minecraftForge {
        loader("12.17.0.2317-1.9.4")
        mixinConfig("e4steam.retro.mixins.json")
    }
    mappings { searge(); mcp("stable", "26-1.9.4") }
    defaultRemapJar = true
}
sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-1.9-1.12/src/main/java"))
    java.srcDir(rootProject.file("adapters/forge-1.8-1.12-login/src/main/java"))
    java.srcDir(rootProject.file("adapters/netty-lan/src/main/java"))
    resources.srcDir(rootProject.file("runtime-template/forge-lan"))
}
dependencies {
    implementation("org.spongepowered:mixin:0.7.11-SNAPSHOT") { isTransitive = false }
    add("shadowBundle", "org.spongepowered:mixin:0.7.11-SNAPSHOT") { isTransitive = false }
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
