package dev.karton.spotifywidget.loader.fabric;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.compat.Keys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;*/

/** Fabric (and Quilt) client entrypoint. */
public class FabricEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpotifyWidgetClient.init();

        for (KeyMapping mapping : Keys.all()) {
            //? if >=26.1 {
            KeyMappingHelper.registerKeyMapping(mapping);
            //?} else
            /*KeyBindingHelper.registerKeyBinding(mapping);*/
        }

        //? if >=1.21.6 {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "now_playing"),
                (context, tickCounter) -> SpotifyWidgetClient.render(new Canvas(context)));
        //?} else
        /*HudRenderCallback.EVENT.register((context, tickCounter) -> SpotifyWidgetClient.render(new Canvas(context)));*/

        ClientTickEvents.END_CLIENT_TICK.register(client -> SpotifyWidgetClient.tick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SpotifyWidgetClient.shutdown());
    }
}
