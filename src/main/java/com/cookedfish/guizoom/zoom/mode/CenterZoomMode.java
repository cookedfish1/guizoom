package com.cookedfish.guizoom.zoom.mode;

import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.api.ZoomTransform;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Reserved / example scheme - the same render time approach as {@link MagnifierMode}, but anchored on
 * the centre of the screen instead of the pointer.
 * <p>
 * Its only difference to the magnifier is the anchor returned by {@link #getTransform}, which makes it
 * a good copy &amp; paste template for a custom zoom scheme: implement {@link IZoomMode}, register the
 * instance in {@link ZoomModeRegistry}, and it shows up in the settings screen, in the config and on
 * the mouse wheel automatically.
 */
public final class CenterZoomMode implements IZoomMode {

    @Override
    public ResourceLocation id() {
        return ZoomModeRegistry.CENTER;
    }

    @Override
    public Component displayName() {
        return Component.translatable("guizoom.mode.center");
    }

    @Override
    public Component description() {
        return Component.translatable("guizoom.mode.center.desc");
    }

    @Override
    public double minZoom() {
        return 0.5D;
    }

    @Override
    public double maxZoom() {
        return 16.0D;
    }

    @Override
    public double defaultZoom() {
        return 1.0D;
    }

    @Override
    public double step() {
        return ZoomConfig.zoomStep();
    }

    @Override
    public boolean poseBased() {
        return true;
    }

    @Override
    public ZoomTransform getTransform(Minecraft minecraft, double zoom, double mouseX, double mouseY) {
        Window window = minecraft.getWindow();
        return new ZoomTransform(zoom, window.getGuiScaledWidth() / 2.0D, window.getGuiScaledHeight() / 2.0D);
    }
}
