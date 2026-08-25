plugins { id("xyz.wagyourtail.unimined") }

unimined.minecraft {
    version("1.12.2")
    minecraftForge {
        loader("14.23.5.2864")
        mixinConfig("e4steam.retro.mixins.json")
    }
    mappings {
        searge()
        mcp("stable", "39-1.12")
    }
    defaultRemapJar = true
}

sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-1.9-1.12/src/main/java"))
    java.srcDir(rootProject.file("adapters/forge-1.8-1.12-login/src/main/java"))
    java.srcDir(rootProject.file("adapters/netty-endpoint/src/main/java"))
}

dependencies {
    implementation("org.spongepowered:mixin:0.7.11-SNAPSHOT") { isTransitive = false }
    add("shadowBundle", "org.spongepowered:mixin:0.7.11-SNAPSHOT") { isTransitive = false }
}

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
    inputs.property("minecraftVersion", "1.12.2")
    filesMatching(listOf("mcmod.info", "e4steam.retro.mixins.json")) {
        expand(inputs.properties)
    }
}
