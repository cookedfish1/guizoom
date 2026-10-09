package com.cookedfish.guizoom.zoom.mode;

import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.zoom.ZoomManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Scheme 3 (reserved / alternative) - continuous fractional GUI scale.
 * <p>
 * Same family as {@link VanillaScaleMode} (the layout really changes, so screens reflow and no widget
 * is ever clipped) but the scale is a {@code double} instead of the vanilla integer option. The
 * vanilla code path is reused as far as possible: the multiplier is applied on top of
 * {@link Window#calculateScale(int, boolean)} and then exactly the three calls
 * {@link Minecraft#resizeDisplay()} performs are made:
 *
 * <pre>
 * window.setGuiScale(scale)
 * screen.resize(mc, window.getGuiScaledWidth(), window.getGuiScaledHeight())
 * ForgeHooksClient.resizeGuiLayers(mc, window.getGuiScaledWidth(), window.getGuiScaledHeight())
 * </pre>
 *
 * The vanilla {@code guiScale} option itself is never modified, so leaving this mode (or a window
 * resize) restores the player's own setting.
 */
public final class FractionalScaleMode implements IZoomMode {

    @Override
    public ResourceLocation id() {
        return ZoomModeRegistry.FRACTIONAL;
    }

    @Override
    public Component displayName() {
        return Component.translatable("guizoom.mode.fractional");
    }

    @Override
    public Component description() {
        return Component.translatable("guizoom.mode.fractional.desc");
    }

    @Override
    public double minZoom() {
        return 0.5D;
    }

    @Override
    public double maxZoom() {
        return 6.0D;
    }

    @Override
    public double defaultZoom() {
        return 1.0D;
    }

    @Override
    public double step() {
        // A full wheel notch worth of change: half a step was too subtle to notice.
        return ZoomConfig.zoomStep();
    }

    @Override
    public void onZoomChanged(Minecraft minecraft, double zoom) {
        // Apply on the next frame: a screen must not be re-initialised from inside input dispatch.
        ZoomManager.get().requestResize();
    }

    @Override
    public void applyWindowScale(Minecraft minecraft, double zoom) {
        Window window = minecraft.getWindow();
        double target = targetScale(minecraft, zoom);
        if (Math.abs(window.getGuiScale() - target) < 1.0E-4D && isScreenInSync(minecraft, window)) {
            return;
        }
        window.setGuiScale(target);
        if (minecraft.screen != null) {
            minecraft.screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
        ForgeHooksClient.resizeGuiLayers(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    @Override
    public boolean isWindowScaleApplied(Minecraft minecraft, double zoom) {
        Window window = minecraft.getWindow();
        return Math.abs(window.getGuiScale() - targetScale(minecraft, zoom)) < 1.0E-3D && isScreenInSync(minecraft, window);
    }

    /**
     * The GUI scale is only really applied when the open screen was laid out for the resulting size
     * as well. Checking both makes a half applied change (new projection, stale widget positions)
     * self healing instead of looking like a render-only zoom.
     */
    private static boolean isScreenInSync(Minecraft minecraft, Window window) {
        return minecraft.screen == null
                || (minecraft.screen.width == window.getGuiScaledWidth() && minecraft.screen.height == window.getGuiScaledHeight());
    }

    @Override
    public void onDeactivate(Minecraft minecraft, double zoom) {
        if (minecraft != null) {
            // Back to the plain vanilla scale the player configured.
            minecraft.resizeDisplay();
        }
    }

    @Override
    public Component zoomLabel(double zoom) {
        return Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", zoom));
    }

    private static double targetScale(Minecraft minecraft, double zoom) {
        Window window = minecraft.getWindow();
        int vanillaScale = minecraft.options == null ? 0 : minecraft.options.guiScale().get();
        int base = window.calculateScale(vanillaScale, minecraft.isEnforceUnicode());
        // Never go below scale 1: Minecraft cannot render a GUI smaller than that.
        return Math.max(1.0D, base * zoom);
    }
}
