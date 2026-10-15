plugins {
    id("net.neoforged.moddev")
}

// Node "<group>-neoforge": pull the group's table and its neoforge sub-table out of stonecutter.properties.toml
val loader = "neoforge"
sc.properties.tags(sc.current.project.substringBeforeLast('-'), loader)

version = "${property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String
base.archivesName = "${property("mod.id")}-$loader"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

sourceSets.main {
    // Entrypoints for the other loaders live next to this one
    java.exclude("dev/karton/spotifywidget/loader/fabric/**", "dev/karton/spotifywidget/loader/forge/**")
    resources.exclude("fabric.mod.json", "META-INF/mods.toml")
}

neoForge {
    version = property("deps.neoforge") as String

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run")
        }
    }

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        // Arrays are flattened to "mod.mc_releases.0", ".1", ...
        val releases = generateSequence(0) { it + 1 }
            .map { project.findProperty("mod.mc_releases.$it")?.toString() }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
        val props = mapOf(
            "id" to project.property("mod.id") as String,
            "name" to project.property("mod.name") as String,
            "description" to project.property("mod.description") as String,
            "author" to project.property("mod.author") as String,
            // Maven range over the releases this jar covers, e.g. "[1.21.6,1.21.8]"
            "minecraft" to "[${releases.first()},${releases.last()}]",
            "version" to project.version.toString(),
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinJava = "JAVA_" + requiredJava.majorVersion
        inputs.property("mixinJava", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
