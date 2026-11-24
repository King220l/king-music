package studio.king220l.yamusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;

import static studio.king220l.yamusic.MusicTheme.*;

final class LyricsPanel extends AbstractWidget {
    private static final int PAD = 14;
    private static final int LINE_HEIGHT = 16;
    private final MusicMod mod;
    private final LyricsAddon addon;
    private LyricsData shown;
    private List<Row> rows = List.of();
    private int totalHeight;
    private double scroll, manualPosition;
    private long manualUntil;
    private int activeLine = -1;

    private record Row(int top, int height, List<FormattedCharSequence> words) {
        int bottom() { return top + height; }
    }

    LyricsPanel(MusicMod mod, LyricsAddon addon, MusicUi.Rect bounds) {
        super(bounds.x(), bounds.y(), bounds.w(), bounds.h(), Component.literal("Текст песни"));
        this.mod = mod;
        this.addon = addon;
    }

    private void layout(Font font, LyricsData data) {
        shown = data;
        var measured = new ArrayList<Row>();
        int y = 3;
        for (var line : data.lines) {
            var wrapped = font.split(styled(line.words(), Type.STRONG), Math.max(12, width - PAD * 2 - 20));
            int height = Math.max(26, wrapped.size() * LINE_HEIGHT + 8);
            measured.add(new Row(y, height, wrapped));
            y += height;
        }
        rows = List.copyOf(measured);
        totalHeight = y + 3;
        activeLine = -1;
        manualUntil = 0;
        scroll = 0;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        outline(g, x, y, width, height, 8, BORDER, RAIL);
        LyricsData data = addon.data();
        if (shown != data) layout(font, data);
        if (rows.isEmpty()) {
            text(g, font, addon.status(), x + PAD, y + Math.min(24, height / 3), MUTED, Type.BODY, width - PAD * 2);
            return;
        }
        int viewport = Math.max(0, height - 30);
        int listY = y + 25;
        boolean manual = !data.synced() || Util.getMillis() < manualUntil;
        String mode = data.synced() ? manual ? "Прокрутка" : "Синхронный текст" : "Текст без таймингов";
        text(g, font, mode, x + PAD, y + 9, MUTED, Type.BODY, width - PAD * 2);
        if (viewport <= 0) return;
        int selected = data.at(addon.displayPosition());
        double target = manualPosition;
        if (!manual) {
            Row row = selected < 0 ? null : rows.get(selected);
            target = row == null ? 0 : row.top + row.height * .5 - viewport * .44;
        }
        target = Math.clamp(target, 0, Math.max(0, totalHeight - viewport));
        if (activeLine < 0 && selected >= 0 && Math.abs(target - scroll) > viewport) scroll = target;
        activeLine = selected;
        scroll += (target - scroll) * .24f;
        int offset = (int) Math.round(scroll);
        var appearance = addon.appearance();
        int lineWidth = Math.max(12, width - PAD * 2 - 20);
        CompatPose.scissor(g, x + 2, listY, x + width - 2, listY + viewport);
        for (int i = firstAt(offset); i < rows.size(); i++) {
            Row row = rows.get(i);
            int yy = listY + row.top - offset;
            if (yy >= listY + viewport) break;
            boolean current = i == selected;
            boolean hover = my >= Math.max(listY, yy) && my < Math.min(listY + viewport, yy + row.height)
                && mx >= x + 6 && mx < x + width - 6;
            boolean seekable = data.lines.get(i).timeMs() >= 0 && mod.player.canSeek() && !mod.follows();
            if (hover && seekable) {
                rounded(g, x + 6, yy, width - 12, row.height, 5, HOVER);
                Icon.PLAY.draw(g, x + width - 21, yy + (row.height - 9) / 2, ACCENT);
            }
            int wordY = yy + (row.height - row.words.size() * LINE_HEIGHT) / 2 + 3;
            int color = current && appearance.emphasis == LyricsSettings.Emphasis.GOLD ? ACCENT_LIGHT
                : selected >= 0 && i < selected ? DIM : TEXT;
            for (var words : row.words) {
                int pixels = font.width(words);
                int left = x + (width - lineWidth) / 2 + appearance.alignment.inset(lineWidth, pixels);
                g.drawString(font, words, left, wordY, color, false);
                if (current && appearance.emphasis == LyricsSettings.Emphasis.UNDERLINE && pixels > 0)
                    g.fill(left, wordY + 10, left + pixels, wordY + 11, ACCENT);
                wordY += LINE_HEIGHT;
            }
        }
        g.disableScissor();
    }

    private int firstAt(int position) {
        int low = 0, high = rows.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (rows.get(mid).bottom() <= position) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (!isMouseOver(x, y) || rows.isEmpty() || delta == 0) return false;
        long now = Util.getMillis();
        double previous = shown.synced() && now >= manualUntil ? scroll : manualPosition;
        manualPosition = Math.clamp(previous - delta * 42, 0, Math.max(0, totalHeight - height + 30));
        manualUntil = now + 4500;
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean twice) {
        var track = mod.player.track();
        if (track == null || mod.follows() || !mod.player.canSeek() || shown == null || shown != addon.data()) return;
        int y = (int) event.y() - getY();
        if (y < 25 || y >= height - 5) return;
        int i = firstAt(y - 25 + (int) Math.round(scroll));
        if (i < rows.size() && shown.lines.get(i).timeMs() >= 0) {
            manualUntil = 0;
            mod.player.seek(track, addon.cuePosition(shown.lines.get(i).timeMs()));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
