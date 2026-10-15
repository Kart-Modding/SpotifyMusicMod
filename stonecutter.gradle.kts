plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3.x-fabric"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    // Node names are "<group>-<loader>", so `//? if neoforge {` etc. work in sources
    constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge", "forge")

    replacements {
        // Minecraft 1.21.11 renamed ResourceLocation -> Identifier
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        // Minecraft 26.3 replaced GLFW keysyms with SDL scancodes
        string(current.parsed >= "26.3") {
            replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        }
    }
}
