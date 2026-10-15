package dev.karton.spotifywidget.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.karton.spotifywidget.SpotifyWidgetClient;
import net.minecraft.client.KeyMapping;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?}

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Key mappings moved from a category string to a {@code KeyMapping.Category} in 1.21.9, and 26.3
 * swapped GLFW keysyms for SDL scancodes ({@code Type.KEYSYM} became {@code Type.KEYBOARD},
 * swapped by Stonecutter). Mappings are only created here; each loader entrypoint registers
 * {@link #all()} through its own API.
 */
public final class Keys {
    //? if >=1.21.9 {
    // NeoForge registers modded categories through its event, the others through the vanilla list
    //? if neoforge {
    /*public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "controls"));
    *///?} else {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "controls"));
    //?}
    //?} else
    /*private static final String CATEGORY = "key.categories.spotifywidget";*/

    /** The "no key" code differs per version (-1 for GLFW keysyms, 0 for SDL scancodes). */
    public static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    private static final List<KeyMapping> ALL = new ArrayList<>();

    private Keys() {
    }

    /** Creates a binding. Pass {@link #UNBOUND} for no default key. */
    public static KeyMapping create(String translationKey, int keyCode) {
        KeyMapping mapping = new KeyMapping(translationKey, InputConstants.Type.KEYBOARD, keyCode, CATEGORY);
        ALL.add(mapping);
        return mapping;
    }

    /** Every binding made so far, for the loader to register. */
    public static List<KeyMapping> all() {
        return Collections.unmodifiableList(ALL);
    }
}
