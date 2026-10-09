package com.cookedfish.guizoom;

import com.cookedfish.guizoom.zoom.ScrollBehavior;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config of GUI Zoom.
 * <p>
 * Every getter is defensive: the config may not have been loaded yet (or at all, e.g. on a client that
 * only has the class on the classpath), in which case the documented default is returned instead of
 * throwing.
 */
public final class ZoomConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.ConfigValue<String> MODE;
    private static final ForgeConfigSpec.DoubleValue ZOOM_STEP;
    private static final ForgeConfigSpec.DoubleValue MAX_ZOOM;
    private static final ForgeConfigSpec.ConfigValue<String> SCROLL_BEHAVIOR;
    private static final ForgeConfigSpec.BooleanValue INVERT_SCROLL;
    private static final ForgeConfigSpec.BooleanValue SMOOTH_SCROLLING;
    private static final ForgeConfigSpec.DoubleValue SMOOTHING_SPEED;
    private static final ForgeConfigSpec.BooleanValue SHOW_INDICATOR;
    private static final ForgeConfigSpec.BooleanValue ZOOM_IN_GAME;
    private static final ForgeConfigSpec.BooleanValue RESTORE_VANILLA_SCALE;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    private static final String DEFAULT_MODE = "guizoom:magnifier";

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "GUI Zoom (by cookedfish) - client side configuration.",
                "Zoom modes live in ZoomModeRegistry, so modes added by other mods appear here and in the",
                "in game settings screen as well.").push("general");

        MODE = builder
                .comment("Active zoom mode id.",
                        "guizoom:vanilla    - real vanilla GUI scale (integer steps, maximum compatibility)",
                        "guizoom:magnifier  - cursor anchored magnifier, layout untouched (default)",
                        "guizoom:fractional - continuous fractional GUI scale, layout reflows",
                        "guizoom:center     - centre anchored render zoom (addon reference implementation)")
                .define("mode", DEFAULT_MODE);

        ZOOM_STEP = builder
                .comment("Zoom change per mouse wheel notch for the continuous modes.")
                .defineInRange("zoomStep", 0.25D, 0.01D, 4.0D);

        MAX_ZOOM = builder
                .comment("Hard upper limit applied on top of each mode's own range.")
                .defineInRange("maxZoom", 8.0D, 1.0D, 64.0D);

        SCROLL_BEHAVIOR = builder
                .comment("ALWAYS   - the wheel always zooms; lists never scroll and buttons never change.",
                        "SMART    - the wheel belongs to the list or wheel reactive button under the pointer",
                        "           (lists scroll, also at their end); everywhere else it zooms.",
                        "HOLD_KEY - zooms only while the hold key (key.guizoom.hold, default C) is held down;",
                        "           without it the wheel behaves exactly like vanilla. Default.")
                .define("scrollBehavior", ScrollBehavior.HOLD_KEY.name(), value -> value instanceof String text && ScrollBehavior.isValid(text));

        INVERT_SCROLL = builder
                .comment("Invert the wheel direction used for zooming.")
                .define("invertScroll", false);

        SMOOTH_SCROLLING = builder
                .comment("Animate render zoom modes instead of jumping to the new value.")
                .define("smoothScrolling", true);

        SMOOTHING_SPEED = builder
                .comment("Animation speed of the smooth zooming, as the fraction of the remaining distance",
                        "covered per frame.")
                .defineInRange("smoothingSpeed", 0.35D, 0.05D, 1.0D);

        SHOW_INDICATOR = builder
                .comment("Show a short lived zoom indicator on the HUD after every change.")
                .define("showZoomIndicator", true);

        ZOOM_IN_GAME = builder
                .comment("Master switch for zooming the HUD while no screen is open.",
                        "Only the ALWAYS behaviour and the hold key use it, and both consume the wheel while",
                        "zooming (so it no longer switches hotbar slots). The SMART behaviour never zooms",
                        "in-game: there the hotbar keeps the wheel. Default: on.")
                .define("zoomInGame", true);

        RESTORE_VANILLA_SCALE = builder
                .comment("When leaving guizoom:vanilla, put the GUI scale option back to the value it had",
                        "before the first wheel zoom. Keep it false if you want the wheel to behave like a",
                        "second video settings slider that persists.")
                .define("restoreVanillaScaleOnExit", false);

        DEBUG_LOGGING = builder
                .comment("Write the zoom pipeline (used wheel events, wheel events left to the game, applied",
                        "GUI scales) to latest.log. Turn this on when something behaves unexpectedly and send",
                        "the log along - otherwise these lines only go to the developer console.")
                .define("debugLogging", false);

        builder.pop();
        SPEC = builder.build();
    }

    private ZoomConfig() {
    }

    public static String modeId() {
        return get(MODE, DEFAULT_MODE);
    }

    public static void setModeId(String id) {
        set(MODE, id);
    }

    public static double zoomStep() {
        return getDouble(ZOOM_STEP, 0.25D);
    }

    public static void setZoomStep(double value) {
        set(ZOOM_STEP, value);
    }

    public static double maxZoom() {
        return getDouble(MAX_ZOOM, 8.0D);
    }

    public static ScrollBehavior scrollBehavior() {
        return ScrollBehavior.byName(get(SCROLL_BEHAVIOR, ScrollBehavior.SMART.name()));
    }

    public static void setScrollBehavior(ScrollBehavior behavior) {
        set(SCROLL_BEHAVIOR, behavior.name());
    }

    public static boolean invertScroll() {
        return getBoolean(INVERT_SCROLL, false);
    }

    public static void setInvertScroll(boolean value) {
        set(INVERT_SCROLL, value);
    }

    public static boolean smoothScrolling() {
        return getBoolean(SMOOTH_SCROLLING, true);
    }

    public static void setSmoothScrolling(boolean value) {
        set(SMOOTH_SCROLLING, value);
    }

    public static double smoothingSpeed() {
        return getDouble(SMOOTHING_SPEED, 0.35D);
    }

    public static void setSmoothingSpeed(double value) {
        set(SMOOTHING_SPEED, value);
    }

    public static boolean showIndicator() {
        return getBoolean(SHOW_INDICATOR, true);
    }

    public static void setShowIndicator(boolean value) {
        set(SHOW_INDICATOR, value);
    }

    public static boolean zoomInGame() {
        return getBoolean(ZOOM_IN_GAME, false);
    }

    public static void setZoomInGame(boolean value) {
        set(ZOOM_IN_GAME, value);
    }

    public static boolean restoreVanillaScale() {
        return getBoolean(RESTORE_VANILLA_SCALE, false);
    }

    public static void setRestoreVanillaScale(boolean value) {
        set(RESTORE_VANILLA_SCALE, value);
    }

    public static boolean debugLogging() {
        return getBoolean(DEBUG_LOGGING, false);
    }

    public static void setDebugLogging(boolean value) {
        set(DEBUG_LOGGING, value);
    }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T loaded = value.get();
            return loaded == null ? fallback : loaded;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double getDouble(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static <T> void set(ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        try {
            value.set(newValue);
        } catch (Throwable ignored) {
            // Not loaded yet, or rejected by a validator: the in-memory state still applies.
        }
    }
}
