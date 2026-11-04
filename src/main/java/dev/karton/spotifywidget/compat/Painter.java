package dev.karton.spotifywidget.compat;

import net.minecraft.client.gui.components.Renderable;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/

import java.util.function.Consumer;

/** Draws through a {@link Canvas} at its place in a screen's widget order. */
public final class Painter implements Renderable {
    private final Consumer<Canvas> action;

    public Painter(Consumer<Canvas> action) {
        this.action = action;
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        action.accept(new Canvas(graphics));
    }
}
