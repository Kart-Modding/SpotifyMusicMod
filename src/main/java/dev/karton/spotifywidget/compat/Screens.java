package dev.karton.spotifywidget.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opening a screen moved from Minecraft to Gui in 26.2. */
public final class Screens {
    /** 26.3 moved input to SDL, where the left mouse button is 1 instead of 0. */
    //? if >=26.3 {
    public static final int LEFT_BUTTON = InputConstants.MOUSE_BUTTON_LEFT;
    //?} else
    /*public static final int LEFT_BUTTON = 0;*/

    private Screens() {
    }

    public static void open(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        minecraft.gui.setScreen(screen);
        //?} else
        /*minecraft.setScreen(screen);*/
    }
}
