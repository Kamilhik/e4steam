plugins { id("xyz.wagyourtail.unimined") }

val fabricApiVersion = "0.28.5+1.14"

unimined.minecraft {
    version("1.14.4")
    fabric {
        loader("0.16.14")
    }
    mappings { intermediary(); mojmap() }
    defaultRemapJar = true
}

sourceSets.main {
    java.srcDir(rootProject.file("adapters/fabric-modern/src/main/java"))
    java.srcDir(rootProject.file("adapters/modern-listener/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.16-login/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.16-play/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.15-chat/src/main/java"))
    java.srcDir(rootProject.file("adapters/minecraft-1.14-1.15-ui/src/main/java"))
}

dependencies {
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly("org.spongepowered:mixin:0.8.5")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", "1.14.4")
    filesMatching(listOf("fabric.mod.json", "e4steam.retro.mixins.json")) {
        expand(inputs.properties)
    }
}
