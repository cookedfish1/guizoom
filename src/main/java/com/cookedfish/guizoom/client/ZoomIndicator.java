package com.cookedfish.guizoom.client;

import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.zoom.ZoomManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Small transient "what did the wheel just do" readout on the HUD. */
public final class ZoomIndicator {

    private static final long VISIBLE_MILLIS = 1500L;

    private ZoomIndicator() {
    }

    public static void render(GuiGraphics guiGraphics) {
        if (guiGraphics == null) {
            return;
        }
        // Cheapest and by far most common case first: nothing changed recently, so this costs one
        // subtraction per frame and never touches the config or the font.
        long elapsed = Util.getMillis() - ZoomManager.get().lastChangeMillis();
        if (elapsed < 0L || elapsed > VISIBLE_MILLIS || !ZoomConfig.showIndicator()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }

        ZoomManager manager = ZoomManager.get();
        IZoomMode mode = manager.activeMode();
        double zoom = manager.zoom();
        Component text = Component.translatable("guizoom.indicator", mode.displayName(), mode.zoomLabel(zoom));

        int alpha = Mth.clamp((int) (255.0D * (1.0D - (double) elapsed / (double) VISIBLE_MILLIS)), 0, 255);
        int width = minecraft.font.width(text);
        int x = (guiGraphics.guiWidth() - width) / 2;
        int y = 6;

        guiGraphics.fill(x - 4, y - 3, x + width + 4, y + 11, (alpha / 2) << 24 | 0x101010);
        guiGraphics.drawString(minecraft.font, text.getVisualOrderText(), x, y, alpha << 24 | 0xFFFFFF);
    }
}
