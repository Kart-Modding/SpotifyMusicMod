package dev.karton.spotifywidget.loader.neoforge;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.compat.Keys;
import dev.karton.spotifywidget.config.ConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** NeoForge client entrypoint. */
@Mod(value = SpotifyWidgetClient.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus, ModContainer container) {
        SpotifyWidgetClient.init();

        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            //? if >=1.21.9
            event.registerCategory(Keys.CATEGORY);
            for (KeyMapping mapping : Keys.all()) event.register(mapping);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "now_playing"),
                (context, tickCounter) -> SpotifyWidgetClient.render(new Canvas(context))));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SpotifyWidgetClient.tick());
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> SpotifyWidgetClient.shutdown());

        // Settings button in the NeoForge mod list
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigScreen(parent));
    }
}
