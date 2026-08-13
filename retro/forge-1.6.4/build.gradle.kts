plugins { id("xyz.wagyourtail.unimined") }

val minecraftVersion = "1.6.4"
unimined.minecraft {
    version(minecraftVersion)
    minecraftForge {
        loader("9.11.1.1345")
        mixinConfig("e4steam.retro.mixins.json")
    }
    defaultRemapJar = true
}
sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-1.6/src/main/java"))
    java.srcDir(rootProject.file("adapters/integrated-1.6/src/main/java"))
    resources.srcDir(rootProject.file("runtime-template/forge-1.6"))
}
dependencies {
    implementation("org.ow2.asm:asm-all:5.2")
    add("shadowBundle", "org.ow2.asm:asm-all:5.2")
    implementation("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        exclude(module = "launchwrapper")
        exclude(module = "guava")
        exclude(module = "gson")
        exclude(module = "commons-io")
    }
    add("shadowBundle", "org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        exclude(module = "launchwrapper")
        exclude(module = "guava")
        exclude(module = "gson")
        exclude(module = "commons-io")
    }
}
tasks.withType<Jar>().configureEach {
    manifest.attributes(mapOf(
            "FMLCorePluginContainsFMLMod" to "true",
            "FMLCorePlugin" to "link.e4steam.retro.forge.core.E4steamForgeCore",
            "TweakOrder" to "-100000",
            "ForceLoadAsMod" to "true",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "MixinConfigs" to "e4steam.retro.mixins.json"
    ))
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching(listOf("mcmod.info", "e4steam.retro.mixins.json")) { expand(inputs.properties) }
}
