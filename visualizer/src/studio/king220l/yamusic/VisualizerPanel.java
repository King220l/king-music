package studio.king220l.yamusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import studio.king220l.yamusic.CompatColor;

import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.Rect;

final class VisualizerPanel extends AbstractWidget {
    private static final int SEGMENTS = 48;
    private static final int GRID = 0xff30272b;
    private static final int PLOT = 0xff17151a;
    private static final int AMBER = 0xffbb7837;
    private static final float[] SIN = new float[SEGMENTS + 1];
    private static final float[] COS = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.TAU * i / SEGMENTS - Math.PI / 2;
            SIN[i] = (float) Math.sin(angle);
            COS[i] = (float) Math.cos(angle);
        }
    }

    private final VisualizerAddon addon;
    private final float[] levels = new float[AudioVisualizer.BAND_COUNT];
    private final float[] peaks = new float[AudioVisualizer.BAND_COUNT];
    private VisualizerAddon.Frame lastWave;
    private long drawnAt;
    private float waveOpacity, wavePeak, pulse;

    VisualizerPanel(VisualizerAddon addon, Rect bounds) {
        super(bounds.x(), bounds.y(), bounds.w(), bounds.h(), Component.literal("График звука"));
        this.addon = addon;
        active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        int x = getX(), y = getY();
        outline(g, x, y, width, height, 8, BORDER, RAIL);
        if (width < 46 || height < 34) return;
        Rect chart = new Rect(x + 7, y + 7, width - 14, height - 14);
        rounded(g, chart.x(), chart.y(), chart.w(), chart.h(), 6, PLOT);
        Rect plot = new Rect(chart.x() + 7, chart.y() + 7, chart.w() - 14, chart.h() - 14);
        g.fillGradient(plot.x(), plot.y(), plot.right(), plot.bottom(), 0x002c2022, 0x66352a21);

        long now = System.nanoTime();
        float elapsed = drawnAt == 0 ? .016f : (float) Math.clamp((now - drawnAt) / 1e9, 0, .1);
        drawnAt = now;
        var mode = addon.mode();
        var frame = addon.readAudio(mode.input, now);
        float[] values = frame == null ? null : frame.values();
        if (mode.input != VisualizerAddon.Input.WAVEFORM) updateLevels(values, elapsed);
        Font font = Minecraft.getInstance().font;
        CompatPose.scissor(g, plot.x(), plot.y(), plot.right(), plot.bottom());
        switch (mode) {
            case BARS -> {
                drawGrid(g, plot);
                drawBars(g, plot);
            }
            case WAVE -> {
                drawGrid(g, plot);
                drawWave(g, plot, frame, elapsed);
            }
            case RING -> drawRing(g, plot);
            case PULSE -> drawPulse(g, plot, elapsed);
            case STEREO -> drawStereo(g, font, plot);
        }
        g.disableScissor();
        if (!addon.hasTrack()) {
            String hint = "Включи трек";
            int w = Math.min(plot.w() - 10, width(font, hint, Type.BODY));
            int left = plot.x() + (plot.w() - w) / 2, middle = plot.y() + plot.h() / 2;
            rounded(g, left - 6, middle - 9, w + 12, 18, 5, 0xff252028);
            text(g, font, hint, left, middle - 4, MUTED, Type.BODY, w);
        }
    }

    private void updateLevels(float[] values, float elapsed) {
        for (int i = 0; i < levels.length; i++) {
            float target = values == null || i >= values.length ? 0 : values[i];
            levels[i] = ease(levels[i], target, elapsed, target > levels[i] ? 29 : 11);
            peaks[i] = Math.max(levels[i], peaks[i] - elapsed * .65f);
        }
    }

    private static void drawGrid(GuiGraphics g, Rect plot) {
        for (int row = 1; row < 5; row++) {
            int y = plot.y() + row * plot.h() / 5;
            g.fill(plot.x(), y, plot.right(), y + 1, GRID);
        }
        for (int col = 1; col < 10; col++) {
            int x = plot.x() + col * plot.w() / 10;
            g.fill(x, plot.y(), x + 1, plot.bottom(), GRID);
        }
    }

    private void drawBars(GuiGraphics g, Rect plot) {
        int x = plot.x() + 5, w = plot.w() - 10;
        int bottom = plot.bottom() - 3, travel = plot.h() - 7;
        int gap = Math.clamp(w / (levels.length * 4), 1, 6);
        for (int i = 0; i < levels.length; i++) {
            int left = x + i * w / levels.length + gap / 2;
            int right = x + (i + 1) * w / levels.length - (gap + 1) / 2;
            if (right <= left) continue;
            int h = Math.max(1, Math.round(levels[i] * travel));
            int color = CompatColor.srgbLerp(Math.min(1, levels[i] * 1.9f), 0xff69543b, ACCENT_LIGHT);
            g.fillGradient(left, bottom - h, right, bottom, color, 0xff7b502a);
            int peak = bottom - Math.round(peaks[i] * travel);
            if (peak < bottom - h) g.fill(left, peak, right, peak + 1, 0xffbd9361);
        }
    }

    private void drawWave(GuiGraphics g, Rect plot, VisualizerAddon.Frame frame, float elapsed) {
        float middle = plot.y() + plot.h() * .5f;
        int axis = Math.round(middle);
        g.fill(plot.x(), axis, plot.right(), axis + 1, 0xff69543b);
        if (frame != null && frame != lastWave) {
            float amplitude = 0;
            for (float value : frame.values()) amplitude = Math.max(amplitude, Math.abs(value));
            wavePeak = Math.max(amplitude, wavePeak * .94f);
            lastWave = frame;
        }
        waveOpacity = ease(waveOpacity, frame == null ? 0 : 1, elapsed, 15);
        if (lastWave == null || wavePeak < .0001f || waveOpacity < .015f) return;
        float[] values = lastWave.values();
        float radius = (plot.h() * .45f) * Math.min(8, .84f / wavePeak) * waveOpacity;
        int segments = Math.min(values.length - 1, Math.max(1, plot.w() / 2));
        float lastX = plot.x(), lastY = middle - values[0] * radius;
        int glow = CompatColor.multiplyAlpha(0xffb77b3e, .33f * waveOpacity);
        int color = CompatColor.multiplyAlpha(ACCENT_LIGHT, waveOpacity);
        for (int i = 1; i <= segments; i++) {
            float x = plot.x() + (float) i * (plot.w() - 1) / segments;
            float y = middle - values[i * (values.length - 1) / segments] * radius;
            line(g, lastX, lastY, x, y, 4, glow);
            line(g, lastX, lastY, x, y, 2, color);
            lastX = x;
            lastY = y;
        }
    }

    private void drawRing(GuiGraphics g, Rect plot) {
        float x = plot.x() + plot.w() * .5f, y = plot.y() + plot.h() * .5f;
        float outer = Math.min(plot.w(), plot.h()) * .47f, inner = outer * .50f;
        ring(g, x, y, outer, 1, GRID);
        ring(g, x, y, inner - 4, 1, 0xff51402e);
        for (int i = 0; i < SEGMENTS; i++) {
            int step = Math.min(i, SEGMENTS - i);
            float level = levels[(step * (levels.length - 1)) / (SEGMENTS / 2)];
            float radius = inner + 2 + (outer - inner - 2) * level;
            int color = CompatColor.srgbLerp(level, 0xff614a35, ACCENT_LIGHT);
            line(g, x + COS[i] * inner, y + SIN[i] * inner, x + COS[i] * radius, y + SIN[i] * radius, 2, color);
        }
        ring(g, x, y, inner * .40f, 1, 0xff5a4633);
        rounded(g, Math.round(x) - 2, Math.round(y) - 2, 5, 5, 2, ACCENT);
    }

    private void drawPulse(GuiGraphics g, Rect plot, float elapsed) {
        float x = plot.x() + plot.w() * .5f, y = plot.y() + plot.h() * .5f;
        float radius = Math.min(plot.w(), plot.h()) * .45f;
        float energy = (levels[0] + levels[1] + levels[2] + levels[3]) * .25f;
        pulse = ease(pulse, energy, elapsed, energy > pulse ? 35 : 6);
        ring(g, x, y, radius, 1, GRID);
        float center = radius * (.19f + pulse * .36f);
        for (int i = 3; i > 0; i--) {
            float echo = .10f + pulse * (.65f - i * .14f);
            ring(g, x, y, center + (radius - center) * i / 3.4f, i == 1 ? 2 : 1,
                CompatColor.multiplyAlpha(ACCENT_LIGHT, echo));
        }
        ring(g, x, y, center, 3, CompatColor.srgbLerp(pulse, 0xff8d6642, ACCENT_LIGHT));
        if (center >= 6) {
            int size = Math.max(4, Math.round(center * 1.35f));
            emblem(g, Math.round(x - size * .5f), Math.round(y - size * .5f), size);
        }
    }

    private void drawStereo(GuiGraphics g, Font font, Rect plot) {
        int area = Math.min(plot.h(), 80);
        int rowH = Math.clamp(area / 4, 5, 20);
        int left = plot.x() + 22, available = plot.w() - 26;
        int count = Math.clamp(available / 6, 4, 25);
        for (int row = 0; row < 2; row++) {
            int y = plot.y() + (plot.h() - (2 * rowH + 8)) / 2 + row * (rowH + 8);
            text(g, font, row == 0 ? "L" : "R", plot.x() + 5, y + (rowH - 9) / 2, MUTED, Type.STRONG, 14);
            int lit = Math.round(levels[row] * count);
            int peak = Math.round(peaks[row] * count) - 1;
            for (int i = 0; i < count; i++) {
                int x = left + i * available / count;
                int w = Math.max(1, left + (i + 1) * available / count - x - 2);
                int litColor = CompatColor.srgbLerp((float) i / (count - 1), AMBER, ACCENT_LIGHT);
                rounded(g, x, y, w, rowH, 2, i < lit ? litColor : i == peak ? 0xff997954 : GRID);
            }
        }
    }

    private static void ring(GuiGraphics g, float x, float y, float radius, int weight, int color) {
        for (int i = 1; i <= SEGMENTS; i++) {
            line(g, x + COS[i - 1] * radius, y + SIN[i - 1] * radius,
                x + COS[i] * radius, y + SIN[i] * radius, weight, color);
        }
    }

    private static void line(GuiGraphics g, float x, float y, float endX, float endY, int weight, int color) {
        float dx = endX - x, dy = endY - y;
        int length = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy)));
        var pose = CompatPose.of(g);
        pose.pushMatrix().translate(x, y).rotate((float) Math.atan2(dy, dx));
        g.fill(0, -weight / 2, length, (weight + 1) / 2, color);
        pose.popMatrix();
    }

    private static float ease(float previous, float target, float elapsed, float rate) {
        return target + (previous - target) * (float) Math.exp(-elapsed * rate);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}

