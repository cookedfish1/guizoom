package com.cookedfish.guizoom.zoom;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Supplier;

/** How the mouse wheel in a GUI is shared between scrolling and zooming. */
public enum ScrollBehavior {

    /** The wheel always zooms: lists never scroll and buttons never change. */
    ALWAYS,
    /**
     * Default. The wheel belongs to the widget under the pointer: inside a scrollable list it only
     * scrolls that list (even when the list is already at its end) and on a wheel reactive button it
     * only changes that button. Everywhere else - the empty space around and between widgets - the
     * wheel zooms.
     */
    SMART,
    /**
     * Zooming only while a single hold key (default {@code C}, rebindable) is held down; the wheel is
     * then consumed exactly like in {@link #ALWAYS}. Without the key the wheel behaves as if this mod
     * were not installed at all.
     */
    HOLD_KEY;

    /** Legacy config value of {@link #HOLD_KEY}, still accepted so old config files keep working. */
    private static final String LEGACY_HOLD_NAME = "MODIFIER_ONLY";

    /** Supplies the (localised, live) name of the hold key for the settings UI. */
    private static Supplier<Component> holdKeyName = () -> Component.literal("C");

    /**
     * Called by the client setup once the key bind exists, so the mode name can read the key the
     * player actually bound ("Hold X") instead of a hard coded letter.
     */
    public static void setHoldKeyNameSupplier(Supplier<Component> supplier) {
        holdKeyName = supplier == null ? () -> Component.literal("C") : supplier;
    }

    /** @return the behaviour for a config value, or {@code null} when the name is unknown. */
    private static ScrollBehavior byNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        for (ScrollBehavior behavior : values()) {
            if (behavior.name().equalsIgnoreCase(name)) {
                return behavior;
            }
        }
        return LEGACY_HOLD_NAME.equalsIgnoreCase(name) ? HOLD_KEY : null;
    }

    public static boolean isValid(String name) {
        return byNameOrNull(name) != null;
    }

    public static ScrollBehavior byName(String name) {
        ScrollBehavior behavior = byNameOrNull(name);
        return behavior == null ? SMART : behavior;
    }

    public ScrollBehavior next() {
        ScrollBehavior[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /** Name shown on the settings screen; the hold key mode reads the current key bind live. */
    public Component displayName() {
        if (this == HOLD_KEY) {
            return Component.translatable("guizoom.scroll_behavior.hold", holdKeyName.get());
        }
        return Component.translatable("guizoom.scroll_behavior." + this.name().toLowerCase(Locale.ROOT));
    }
}
