package com.cookedfish.guizoom.api;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * A pluggable UI zoom strategy.
 * <p>
 * GUI Zoom ships with four implementations, but nothing in the mod is hard coded against them:
 * every mode is looked up through {@link ZoomModeRegistry}, appears automatically in the in game
 * settings screen and can be selected through the {@code mode} entry of the client config.
 * Third party mods can therefore add their own scheme by registering one of these in
 * {@code FMLClientSetupEvent}.
 *
 * <h2>The two families</h2>
 * <ul>
 *   <li>{@link #poseBased()} {@code == true}: a pure render time transform
 *       ({@link #getTransform}). The UI layout, the logical GUI size and every screen's widgets stay
 *       untouched, which is as compatible as it gets. {@code guizoom:magnifier} and
 *       {@code guizoom:center} work like this.</li>
 *   <li>{@link #poseBased()} {@code == false}: a "window" mode that changes the real GUI scale of the
 *       game ({@link #applyWindowScale}), exactly like the vanilla video settings slider does.
 *       {@code guizoom:vanilla} and {@code guizoom:fractional} work like this.</li>
 * </ul>
 *
 * @see ZoomModeRegistry
 */
public interface IZoomMode {

    /** Unique id, e.g. {@code guizoom:magnifier}. */
    ResourceLocation id();

    /** Name shown on the settings screen and in the zoom indicator. */
    Component displayName();

    /** Optional one line help text. */
    default Component description() {
        return Component.empty();
    }

    /** Smallest value {@link com.cookedfish.guizoom.zoom.ZoomManager} will store for this mode. */
    double minZoom();

    /** Largest value the manager will store (the global {@code maxZoom} config may lower it further). */
    double maxZoom();

    /** Value used the first time this mode becomes zoomable. 1.0 means "unchanged UI". */
    double defaultZoom();

    /** Zoom delta for one mouse wheel notch. */
    double step();

    /** When true the wheel walks integers (and the value is rounded), like vanilla's GUI scale. */
    default boolean integerSteps() {
        return false;
    }

    /** When true this mode renders through {@link #getTransform} instead of changing the GUI scale. */
    default boolean poseBased() {
        return false;
    }

    /** Value the reset key bind / reset button jumps to. */
    default double resetZoom() {
        return this.defaultZoom();
    }

    /** Called after the stored zoom value changed. */
    default void onZoomChanged(Minecraft minecraft, double zoom) {
    }

    /** Called when this mode becomes the active one. */
    default void onActivate(Minecraft minecraft, double zoom) {
    }

    /** Called when another mode takes over, so this one can undo whatever it changed. */
    default void onDeactivate(Minecraft minecraft, double zoom) {
    }

    /**
     * Pose based modes only: the transform to wrap the HUD / the current screen with.
     *
     * @param mouseX current pointer X in GUI scaled coordinates
     * @param mouseY current pointer Y in GUI scaled coordinates
     * @return {@code null} or {@link ZoomTransform#IDENTITY} to render unchanged
     */
    default ZoomTransform getTransform(Minecraft minecraft, double zoom, double mouseX, double mouseY) {
        return ZoomTransform.IDENTITY;
    }

    /**
     * Window modes only: (re)apply the real GUI scale. Called when the zoom changed, when a screen is
     * initialised and when the window was resized.
     */
    default void applyWindowScale(Minecraft minecraft, double zoom) {
    }

    /**
     * Window modes only: is the GUI scale already the one this mode wants? Returning false makes the
     * manager re-apply it (used to survive window resizes and vanilla settings changes).
     */
    default boolean isWindowScaleApplied(Minecraft minecraft, double zoom) {
        return true;
    }

    /**
     * Lets a mode report back the value the game actually accepted, e.g. the vanilla GUI scale option
     * clamps to whatever fits on screen.
     *
     * @return the effective value, or {@link Double#NaN} to keep the stored one
     */
    default double readZoom(Minecraft minecraft) {
        return Double.NaN;
    }

    /** Text used by the HUD indicator. */
    default Component zoomLabel(double zoom) {
        return Component.literal(String.format(Locale.ROOT, "%.2fx", zoom));
    }
}
