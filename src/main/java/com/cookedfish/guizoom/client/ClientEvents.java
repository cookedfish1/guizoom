package com.cookedfish.guizoom.client;

import com.cookedfish.guizoom.GuiZoom;
import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.ZoomTransform;
import com.cookedfish.guizoom.zoom.ScrollBehavior;
import com.cookedfish.guizoom.zoom.ZoomManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Every hook the mod uses, on the Forge event bus.
 *
 * <h2>Why these hooks</h2>
 * <ul>
 *   <li>{@code ScreenEvent.MouseScrolled.Pre} is fired by Forge from {@code MouseHandler}, <i>before</i>
 *       {@code Screen.mouseScrolled} is invoked. Cancelling it stops the wheel from ever reaching the
 *       widgets, not cancelling it leaves the screen in full control - which is how the three scroll
 *       behaviours are implemented.</li>
 *   <li>{@code RenderGuiEvent.Pre/Post} wrap {@code ForgeGui.render} (the whole HUD).</li>
 *   <li>{@code ScreenEvent.Render.Pre/Post} wrap {@code Screen.renderWithTooltip}, which is
 *       {@code final} in 1.20.1 - so one hook covers all screens, vanilla and modded alike, including
 *       item tooltips. Because Forge still posts {@code Render.Post} after a cancelled
 *       {@code Render.Pre}, the screen can be rendered manually with remapped pointer coordinates,
 *       which is what keeps centre anchored zooming click accurate without a single Mixin.</li>
 *   <li>{@code InputEvent.Key} only fires without an open screen, hence
 *       {@code ScreenEvent.KeyPressed.Pre} is used for the in-GUI key binds.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GuiZoom.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    /** Below this GUI width the extra "GUI Zoom" button would overlap the screen title. */
    private static final int MIN_WIDTH_FOR_BUTTON = 240;

    private ClientEvents() {
    }

    // ------------------------------------------------------------------ mouse wheel

    @SubscribeEvent
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }
        ZoomManager manager = ZoomManager.get();

        switch (ZoomConfig.scrollBehavior()) {
            case ALWAYS -> {
                // The wheel belongs to the zoom: lists never scroll and buttons never change.
                manager.handleScroll(delta);
                event.setCanceled(true);
            }
            case HOLD_KEY -> {
                // Key held: exactly like ALWAYS. Key not held: as if this mod were not installed.
                // The GLFW key state is only queried for this behaviour, not for every wheel event.
                if (isHoldKeyDown(minecraft)) {
                    manager.handleScroll(delta);
                    event.setCanceled(true);
                } else if (GuiZoom.isDiagnosing()) {
                    GuiZoom.diagnose("[GUI Zoom] wheel left to the screen ({} not held)",
                            ClientSetup.HOLD_ZOOM.getTranslatedKeyMessage().getString());
                }
            }
            case SMART -> {
                // Ask the screen first - the very same call vanilla would make. A list then scrolls
                // (also while it already sits at its end), a wheel reactive button cycles and every
                // modded scroll widget works, no matter which class implements it. Only when nothing
                // under the pointer wanted the wheel - i.e. empty space - does it become a zoom.
                boolean consumed = screen.mouseScrolled(event.getMouseX(), event.getMouseY(), delta);
                event.setCanceled(true);
                if (consumed) {
                    if (GuiZoom.isDiagnosing()) {
                        GuiZoom.diagnose("[GUI Zoom] wheel left to the screen (list or button under the pointer)");
                    }
                } else {
                    manager.handleScroll(delta);
                }
            }
        }
    }

    /**
     * In-game wheel (no screen open).
     * <p>
     * The scroll behaviour decides whether it zooms. In-game the hotbar is the thing that owns the
     * wheel - the same way a list owns it inside a screen - so {@code SMART} always leaves it alone and
     * the player keeps switching hotbar slots. Zooming the HUD therefore needs either {@code ALWAYS} or
     * the hold key, both still behind the {@code zoomInGame} master switch.
     */
    @SubscribeEvent
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null) {
            return;
        }
        boolean wanted = switch (ZoomConfig.scrollBehavior()) {
            case ALWAYS -> ZoomConfig.zoomInGame();
            case SMART -> false;
            case HOLD_KEY -> ZoomConfig.zoomInGame() && isHoldKeyDown(minecraft);
        };
        if (!wanted) {
            if (GuiZoom.isDiagnosing()) {
                GuiZoom.diagnose("[GUI Zoom] in-game wheel left to the hotbar ({})", ZoomConfig.scrollBehavior());
            }
            return;
        }
        if (ZoomManager.get().handleScroll(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    /**
     * @return true while the single hold key of the {@code HOLD_KEY} scroll behaviour is pressed
     */
    public static boolean isHoldKeyDown(Minecraft minecraft) {
        InputConstants.Key key = ClientSetup.HOLD_ZOOM.getKey();
        if (key == null || key == InputConstants.UNKNOWN || key.getType() != InputConstants.Type.KEYSYM) {
            return false;
        }
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
    }

    // ------------------------------------------------------------------ pointer mapping

    /*
     * Modes whose anchor is not the pointer (guizoom:center) draw a widget somewhere else than its
     * logical position. Input events and the render pass are therefore re-dispatched with the
     * pointer mapped back into the logical space. Everything here is a no-op for modes that anchor
     * on the cursor (guizoom:magnifier, guizoom:pixel) and for the window based modes, so the
     * handlers are inert in practice for the default configuration.
     *
     * LOWEST priority so that other mods see the original event first.
     */

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        double[] mapped = ZoomManager.get().uiSpace(minecraft, event.getMouseX(), event.getMouseY());
        if (mapped == null) {
            return;
        }
        event.setCanceled(true);
        screen.mouseClicked(mapped[0], mapped[1], event.getButton());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        double[] mapped = ZoomManager.get().uiSpace(minecraft, event.getMouseX(), event.getMouseY());
        if (mapped == null) {
            return;
        }
        event.setCanceled(true);
        screen.mouseReleased(mapped[0], mapped[1], event.getButton());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        ZoomTransform transform = ZoomManager.get().renderTransform(minecraft, event.getMouseX(), event.getMouseY());
        if (!transform.shiftsPointer(event.getMouseX(), event.getMouseY())) {
            return;
        }
        event.setCanceled(true);
        screen.mouseDragged(transform.inverseX(event.getMouseX()), transform.inverseY(event.getMouseY()),
                event.getMouseButton(), transform.inverseDelta(event.getDragX()), transform.inverseDelta(event.getDragY()));
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Window based modes change the real GUI scale, which changes the size of every screen. That must
     * happen <b>before</b> {@code GameRenderer.render} computes the GUI projection matrix, otherwise
     * the frame would be drawn with the old projection but the new layout. Forge fires this tick event
     * right before that render call, which makes it the correct place - and it is also safely outside
     * of input dispatch, so re-initialising the open screen is fine here.
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ZoomManager manager = ZoomManager.get();
        manager.tickAnimation(minecraft);
        manager.updateWindowScale(minecraft);
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ZoomManager manager = ZoomManager.get();
        // Fast path: while nothing is magnified (the normal case, and always for the window based
        // modes) the render hooks cost one field check and nothing else.
        if (!manager.needsPoseTransform()) {
            return;
        }
        ZoomTransform transform = manager.renderTransform(minecraft, ZoomManager.mouseGuiX(minecraft), ZoomManager.mouseGuiY(minecraft));
        manager.pushPose(event.getGuiGraphics(), transform);
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        ZoomManager.get().popPose(event.getGuiGraphics());
        // With a screen open the indicator is drawn later, on top of that screen instead.
        if (Minecraft.getInstance() != null && Minecraft.getInstance().screen == null) {
            ZoomIndicator.render(event.getGuiGraphics());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        ZoomManager manager = ZoomManager.get();
        if (!manager.needsPoseTransform()) {
            return;
        }
        double mouseX = ZoomManager.mouseGuiX(minecraft);
        double mouseY = ZoomManager.mouseGuiY(minecraft);
        // Computed once per frame and reused for both the pose and the pointer mapping below.
        ZoomTransform transform = manager.renderTransform(minecraft, mouseX, mouseY);
        manager.pushPose(event.getGuiGraphics(), transform);

        // Vanilla uses the raw pointer position to decide which widget is hovered and which tooltip
        // to show. For a non cursor anchored zoom that is the wrong widget, so the screen is rendered
        // here with the mapped coordinates instead. Forge still posts Render.Post afterwards, so the
        // pose pushed above is popped as usual. Anchored-on-the-pointer modes map to themselves and
        // take the untouched path.
        double[] mapped = ZoomManager.uiSpaceOf(transform, mouseX, mouseY);
        if (mapped != null) {
            event.setCanceled(true);
            screen.renderWithTooltip(event.getGuiGraphics(), (int) Math.round(mapped[0]), (int) Math.round(mapped[1]),
                    event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        ZoomManager.get().popPose(event.getGuiGraphics());
        // Topmost layer of the open GUI: after the screen, its widgets and its tooltips.
        ZoomIndicator.render(event.getGuiGraphics());
    }

    // ------------------------------------------------------------------ screens

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        // Keeps window based modes alive across window resizes and option screen visits.
        ZoomManager.get().onScreenInit(minecraft);

        // Discoverability: a "GUI Zoom" button in the top right corner of the vanilla video settings.
        if (screen instanceof VideoSettingsScreen && screen.width >= MIN_WIDTH_FOR_BUTTON) {
            event.addListener(Button.builder(
                            Component.translatable("guizoom.button.settings"),
                            button -> Minecraft.getInstance().setScreen(new ZoomSettingsScreen(screen)))
                    .bounds(screen.width - 112, 6, 106, 20)
                    .tooltip(Tooltip.create(Component.translatable("guizoom.button.tooltip")))
                    .build());
        }
    }

    // ------------------------------------------------------------------ keys

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (minecraft == null || screen == null) {
            return;
        }
        if (handleKey(minecraft, screen, event.getKeyCode(), true)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null) {
            return;
        }
        handleKey(minecraft, null, event.getKey(), false);
    }

    private static boolean handleKey(Minecraft minecraft, Screen screen, int keyCode, boolean inScreen) {
        if (keyCode == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        // Never steal keys from a focused text field (chat, anvil, book, sign, ...).
        if (inScreen && isTextInputFocused(screen)) {
            return false;
        }
        if (matches(ClientSetup.RESET_ZOOM, keyCode)) {
            ZoomManager.get().reset();
            drainClicks();
            return true;
        }
        if (matches(ClientSetup.CYCLE_MODE, keyCode)) {
            ZoomManager.get().cycleMode();
            drainClicks();
            return true;
        }
        // "Open settings" is in-game only: inside a GUI the key would compete with text input,
        // and the video settings button is right there.
        if (!inScreen && matches(ClientSetup.OPEN_SETTINGS, keyCode)) {
            minecraft.setScreen(new ZoomSettingsScreen(null));
            drainClicks();
            return true;
        }
        return false;
    }

    private static boolean matches(KeyMapping mapping, int keyCode) {
        return mapping != null && mapping.getKey().getValue() == keyCode;
    }

    /** True while a vanilla text widget has the keyboard focus. */
    private static boolean isTextInputFocused(Screen screen) {
        if (screen == null) {
            return false;
        }
        var focused = screen.getFocused();
        return focused instanceof EditBox || focused instanceof MultiLineEditBox;
    }

    /** Key bind clicks are handled manually, so the vanilla counters are cleared again. */
    private static void drainClicks() {
        while (ClientSetup.OPEN_SETTINGS.consumeClick()) {
            // drain
        }
        while (ClientSetup.RESET_ZOOM.consumeClick()) {
            // drain
        }
        while (ClientSetup.CYCLE_MODE.consumeClick()) {
            // drain
        }
    }
}
