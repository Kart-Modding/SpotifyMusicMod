pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
    plugins {
        // Loader toolchains, picked per node by build.<loader>.gradle.kts
        id("net.neoforged.moddev") version "2.0.148"
        id("net.minecraftforge.gradle") version "7.0.40"
        // Uploads jars to Modrinth and CurseForge (publishMods task)
        id("me.modmuss50.mod-publish-plugin") version "2.2.1"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Applies the correct Loom variant for obfuscated (<=1.21.11) and unobfuscated (26.1+) versions
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * One node per binary-compatible group and loader, named "<group>-<loader>".
         * Each jar covers the range in mod.mc_compat. Quilt runs the Fabric jars as-is.
         */
        fun group(name: String, minecraft: String = name) {
            for (loader in listOf("fabric", "neoforge", "forge"))
                version("$name-$loader", minecraft).buildscript("build.$loader.gradle.kts")
        }

        for (it in listOf("1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11")) group(it)
        group("26.1.x", "26.1.2")
        group("26.2.x", "26.2")
        group("26.3.x", "26.3")
        vcsVersion = "26.3.x-fabric"
    }
}

rootProject.name = "SpotifyHud"

// loom-back-compat puts Loom on the classpath of every project that sets loomx.loom_version,
// so only the Fabric nodes get it. projectsLoaded runs before any project is configured.
gradle.projectsLoaded {
    rootProject.subprojects {
        if (name.endsWith("-fabric")) extra["loomx.loom_version"] = "1.17-SNAPSHOT"
    }
}
