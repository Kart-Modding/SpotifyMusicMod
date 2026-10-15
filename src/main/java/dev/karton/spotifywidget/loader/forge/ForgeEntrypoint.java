package dev.karton.spotifywidget.loader.forge;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.compat.Keys;
import dev.karton.spotifywidget.config.ConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?} elif <1.21.9 {
/*import net.minecraftforge.eventbus.api.bus.BusGroup;
*///?}

/**
 * Forge client entrypoint. Forge moved to EventBus 7 in 1.21.6 (per-event buses looked up from the
 * mod's bus group) and gave every event a static {@code BUS} in 1.21.9.
 */
@Mod(SpotifyWidgetClient.MOD_ID)
public class ForgeEntrypoint {
    public ForgeEntrypoint(FMLJavaModLoadingContext context) {
        SpotifyWidgetClient.init();

        //? if >=1.21.9 {
        RegisterKeyMappingsEvent.BUS.addListener(ForgeEntrypoint::registerKeys);
        AddGuiOverlayLayersEvent.BUS.addListener(ForgeEntrypoint::addLayer);
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> SpotifyWidgetClient.tick());
        GameShuttingDownEvent.BUS.addListener(event -> SpotifyWidgetClient.shutdown());
        //?} elif >=1.21.6 {
        /*BusGroup modBus = context.getModBusGroup();
        RegisterKeyMappingsEvent.getBus(modBus).addListener(ForgeEntrypoint::registerKeys);
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(ForgeEntrypoint::addLayer);
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> SpotifyWidgetClient.tick());
        GameShuttingDownEvent.BUS.addListener(event -> SpotifyWidgetClient.shutdown());
        *///?} else {
        /*IEventBus modBus = context.getModEventBus();
        modBus.addListener(ForgeEntrypoint::registerKeys);
        modBus.addListener(ForgeEntrypoint::addLayer);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> SpotifyWidgetClient.tick());
        MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> SpotifyWidgetClient.shutdown());
        *///?}

        // Settings button in the Forge mod list
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : Keys.all()) event.register(mapping);
    }

    private static void addLayer(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "now_playing"),
                (context, tickCounter) -> SpotifyWidgetClient.render(new Canvas(context)));
    }
}
