package dev.karton.spotifywidget.compat;

import net.minecraft.client.gui.components.Renderable;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/

import java.util.function.Consumer;

/** Draws through a {@link Canvas} at its place in a screen's widget order. */
public final class Painter implements Renderable {
    /** Painting that also wants the mouse position in GUI pixels. */
    public interface WithMouse {
        void paint(Canvas canvas, int mouseX, int mouseY);
    }

    private final WithMouse action;

    public Painter(Consumer<Canvas> action) {
        this.action = (canvas, mouseX, mouseY) -> action.accept(canvas);
    }

    public Painter(WithMouse action) {
        this.action = action;
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        action.paint(new Canvas(graphics), mouseX, mouseY);
    }
}
