package com.cookedfish.guizoom;

import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.client.ZoomSettingsScreen;
import com.cookedfish.guizoom.zoom.ZoomManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * GUI Zoom - mouse wheel driven zooming for every GUI, for Minecraft 1.20.1 on Forge.
 *
 * <h2>Design notes</h2>
 * The mod deliberately contains <b>no mixins and no access transformers</b>. Compatibility with other
 * mods was the main design goal, so every scheme is built either on the vanilla code path itself or on
 * public Forge events:
 *
 * <ul>
 *   <li>{@code guizoom:vanilla} writes {@code Options.guiScale} and calls {@code Minecraft.resizeDisplay()},
 *       i.e. literally the code behind the vanilla video settings slider.</li>
 *   <li>{@code guizoom:magnifier} and {@code guizoom:center} scale the GUI pose stack from
 *       {@code RenderGuiEvent} / {@code ScreenEvent.Render}. Because {@code Screen.renderWithTooltip}
 *       is {@code final}, one hook covers every screen - vanilla or modded - while leaving the layout
 *       and the logical GUI size completely untouched.</li>
 *   <li>{@code guizoom:fractional} calls {@code Window.setGuiScale(double)} plus the same two
 *       resize calls {@code Minecraft.resizeDisplay()} makes.</li>
 * </ul>
 *
 * Input arrives through {@code ScreenEvent.MouseScrolled.Pre} (which is fired by Forge from
 * {@code MouseHandler} before the screen ever sees the event) and {@code InputEvent.MouseScrollingEvent}
 * for the HUD.
 */
@Mod(GuiZoom.MODID)
public final class GuiZoom {

    public static final String MODID = "guizoom";

    public static final Logger LOGGER = LogUtils.getLogger();

    public GuiZoom(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, ZoomConfig.SPEC);
        ZoomManager.get().registerBuiltinModes();

        // "Config" button of the mod list entry.
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ZoomSettingsScreen(parent)));

        LOGGER.info("[GUI Zoom] loaded - {} zoom mode(s) available", ZoomModeRegistry.all().size());
    }

    /**
     * @return true when {@link #diagnose} would actually write something, so callers can skip building
     *         the arguments entirely
     */
    public static boolean isDiagnosing() {
        return LOGGER.isDebugEnabled() || ZoomConfig.debugLogging();
    }

    /**
     * Diagnostic logging of the zoom pipeline: which wheel events were used, which were left to the
     * game, and what GUI scale a mode applied.
     * <p>
     * Written at DEBUG (visible in the developer console, silent for normal players) unless the
     * {@code debugLogging} config option is enabled, which raises it to INFO so the lines end up in
     * {@code latest.log} and can be attached to a bug report.
     */
    public static void diagnose(String message, Object... args) {
        // The free check first: reading the config costs more than the message itself.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(message, args);
        } else if (ZoomConfig.debugLogging()) {
            LOGGER.info(message, args);
        }
    }
}
