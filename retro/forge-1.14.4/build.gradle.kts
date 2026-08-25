plugins { id("xyz.wagyourtail.unimined") }
unimined.minecraft {
    version("1.14.4")
    minecraftForge { loader("28.2.30"); mixinConfig("e4steam.retro.mixins.json") }
    mappings { intermediary(); mojmap() }
    defaultRemapJar = true
}
sourceSets.main {
    java.srcDir(rootProject.file("adapters/forge-modern/src/main/java"))
    java.srcDir(rootProject.file("adapters/forge-1.13-1.14-hooks/src/main/java"))
    java.srcDir(rootProject.file("adapters/modern-listener/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.16-command/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.15-ui/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.15-chat/src/main/java"))
    java.exclude("link/e4steam/retro/mixin/ConnectScreenAddressRetroMixin.java")
}
dependencies { compileOnly("org.spongepowered:mixin:0.8.5") }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") { expand(inputs.properties) }
}
