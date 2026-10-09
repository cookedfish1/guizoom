package com.cookedfish.guizoom.zoom.mode;

import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.api.ZoomTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Scheme 2 - the magnifier.
 * <p>
 * The UI is <b>not</b> re-laid out. Instead the whole GUI pose stack is scaled around the mouse
 * cursor while it is being rendered, so the interface behaves like a sheet of paper seen through a
 * magnifying glass: pixels get bigger, less of the screen fits, and moving the mouse pans the view.
 * <p>
 * Anchoring on the cursor is what keeps it usable: the cursor is a fixed point of the transform, so
 * the GUI coordinate under the pointer is unchanged and vanilla hit testing keeps pointing at the
 * widget the player sees. Nothing has to be patched in {@code Screen}, {@code AbstractContainerScreen}
 * or any modded widget, and because the transform is applied at render time the logical GUI size
 * ({@code window.getGuiScaledWidth()}) is untouched as well.
 * <p>
 * The HUD is magnified too (through {@code RenderGuiEvent}), so the hotbar, chat and health bars stay
 * consistent with the open screen.
 */
public final class MagnifierMode implements IZoomMode {

    @Override
    public ResourceLocation id() {
        return ZoomModeRegistry.MAGNIFIER;
    }

    @Override
    public Component displayName() {
        return Component.translatable("guizoom.mode.magnifier");
    }

    @Override
    public Component description() {
        return Component.translatable("guizoom.mode.magnifier.desc");
    }

    @Override
    public double minZoom() {
        return 1.0D;
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
        return new ZoomTransform(zoom, mouseX, mouseY);
    }
}
