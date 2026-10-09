package com.cookedfish.guizoom.client;

import com.cookedfish.guizoom.GuiZoom;
import com.cookedfish.guizoom.zoom.ScrollBehavior;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Key binds and the mod bus registration. */
@Mod.EventBusSubscriber(modid = GuiZoom.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    public static final String CATEGORY = "key.categories.guizoom";

    /** Opens the in game settings screen while no screen is open. */
    public static final KeyMapping OPEN_SETTINGS =
            new KeyMapping("key.guizoom.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** Back to the neutral value of the active mode. */
    public static final KeyMapping RESET_ZOOM =
            new KeyMapping("key.guizoom.reset", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CATEGORY);

    /**
     * Hold key of the {@code HOLD_KEY} scroll behaviour: while it is held the wheel zooms instead of
     * scrolling. Defaults to C.
     */
    public static final KeyMapping HOLD_ZOOM =
            new KeyMapping("key.guizoom.hold", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    /** Walks through all registered zoom modes. Unbound by default. */
    public static final KeyMapping CYCLE_MODE =
            new KeyMapping("key.guizoom.cycle_mode", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // The settings UI shows this key in the name of the HOLD_KEY scroll behaviour, so it must be
        // read live from the key bind rather than hard coded.
        ScrollBehavior.setHoldKeyNameSupplier(HOLD_ZOOM::getTranslatedKeyMessage);

        event.register(OPEN_SETTINGS);
        event.register(RESET_ZOOM);
        event.register(HOLD_ZOOM);
        event.register(CYCLE_MODE);
    }
}
