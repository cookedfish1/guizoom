package com.cookedfish.guizoom.zoom.mode;

import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.zoom.ZoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Scheme 1 - "just like the vanilla video settings slider".
 * <p>
 * This mode does not implement any scaling of its own. It only writes the vanilla
 * {@code Options.guiScale} option and then calls {@link Minecraft#resizeDisplay()}, which is the very
 * same code path the video settings screen uses:
 *
 * <pre>
 * Options.guiScale() -> Window.calculateScale(int, boolean) -> Window.setGuiScale(double)
 *                    -> Screen.resize(Minecraft, int, int) -> ForgeHooksClient.resizeGuiLayers(...)
 * </pre>
 *
 * Because the real GUI scale changes, every vanilla and modded screen re-lays itself out exactly as
 * if the player had dragged the vanilla slider. That is the most compatible scheme there is, at the
 * price of coarse integer steps.
 */
public final class VanillaScaleMode implements IZoomMode {

    /** Vanilla option value (0 == "auto") captured before the first wheel zoom, for reset/restore. */
    private int originalScale = -1;

    @Override
    public ResourceLocation id() {
        return ZoomModeRegistry.VANILLA;
    }

    @Override
    public Component displayName() {
        return Component.translatable("guizoom.mode.vanilla");
    }

    @Override
    public Component description() {
        return Component.translatable("guizoom.mode.vanilla.desc");
    }

    @Override
    public double minZoom() {
        return 1.0D;
    }

    @Override
    public double maxZoom() {
        // The vanilla option clamps itself to whatever fits on the current screen, so a generous
        // upper bound is safe: OptionInstance.ClampingLazyMaxIntRange silently clamps it.
        return 32.0D;
    }

    @Override
    public double defaultZoom() {
        return currentEffectiveScale();
    }

    @Override
    public double step() {
        return 1.0D;
    }

    @Override
    public boolean integerSteps() {
        return true;
    }

    @Override
    public double resetZoom() {
        return this.originalScale >= 0 ? this.originalScale : currentEffectiveScale();
    }

    @Override
    public void onZoomChanged(Minecraft minecraft, double zoom) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (this.originalScale < 0) {
            this.originalScale = minecraft.options.guiScale().get();
        }
        int wanted = Mth.clamp((int) Math.round(zoom), 0, 64);
        if (minecraft.options.guiScale().get() != wanted) {
            minecraft.options.guiScale().set(wanted);
        }
        // Deferred to the next frame: resizeDisplay() re-initialises the open screen, which must not
        // happen in the middle of mouse input dispatch.
        ZoomManager.get().requestResize();
    }

    @Override
    public void applyWindowScale(Minecraft minecraft, double zoom) {
        // 100% vanilla: re-evaluate the option, resize the window GUI scale, resize the screen and
        // let Forge resize its GUI layers.
        minecraft.resizeDisplay();
    }

    @Override
    public double readZoom(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return Double.NaN;
        }
        int value = minecraft.options.guiScale().get();
        // 0 means "auto": keep the value the manager holds instead of collapsing to the minimum.
        return value <= 0 ? Double.NaN : value;
    }

    @Override
    public Component zoomLabel(double zoom) {
        return Component.literal(Integer.toString((int) Math.round(zoom)));
    }

    @Override
    public void onDeactivate(Minecraft minecraft, double zoom) {
        if (this.originalScale >= 0 && ZoomConfig.restoreVanillaScale() && minecraft != null && minecraft.options != null) {
            if (minecraft.options.guiScale().get() != this.originalScale) {
                minecraft.options.guiScale().set(this.originalScale);
            }
            minecraft.resizeDisplay();
            this.originalScale = -1;
        }
    }

    private static double currentEffectiveScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 2.0D;
        }
        double scale = minecraft.getWindow().getGuiScale();
        return scale <= 0.0D ? 2.0D : Math.round(scale);
    }
}
