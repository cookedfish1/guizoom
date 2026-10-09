package com.cookedfish.guizoom.client;

import com.cookedfish.guizoom.GuiZoom;
import com.cookedfish.guizoom.ZoomConfig;
import com.cookedfish.guizoom.api.IZoomMode;
import com.cookedfish.guizoom.api.ZoomModeRegistry;
import com.cookedfish.guizoom.zoom.ScrollBehavior;
import com.cookedfish.guizoom.zoom.ZoomManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collection;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * In game settings screen.
 * <p>
 * The mode list is built from {@link ZoomModeRegistry}, so modes registered by other mods appear here
 * automatically. Every widget writes straight into the client config; the values are persisted by
 * Forge when the config is unloaded.
 */
public class ZoomSettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 24;
    private static final int FIRST_ROW_Y = 40;
    private static final int MAX_COLUMN_WIDTH = 150;

    private final Screen parent;

    /** Kept so the label can be re-synced if the mode changed behind the screen's back. */
    private CycleButton<IZoomMode> modeButton;

    public ZoomSettingsScreen(Screen parent) {
        super(Component.translatable("guizoom.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columnWidth = Math.max(60, Math.min(MAX_COLUMN_WIDTH, this.width / 2 - 10));
        int left = this.width / 2 - columnWidth - 5;
        int right = this.width / 2 + 5;

        // Row 1: the zoom scheme itself and how the wheel is shared with lists.
        this.modeButton = this.createModeButton(left, FIRST_ROW_Y, columnWidth);
        if (this.modeButton != null) {
            this.addRenderableWidget(this.modeButton);
        }
        this.addRenderableWidget(CycleButton
                .builder((ScrollBehavior behavior) -> behavior.displayName())
                .withValues(ScrollBehavior.values())
                .withInitialValue(ZoomConfig.scrollBehavior())
                .create(right, FIRST_ROW_Y, columnWidth, ROW_HEIGHT,
                        Component.translatable("guizoom.option.scroll_behavior"),
                        (button, behavior) -> ZoomConfig.setScrollBehavior(behavior)));

        // Row 2: step size and animation speed.
        this.addRenderableWidget(new ValueSlider(left, FIRST_ROW_Y + ROW_GAP, columnWidth, ROW_HEIGHT,
                Component.translatable("guizoom.option.zoom_step"), 0.05D, 1.0D, ZoomConfig.zoomStep(),
                ZoomConfig::setZoomStep));
        this.addRenderableWidget(new ValueSlider(right, FIRST_ROW_Y + ROW_GAP, columnWidth, ROW_HEIGHT,
                Component.translatable("guizoom.option.smoothing_speed"), 0.05D, 1.0D, ZoomConfig.smoothingSpeed(),
                ZoomConfig::setSmoothingSpeed));

        // Row 3.
        this.addRenderableWidget(this.createToggle(left, FIRST_ROW_Y + ROW_GAP * 2, columnWidth,
                "guizoom.option.invert_scroll", ZoomConfig.invertScroll(), ZoomConfig::setInvertScroll));
        this.addRenderableWidget(this.createToggle(right, FIRST_ROW_Y + ROW_GAP * 2, columnWidth,
                "guizoom.option.smooth_scrolling", ZoomConfig.smoothScrolling(), ZoomConfig::setSmoothScrolling));

        // Row 4.
        this.addRenderableWidget(this.createToggle(left, FIRST_ROW_Y + ROW_GAP * 3, columnWidth,
                "guizoom.option.show_indicator", ZoomConfig.showIndicator(), ZoomConfig::setShowIndicator));
        this.addRenderableWidget(this.createToggle(right, FIRST_ROW_Y + ROW_GAP * 3, columnWidth,
                "guizoom.option.zoom_in_game", ZoomConfig.zoomInGame(), ZoomConfig::setZoomInGame));

        // Row 5.
        this.addRenderableWidget(this.createToggle(left, FIRST_ROW_Y + ROW_GAP * 4, columnWidth,
                "guizoom.option.restore_vanilla", ZoomConfig.restoreVanillaScale(), ZoomConfig::setRestoreVanillaScale));
        this.addRenderableWidget(Button
                .builder(Component.translatable("guizoom.button.reset"), button -> ZoomManager.get().reset())
                .bounds(right, FIRST_ROW_Y + ROW_GAP * 4, columnWidth, ROW_HEIGHT)
                .build());

        // Bottom.
        this.addRenderableWidget(Button
                .builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, ROW_HEIGHT)
                .build());
    }

    private CycleButton<IZoomMode> createModeButton(int x, int y, int width) {
        Collection<IZoomMode> modes = ZoomModeRegistry.all();
        if (modes.isEmpty()) {
            // Cannot happen with the built in modes registered, but never build an empty cycle button.
            return null;
        }
        IZoomMode active = ZoomManager.get().activeMode();
        return CycleButton
                .builder((IZoomMode mode) -> mode.displayName())
                .withValues(modes)
                .withInitialValue(modes.contains(active) ? active : modes.iterator().next())
                .withTooltip(mode -> net.minecraft.client.gui.components.Tooltip.create(mode.description()))
                .create(x, y, width, ROW_HEIGHT, Component.translatable("guizoom.option.mode"),
                        (button, mode) -> {
                            ZoomConfig.setModeId(mode.id().toString());
                            ZoomManager.get().setActive(mode);
                        });
    }

    private CycleButton<Boolean> createToggle(int x, int y, int width, String translationKey, boolean initial,
            java.util.function.Consumer<Boolean> setter) {
        return CycleButton
                .onOffBuilder(initial)
                .create(x, y, width, ROW_HEIGHT, Component.translatable(translationKey),
                        (button, value) -> setter.accept(value));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // The key binds can switch the mode while this screen is open, and leaving a window based
        // mode re-initialises the screen mid transition. Re-sync the button so its label can never
        // disagree with the mode that is actually active.
        IZoomMode active = ZoomManager.get().activeMode();
        if (this.modeButton != null && this.modeButton.getValue() != active) {
            GuiZoom.diagnose("[GUI Zoom] mode button showed {} while {} was active - re-syncing",
                    this.modeButton.getValue().id(), active.id());
            this.modeButton.setValue(active);
        }

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("guizoom.screen.hint"),
                this.width / 2, this.height - 46, 0xA0A0A0);

        Component status = Component.translatable("guizoom.screen.status",
                active.displayName(), active.zoomLabel(ZoomManager.get().zoom()));
        guiGraphics.drawCenteredString(this.font, status, this.width / 2, this.height - 34, 0x55FF55);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** Minimal slider writing a double straight into the config. */
    private static final class ValueSlider extends AbstractSliderButton {

        private final Component label;
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;

        private ValueSlider(int x, int y, int width, int height, Component label, double min, double max, double value,
                DoubleConsumer onChange) {
            // The super constructor calls updateMessage() before these fields are assigned, hence the
            // null check in updateMessage().
            super(x, y, width, height, Component.empty(), Mth.clamp((value - min) / (max - min), 0.0D, 1.0D));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.updateMessage();
        }

        private double actualValue() {
            // Two decimals: the slider reports this straight into the config file, and
            // 0.2538088688380282 is not something anyone wants to read there.
            double raw = this.min + (this.max - this.min) * this.value;
            return Math.round(raw * 100.0D) / 100.0D;
        }

        @Override
        protected void updateMessage() {
            if (this.label == null) {
                return;
            }
            this.setMessage(Component.literal(String.format(Locale.ROOT, "%s: %.2f", this.label.getString(), this.actualValue())));
        }

        @Override
        protected void applyValue() {
            if (this.onChange != null) {
                this.onChange.accept(this.actualValue());
            }
        }
    }
}
