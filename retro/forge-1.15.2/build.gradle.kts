plugins { id("xyz.wagyourtail.unimined") }

unimined.minecraft {
    version("1.15.2")
    minecraftForge { loader("31.2.62"); mixinConfig("e4steam.retro.mixins.json") }
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
    filesMatching("META-INF/mods.toml") { expand(inputs.properties) }
}
