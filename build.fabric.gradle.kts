plugins {
    // Applies the right Loom variant (remapping on <=1.21.11, plain on 26.1+)
    id("dev.kikugie.loom-back-compat")
}

// Node "<group>-fabric": pull the group's table and its fabric sub-table out of stonecutter.properties.toml
val loader = "fabric"
sc.properties.tags(sc.current.project.substringBeforeLast('-'), loader)

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id")}-$loader"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

sourceSets.main {
    // Entrypoints for the other loaders live next to this one
    java.exclude("dev/karton/spotifywidget/loader/neoforge/**", "dev/karton/spotifywidget/loader/forge/**")
    resources.exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, property("deps.fabric_api") as String))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    // fabric.mod.json depends on the "fabric-api" id, which only the full jar provides in dev
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    // Two Fabric API modules were renamed along the way
    val keyModule = if (sc.current.parsed >= "26.1") "fabric-key-mapping-api-v1" else "fabric-key-binding-api-v1"
    val resourceModule = if (sc.current.parsed >= "1.21.11") "fabric-resource-loader-v1" else "fabric-resource-loader-v0"
    fapi(
        "fabric-api-base",
        "fabric-rendering-v1",
        "fabric-lifecycle-events-v1",
        resourceModule,
        keyModule,
    )

    // Config screen integration - optional at runtime
    modCompileOnly("maven.modrinth:modmenu:${property("deps.modmenu")}")
}

loom {
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
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
        val props = mapOf(
            "id" to project.property("mod.id") as String,
            "name" to project.property("mod.name") as String,
            "description" to project.property("mod.description") as String,
            "author" to project.property("mod.author") as String,
            "minecraft" to project.property("mod.mc_compat") as String,
            // "1.0.0+1.21.8" so every jar carries the game version it was built for
            "version" to project.version.toString(),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_" + requiredJava.majorVersion
        inputs.property("mixinJava", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
