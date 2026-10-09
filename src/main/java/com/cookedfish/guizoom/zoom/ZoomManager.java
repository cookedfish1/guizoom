package com.cookedfish.guizoom.zoom;

import com.cookedfish.guizoom.GuiZoom;
import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.api.ZoomTransform;
import com.cookedfish.guizoom.zoom.mode.CenterZoomMode;
import com.cookedfish.guizoom.zoom.mode.FractionalScaleMode;
import com.cookedfish.guizoom.zoom.mode.MagnifierMode;
import com.cookedfish.guizoom.zoom.mode.VanillaScaleMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the active zoom mode and its value, and applies it either as a render time pose transform or
 * as a real GUI scale change.
 * <p>
 * Everything that mutates the GUI scale is deferred to the next frame
 * ({@link #updateWindowScale(Minecraft)} is called from the {@code RenderGuiEvent.Pre} hook): a screen
 * must never be re-initialised from inside mouse input dispatch.
 */
public final class ZoomManager {

    private static final ZoomManager INSTANCE = new ZoomManager();

    /** Only used if the registry is somehow empty, so the manager can never return null. */
    private static final IZoomMode FALLBACK = new MagnifierMode();

    private final Map<ResourceLocation, Double> zoomByMode = new LinkedHashMap<>();
    private final Set<ResourceLocation> failedModes = new HashSet<>();

    private IZoomMode resolvedMode;
    private double displayZoom = 1.0D;
    private double scrollAccumulator;
    private boolean resizePending;
    private boolean applyingWindowScale;
    private PoseStack pushedPose;
    private long lastChangeMillis;
    private ZoomManager() {
    }

    public static ZoomManager get() {
        return INSTANCE;
    }

    /** Registers the built in schemes. Called once from the mod constructor. */
    public void registerBuiltinModes() {
        ZoomModeRegistry.register(new VanillaScaleMode());
        ZoomModeRegistry.register(new MagnifierMode());
        ZoomModeRegistry.register(new FractionalScaleMode());
        ZoomModeRegistry.register(new CenterZoomMode());
    }

    // ------------------------------------------------------------------ mode

    public IZoomMode activeMode() {
        if (this.resolvedMode == null) {
            IZoomMode fromConfig = ZoomModeRegistry.byString(ZoomConfig.modeId());
            this.resolvedMode = fromConfig != null ? fromConfig : ZoomModeRegistry.byString(ZoomModeRegistry.defaultId().toString());
            if (this.resolvedMode == null) {
                this.resolvedMode = FALLBACK;
            }
            this.displayZoom = this.zoomOf(this.resolvedMode);
            this.scrollAccumulator = 0.0D;
        }
        return this.resolvedMode;
    }

    /** Switches to another registered mode, announcing the change to both modes. */
    public void setActive(IZoomMode mode) {
        if (mode == null) {
            return;
        }
        IZoomMode previous = this.activeMode();
        if (previous == mode) {
            this.requestResize();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiZoom.diagnose("[GUI Zoom] zoom mode {} -> {}", previous.id(), mode.id());

        // The new mode has to be installed BEFORE the old one is deactivated.
        // Deactivating a window based mode (guizoom:vanilla, guizoom:fractional) calls
        // Minecraft.resizeDisplay(), which re-initialises the open screen - and Screen.init() reads
        // activeMode() again (the settings screen builds its mode button from it). Installing the new
        // mode afterwards made any UI created in that window show the mode that was just left behind,
        // so the button lagged exactly one click behind the real mode.
        this.resolvedMode = mode;
        this.scrollAccumulator = 0.0D;
        this.displayZoom = this.zoomOf(mode);
        if (minecraft != null) {
            previous.onDeactivate(minecraft, this.zoomOf(previous));
            mode.onActivate(minecraft, this.zoomOf(mode));
        }
        this.resizePending = true;
    }

    /** Switches to another registered mode by id. */
    public void setActive(ResourceLocation id) {
        this.setActive(ZoomModeRegistry.get(id));
    }

    /** Jumps to the next registered mode, used by the cycle key bind. */
    public void cycleMode() {
        IZoomMode current = this.activeMode();
        IZoomMode next = current;
        boolean found = false;
        for (IZoomMode mode : ZoomModeRegistry.all()) {
            if (found) {
                next = mode;
                break;
            }
            if (mode == current) {
                found = true;
            }
        }
        if (next == current && !ZoomModeRegistry.all().isEmpty()) {
            next = ZoomModeRegistry.all().iterator().next();
        }
        ZoomConfig.setModeId(next.id().toString());
        this.setActive(next);
    }

    // ------------------------------------------------------------------ value

    /** Current zoom of the active mode. */
    public double zoom() {
        return this.zoomOf(this.activeMode());
    }

    public double zoomOf(IZoomMode mode) {
        Double stored = this.zoomByMode.get(mode.id());
        if (stored == null) {
            stored = this.clamp(mode, mode.defaultZoom());
            this.zoomByMode.put(mode.id(), stored);
        }
        return stored;
    }

    public void setZoom(double value) {
        this.setZoom(this.activeMode(), value);
    }

    public void setZoom(IZoomMode mode, double value) {
        double clamped = this.clamp(mode, value);
        double previous = this.zoomOf(mode);
        this.zoomByMode.put(mode.id(), clamped);
        if (Math.abs(previous - clamped) <= 1.0E-6D) {
            return;
        }
        this.lastChangeMillis = Util.getMillis();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        mode.onZoomChanged(minecraft, clamped);
        if (mode.poseBased()) {
            double readBack = mode.readZoom(minecraft);
            if (!Double.isNaN(readBack)) {
                this.zoomByMode.put(mode.id(), this.clamp(mode, readBack));
            }
        } else {
            // Window modes are applied right here, from inside the input event, exactly like the
            // vanilla GUI scale button does from inside its own click handler. One wheel notch must
            // always mean one visible change - never "scroll again to see it".
            this.applyWindowScaleNow(minecraft, mode);
        }
    }

    /** Back to the mode's neutral value, used by the reset key bind and the settings button. */
    public void reset() {
        IZoomMode mode = this.activeMode();
        this.scrollAccumulator = 0.0D;
        this.setZoom(mode, mode.resetZoom());
        this.resizePending = true;
    }

    /**
     * Consumes a wheel event.
     *
     * @param delta the scroll delta of the event
     * @return true when the zoom value actually changed, i.e. the event was used
     */
    public boolean handleScroll(double delta) {
        if (delta == 0.0D) {
            return false;
        }
        IZoomMode mode = this.activeMode();
        double direction = ZoomConfig.invertScroll() ? -delta : delta;
        double before = this.zoomOf(mode);

        if (mode.integerSteps()) {
            // Discrete wheels send whole notches, touch pads send many tiny deltas: accumulate and
            // only act on complete steps.
            this.scrollAccumulator += direction;
            int steps = (int) this.scrollAccumulator;
            if (steps == 0) {
                return false;
            }
            this.setZoom(mode, before + steps);
            this.scrollAccumulator -= steps;
        } else {
            this.scrollAccumulator = 0.0D;
            this.setZoom(mode, before + direction * Math.max(0.01D, mode.step()));
        }

        if (this.zoomOf(mode) == before) {
            return false;
        }
        this.lastChangeMillis = Util.getMillis();
        GuiZoom.diagnose("[GUI Zoom] wheel delta {} -> {} zoom {} (step {})", delta, mode.id(), this.zoomOf(mode), mode.step());
        return true;
    }

    // ------------------------------------------------------------------ render / apply

    /** Advances the smooth animation. Called once per frame before the GUI is drawn. */
    public void tickAnimation(Minecraft minecraft) {
        IZoomMode mode = this.activeMode();
        double target = this.zoomOf(mode);
        if (!mode.poseBased()) {
            this.displayZoom = target;
            return;
        }
        double difference = target - this.displayZoom;
        if (Math.abs(difference) < 1.0E-3D) {
            // Steady state: the common case, and it touches neither the config nor the animation.
            this.displayZoom = target;
            return;
        }
        if (ZoomConfig.smoothScrolling()) {
            this.displayZoom += difference * Mth.clamp(ZoomConfig.smoothingSpeed(), 0.05D, 1.0D);
        } else {
            this.displayZoom = target;
        }
    }

    /**
     * @return true when the pose hooks have anything to draw this frame. False in the normal
     *         "not zoomed" state and always for the window based modes, which lets both render hooks
     *         bail out after a single field check.
     */
    public boolean needsPoseTransform() {
        return this.activeMode().poseBased() && Math.abs(this.displayZoom - 1.0D) > 1.0E-4D;
    }

    /**
     * Safety net for window based modes: keeps the real GUI scale correct when something outside this
     * mod changed it - a window resize, the vanilla GUI scale slider, another zoom mode handing over.
     * Called once per frame before the GUI projection is computed; the normal path applies the scale
     * immediately in {@link #setZoom}, so this usually has nothing to do.
     */
    public void updateWindowScale(Minecraft minecraft) {
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        IZoomMode mode = this.activeMode();
        if (mode.poseBased() || this.applyingWindowScale) {
            this.resizePending = false;
            return;
        }
        double zoom = this.zoomOf(mode);
        boolean needed = this.resizePending || !mode.isWindowScaleApplied(minecraft, zoom);
        if (!needed) {
            return;
        }
        GuiZoom.diagnose("[GUI Zoom] re-applying window scale for {} (zoom {}, wanted {})",
                mode.id(), zoom, this.resizePending ? "pending" : "drifted");
        this.applyWindowScaleNow(minecraft, mode);
    }

    /** Applies the active window based mode right now and records what the game actually accepted. */
    private void applyWindowScaleNow(Minecraft minecraft, IZoomMode mode) {
        if (mode.poseBased() || this.applyingWindowScale) {
            return;
        }
        double zoom = this.zoomOf(mode);
        double previousScale = minecraft.getWindow().getGuiScale();
        this.applyingWindowScale = true;
        try {
            mode.applyWindowScale(minecraft, zoom);
        } catch (Throwable throwable) {
            // A broken mode must not take the whole GUI down, but it must be visible in the log.
            if (this.failedModes.add(mode.id())) {
                GuiZoom.LOGGER.warn("[GUI Zoom] Zoom mode {} failed to apply its window scale", mode.id(), throwable);
            }
        } finally {
            this.applyingWindowScale = false;
        }
        this.resizePending = false;
        double readBack = mode.readZoom(minecraft);
        if (!Double.isNaN(readBack)) {
            this.zoomByMode.put(mode.id(), this.clamp(mode, readBack));
        }
        GuiZoom.diagnose("[GUI Zoom] {} zoom {} -> gui scale {} (was {}, applied now: {})",
                mode.id(), zoom, minecraft.getWindow().getGuiScale(), previousScale,
                mode.isWindowScaleApplied(minecraft, zoom));
    }

    /** Marks the real GUI scale as out of date; applied on the next frame. */
    public void requestResize() {
        this.resizePending = true;
    }

    /** Called after any screen was initialised, so window based modes can keep their scale. */
    public void onScreenInit(Minecraft minecraft) {
        IZoomMode mode = this.activeMode();
        if (!mode.poseBased() && !mode.isWindowScaleApplied(minecraft, this.zoomOf(mode))) {
            this.resizePending = true;
        }
    }

    /**
     * The transform the active mode wants to render with right now.
     * <p>
     * Both the HUD and the screen render hook call this once per frame and pass the result on to
     * {@link #pushPose} and {@link #uiSpaceOf}, so a frame needs at most one transform instance and the
     * HUD and the screen always use the identical anchor even if the mouse moves in between.
     *
     * @param mouseX pointer X in GUI scaled coordinates (the anchor of cursor based modes)
     * @param mouseY pointer Y in GUI scaled coordinates
     */
    public ZoomTransform renderTransform(Minecraft minecraft, double mouseX, double mouseY) {
        IZoomMode mode = this.activeMode();
        if (!mode.poseBased() || minecraft == null) {
            return ZoomTransform.IDENTITY;
        }
        ZoomTransform transform = mode.getTransform(minecraft, this.displayZoom, mouseX, mouseY);
        return transform == null ? ZoomTransform.IDENTITY : transform;
    }

    /**
     * Maps a pointer position to the logical position of the widget that is drawn under it.
     * <p>
     * Needed for modes whose anchor is not the pointer: a uniform scale only keeps the anchor in
     * place, so everywhere else the drawn widget and the coordinates vanilla uses for hit testing
     * drift apart. The caller re-dispatches the input / render with the returned coordinates.
     *
     * @return the mapped coordinates, or {@code null} when no mapping is required
     */
    public double[] uiSpace(Minecraft minecraft, double mouseX, double mouseY) {
        return uiSpaceOf(this.renderTransform(minecraft, mouseX, mouseY), mouseX, mouseY);
    }

    /** {@link #uiSpace} for a transform that was already computed for this frame. */
    public static double[] uiSpaceOf(ZoomTransform transform, double mouseX, double mouseY) {
        if (transform == null || !transform.shiftsPointer(mouseX, mouseY)) {
            return null;
        }
        return new double[]{transform.inverseX(mouseX), transform.inverseY(mouseY)};
    }

    /**
     * Wraps the current GUI render in the given transform, if there is one.
     * <p>
     * The pushed {@link PoseStack} is remembered by identity, so a leaked push from an earlier frame
     * (a freshly created {@code GuiGraphics} each frame) can never cause an unbalanced pop.
     */
    public void pushPose(GuiGraphics guiGraphics, ZoomTransform transform) {
        if (guiGraphics == null || this.pushedPose != null || transform == null || transform.isIdentity()) {
            return;
        }
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        transform.apply(pose);
        this.pushedPose = pose;
    }

    /** Pops the transform pushed by {@link #pushPose}. Safe to call when nothing was pushed. */
    public void popPose(GuiGraphics guiGraphics) {
        PoseStack pose = guiGraphics == null ? null : guiGraphics.pose();
        PoseStack pushed = this.pushedPose;
        this.pushedPose = null;
        if (pushed != null && pushed == pose) {
            pushed.popPose();
        }
    }

    // ------------------------------------------------------------------ misc

    /** @return the value that is actually used for rendering right now. */
    public double displayZoom() {
        return this.activeMode().poseBased() ? this.displayZoom : this.zoom();
    }

    public long lastChangeMillis() {
        return this.lastChangeMillis;
    }

    /** True when the active mode currently changes the UI at all. */
    public boolean isZoomed() {
        IZoomMode mode = this.activeMode();
        return Math.abs(this.zoom() - mode.resetZoom()) > 1.0E-3D && Math.abs(this.zoom() - 1.0D) > 1.0E-3D;
    }

    /** Pointer X in GUI scaled coordinates, matching what vanilla hands to screens. */
    public static double mouseGuiX(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int width = window.getScreenWidth();
        return width <= 0 ? 0.0D : minecraft.mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) width;
    }

    /** Pointer Y in GUI scaled coordinates, matching what vanilla hands to screens. */
    public static double mouseGuiY(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int height = window.getScreenHeight();
        return height <= 0 ? 0.0D : minecraft.mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) height;
    }

    private double clamp(IZoomMode mode, double value) {
        double max = Math.min(mode.maxZoom(), ZoomConfig.maxZoom());
        double min = Math.min(mode.minZoom(), max);
        return Mth.clamp(value, min, max);
    }
}
