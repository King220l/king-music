package studio.king220l.yamusic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.*;

abstract class HudPlacementScreen extends MusicScreen {
    private static final int MARGIN = 6;
    private static final int SNAP_DISTANCE = 13;
    private static final int FIRST_SLIDER = 33;
    private static final int ROW_STEP = 24;
    private static final int ANCHOR_INSET = 12;
    private enum Drag { NONE, HUD, TOOLBAR }
    private Rect toolbar;
    private List<PlacementSlider> sliders = List.of();
    private boolean collapsed;
    private Drag drag = Drag.NONE;
    private double dx, dy;

    HudPlacementScreen(MusicMod mod, Screen parent, String title) {
        super(mod, parent, title);
    }

    abstract Rect previewBounds();
    abstract void drawPreview(GuiGraphicsExtractor g);
    abstract void movePreview(double x, double y);
    abstract void resetPosition();
    abstract int sizePercent();
    abstract void sizePercent(int value);
    abstract int opacityPercent();
    abstract void opacityPercent(int value);
    abstract void save();

    void previewControls(int x, int y) {}

    record PlacementSlider(String label, Icon icon, String suffix, int value, int min, int max, IntConsumer apply) {}

    List<PlacementSlider> extraSliders() { return List.of(); }

    final Rect previewArea() { return new Rect(0, 0, width, height); }

    @Override
    protected final void init() {
        sliders = new ArrayList<>(List.of(
            new PlacementSlider("Размер HUD", Icon.RESIZE, "%", sizePercent(), 60, 150, this::sizePercent),
            new PlacementSlider("Непрозрачность", Icon.OPACITY, "%", opacityPercent(), 40, 100, this::opacityPercent)));
        sliders.addAll(extraSliders());
        layoutToolbar();
        int right = toolbar.right();
        previewControls(right - 142, toolbar.y() + 7);
        icon(new Rect(right - 116, toolbar.y() + 7, 21, 20), collapsed ? "Развернуть панель" : "Свернуть панель",
            collapsed ? Icon.PLUS : Icon.UP, Style.GHOST, () -> {
                collapsed = !collapsed;
                rebuildWidgets();
            });
        icon(new Rect(right - 90, toolbar.y() + 7, 21, 20), "Сбросить положение", Icon.REFRESH, Style.GHOST, () -> {
            resetPosition();
            save();
            rebuildWidgets();
        });
        button(new Rect(right - 63, toolbar.y() + 7, 55, 20), "Готово", null, Style.PRIMARY, true, false, this::onClose);
        if (collapsed) return;
        int labelSpace = Math.min(111, toolbar.w() / 2);
        int sliderX = toolbar.x() + labelSpace;
        int sliderW = right - sliderX - 9;
        for (int i = 0; i < sliders.size(); i++) {
            PlacementSlider option = sliders.get(i);
            addRenderableWidget(new Slider(new Rect(sliderX, toolbar.y() + FIRST_SLIDER + i * ROW_STEP, sliderW, 18),
                option.label, option.suffix, option.value, option.min, option.max, true, option.apply, this::save).fineSteps());
        }
    }

    private void layoutToolbar() {
        int h = collapsed ? 35 : 39 + ROW_STEP * sliders.size();
        int w = Math.min(344, width - 14);
        if (toolbar != null) {
            toolbar = new Rect(fitToolbar(toolbar.x(), width, w), fitToolbar(toolbar.y(), height, h), w, h);
        } else {
            Rect box = previewBounds();
            int x = box.x() + box.w() / 2 < width / 2 ? width - w - 8 : 8;
            int y = box.y() + box.h() / 2 < height / 2 ? height - h - 8 : 8;
            toolbar = new Rect(fitToolbar(x, width, w), fitToolbar(y, height, h), w, h);
        }
    }

    private static int fitToolbar(int value, int screen, int panel) {
        return Math.clamp(value, 0, Math.max(0, screen - panel));
    }

    private void moveToolbar(double x, double y) {
        int left = fitToolbar((int) Math.round(x), width, toolbar.w());
        int top = fitToolbar((int) Math.round(y), height, toolbar.h());
        int deltaX = left - toolbar.x(), deltaY = top - toolbar.y();
        if (deltaX == 0 && deltaY == 0) return;
        toolbar = new Rect(left, top, toolbar.w(), toolbar.h());
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.setX(widget.getX() + deltaX);
                widget.setY(widget.getY() + deltaY);
            }
        }
    }

    private Rect titleGrip() { return new Rect(toolbar.x() + 5, toolbar.y() + 4, toolbar.w() - 148, 25); }

    static Rect bounds(int width, int height, int w, int h, double x, double y) {
        return new Rect(MARGIN + (int) Math.round(free(width, w) * x),
            MARGIN + (int) Math.round(free(height, h) * y), w, h);
    }

    static double[] magnet(int width, int height, int w, int h, double left, double top) {
        int freeX = free(width, w), freeY = free(height, h);
        double x = Math.clamp((left - MARGIN) / Math.max(1, freeX), 0, 1);
        double y = Math.clamp((top - MARGIN) / Math.max(1, freeY), 0, 1);
        double anchorX = Math.round(x * 2) / 2d, anchorY = Math.round(y * 2) / 2d;
        return Math.hypot((x - anchorX) * freeX, (y - anchorY) * freeY) <= SNAP_DISTANCE
            ? new double[]{anchorX, anchorY} : new double[]{x, y};
    }

    private static int free(int screen, int card) { return Math.max(0, screen - card - MARGIN * 2); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (toolbar.contains(event.x(), event.y())) {
            if (event.y() < toolbar.y() + FIRST_SLIDER) startDrag(Drag.TOOLBAR, toolbar, event);
            return true;
        }
        Rect box = previewBounds();
        if (box.contains(event.x(), event.y())) {
            startDrag(Drag.HUD, box, event);
            return true;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (Math.abs(event.x() - marker(width, col)) > 8 || Math.abs(event.y() - marker(height, row)) > 8) continue;
                movePreview(col / 2d, row / 2d);
                save();
                return true;
            }
        }
        return false;
    }

    private void startDrag(Drag target, Rect box, MouseButtonEvent event) {
        drag = target;
        dx = event.x() - box.x();
        dy = event.y() - box.y();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        if (drag == Drag.NONE || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseDragged(event, x, y);
        if (drag == Drag.TOOLBAR) {
            moveToolbar(event.x() - dx, event.y() - dy);
            return true;
        }
        Rect box = previewBounds();
        double[] next = magnet(width, height, box.w(), box.h(), event.x() - dx, event.y() - dy);
        movePreview(next[0], next[1]);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag == Drag.NONE || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseReleased(event);
        if (drag == Drag.HUD) save();
        drag = Drag.NONE;
        return true;
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    private boolean drawGrid(GuiGraphicsExtractor g, Rect preview) {
        boolean snapped = false;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Rect anchor = bounds(width, height, preview.w(), preview.h(), x / 2d, y / 2d);
                int cx = marker(width, x), cy = marker(height, y);
                boolean selected = anchor.x() == preview.x() && anchor.y() == preview.y();
                snapped |= selected;
                int color = selected ? ACCENT : 0xff9a8285;
                rounded(g, cx - 8, cy - 8, 17, 17, 6, 0xaa28232b);
                g.fill(cx - 4, cy, cx + 5, cy + 1, color);
                g.fill(cx, cy - 4, cx + 1, cy + 5, color);
            }
        }
        return snapped;
    }

    private static int marker(int dimension, int index) {
        return ANCHOR_INSET + (dimension - 2 * ANCHOR_INSET) * index / 2;
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        extractBackground(g, mx, my, dt);
        Rect preview = previewBounds();
        boolean snapped = drawGrid(g, preview);
        drawPreview(g);
        if (drag == Drag.HUD && snapped) {
            g.fill(preview.x() + 5, preview.y() - 2, preview.right() - 5, preview.y() - 1, ACCENT);
            g.fill(preview.x() + 5, preview.bottom() + 1, preview.right() - 5, preview.bottom() + 2, ACCENT);
        }
        g.nextStratum();
        outline(g, toolbar.x(), toolbar.y(), toolbar.w(), toolbar.h(), 8, BORDER, BASE);
        Rect grip = titleGrip();
        if (drag == Drag.TOOLBAR || drag == Drag.NONE && grip.contains(mx, my)) {
            rounded(g, grip.x(), grip.y(), grip.w(), grip.h(), 6, drag == Drag.TOOLBAR ? ACCENT_SOFT : RAIL);
            if (drag == Drag.NONE) CompatUi.tooltip(g, font, Component.literal("Перетащи панель за заголовок"), mx, my);
        }
        Icon.POSITION.draw(g, toolbar.x() + 10, toolbar.y() + 12, ACCENT);
        text(g, font, getTitle().getString(), toolbar.x() + 25, toolbar.y() + 12, TEXT, Type.STRONG,
            toolbar.w() - 172);
        for (int i = 0; !collapsed && i < sliders.size(); i++) {
            PlacementSlider option = sliders.get(i);
            int y = toolbar.y() + FIRST_SLIDER + i * ROW_STEP + 5;
            option.icon.draw(g, toolbar.x() + 10, y, MUTED);
            text(g, font, option.label, toolbar.x() + 25, y, TEXT, Type.BODY, Math.min(111, toolbar.w() / 2) - 28);
        }
        super.extractRenderState(g, mx, my, dt);
    }
}
