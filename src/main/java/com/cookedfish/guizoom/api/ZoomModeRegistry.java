package com.cookedfish.guizoom.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of every known zoom scheme.
 * <p>
 * The built in modes are registered by {@code ZoomManager#registerBuiltinModes()} while the mod is
 * constructed. Other mods may add their own during {@code FMLClientSetupEvent}:
 *
 * <pre>{@code
 * @Mod.EventBusSubscriber(modid = "example", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
 * public final class ExampleClient {
 *     @SubscribeEvent
 *     public static void setup(FMLClientSetupEvent event) {
 *         ZoomModeRegistry.register(new MyZoomMode());
 *     }
 * }
 * }</pre>
 *
 * Registration is intentionally order independent: an unknown {@code mode} value in the config simply
 * falls back to a built in mode, so a missing optional mod never breaks the game.
 */
public final class ZoomModeRegistry {

    /** Scheme 1 - the vanilla GUI scale. */
    public static final ResourceLocation VANILLA = builtin("vanilla");
    /** Scheme 2 - cursor anchored magnifier ("pixel zoom"). */
    public static final ResourceLocation MAGNIFIER = builtin("magnifier");
    /** Scheme 3 - continuous fractional GUI scale (a reserved alternative to the vanilla integer one). */
    public static final ResourceLocation FRACTIONAL = builtin("fractional");
    /** Scheme 3b - screen centre anchored smooth zoom, also the reference implementation for addons. */
    public static final ResourceLocation CENTER = builtin("center");

    private static final Map<ResourceLocation, IZoomMode> MODES = new LinkedHashMap<>();

    private ZoomModeRegistry() {
    }

    /**
     * Adds a zoom mode.
     *
     * @throws IllegalArgumentException if the same id was already registered by a different instance
     */
    public static synchronized void register(IZoomMode mode) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(mode.id(), "mode id");
        IZoomMode previous = MODES.get(mode.id());
        if (previous != null && previous != mode) {
            throw new IllegalArgumentException("Duplicate GUI Zoom mode id: " + mode.id());
        }
        MODES.put(mode.id(), mode);
    }

    /** @return the mode with this exact id, or {@code null}. */
    public static synchronized IZoomMode get(ResourceLocation id) {
        return id == null ? null : MODES.get(id);
    }

    /**
     * Lenient lookup used for the config value: accepts {@code guizoom:magnifier}, {@code magnifier}
     * and surrounding whitespace.
     *
     * @return the matching mode, or {@code null} when nothing matches
     */
    public static synchronized IZoomMode byString(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id.trim());
        if (key != null) {
            IZoomMode exact = MODES.get(key);
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<ResourceLocation, IZoomMode> entry : MODES.entrySet()) {
                if (entry.getKey().getPath().equals(key.getPath())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /** @return every registered mode, in registration order. */
    public static synchronized Collection<IZoomMode> all() {
        return List.copyOf(MODES.values());
    }

    /** @return the id used when nothing is configured or the configured id is unknown. */
    public static synchronized ResourceLocation defaultId() {
        return MODES.containsKey(MAGNIFIER) ? MAGNIFIER : (MODES.isEmpty() ? MAGNIFIER : MODES.keySet().iterator().next());
    }

    /** Convenience factory for {@code guizoom} namespaced ids. */
    public static ResourceLocation id(String path) {
        return builtin(path);
    }

    private static ResourceLocation builtin(String path) {
        ResourceLocation id = ResourceLocation.tryBuild("guizoom", path);
        if (id == null) {
            throw new IllegalStateException("Invalid GUI Zoom mode id: guizoom:" + path);
        }
        return id;
    }
}
