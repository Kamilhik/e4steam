plugins { id("xyz.wagyourtail.unimined") }

unimined.minecraft {
    version("1.13.2")
    minecraftForge {
        loader("25.0.223")
        mixinConfig("e4steam.retro.mixins.json")
    }
    mappings {
        searge()
        mcp("snapshot", "20180921-1.13")
    }
    defaultRemapJar = true
}

sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-1.13/src/main/java"))
    java.srcDir(rootProject.file("adapters/forge-1.13-1.14-hooks/src/main/java"))
    java.srcDir(rootProject.file("adapters/netty-endpoint/src/main/java"))
}

dependencies { compileOnly("org.spongepowered:mixin:0.8.5") }

tasks.withType<Jar>().configureEach {
    manifest.attributes(mapOf(
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "MixinConfigs" to "e4steam.retro.mixins.json"
    ))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", "1.13.2")
    filesMatching(listOf("META-INF/mods.toml", "e4steam.retro.mixins.json")) {
        expand(inputs.properties)
    }
}
