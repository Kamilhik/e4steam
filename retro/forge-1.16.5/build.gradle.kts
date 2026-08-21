plugins {
    id("xyz.wagyourtail.unimined")
}

unimined.minecraft {
    version("1.16.5")
    minecraftForge {
        loader("36.2.42")
        mixinConfig("e4steam.retro.mixins.json")
    }
    mappings { intermediary(); mojmap() }
    defaultRemapJar = true
}

sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-modern/src/main/java"))
    java.srcDir(rootProject.file("adapters/modern-listener/src/main/java"))
}

dependencies { compileOnly("org.spongepowered:mixin:0.8.5") }

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", "1.16.5")
    filesMatching(listOf("META-INF/mods.toml", "e4steam.retro.mixins.json")) {
        expand(inputs.properties)
    }
}
