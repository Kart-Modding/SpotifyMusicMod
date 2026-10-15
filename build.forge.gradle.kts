plugins {
    java
    id("net.minecraftforge.gradle")
    id("me.modmuss50.mod-publish-plugin")
}

// Node "<group>-forge": pull the group's table and its forge sub-table out of stonecutter.properties.toml
val loader = "forge"
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
    java.exclude("dev/karton/spotifywidget/loader/fabric/**", "dev/karton/spotifywidget/loader/neoforge/**")
    resources.exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
}

minecraft {
    runs {
        configureEach {
            workingDir = rootProject.layout.projectDirectory.dir("run")
        }
        register("client")
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${property("deps.forge")}"))
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
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

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
        filesMatching("META-INF/mods.toml") { expand(props) }

        // Forge bundles Mixin 0.8.7, whose newest compatibility level is JAVA_21
        filesMatching("*.mixins.json") { expand("java" to "JAVA_21") }
    }

    jar {
        // Forge has no mixin entry in mods.toml; it reads the config from the manifest
        manifest.attributes("MixinConfigs" to "${project.property("mod.id")}.mixins.json")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}

// Uploads this node's jar to Modrinth and CurseForge.
// CI runs it when mod.version changes on main. Without MODRINTH_TOKEN it is a dry run, so a local
// `./gradlew publishMods` only logs what it would upload.
publishMods {
    // Arrays are flattened to "mod.mc_releases.0", ".1", ...
    val releases = generateSequence(0) { it + 1 }
        .map { project.findProperty("mod.mc_releases.$it")?.toString() }
        .takeWhile { it != null }
        .filterNotNull()
        .toList()

    file = tasks.jar.flatMap { it.archiveFile }
    displayName = "${property("mod.name")} ${project.version} (Forge)"
    version = project.version.toString()
    changelog = providers.environmentVariable("PUBLISH_CHANGELOG").orElse("")
    type = STABLE
    modLoaders.addAll("forge")
    dryRun = providers.environmentVariable("MODRINTH_TOKEN").orNull == null

    modrinth {
        projectId = "pjhN6TWN"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(releases)
    }

    curseforge {
        projectId = "1655041"
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(releases)
        javaVersions.add(requiredJava)
        clientRequired = true
        serverRequired = false
    }
}
