package studio.king220l.yamusic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import studio.king220l.yamusic.CompatColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.Rect;

final class LyricsHud {
    private static final int WIDTH = 248;
    private static final int SIDE_PAD = 12;
    private static final int VERTICAL_PAD = 6;
    private static final int AVAILABLE = WIDTH - 2 * SIDE_PAD;
    private static final int OUTLINE_GOLD = 0xffa06b2c;
    private final LyricsSettings settings;
    private Font measuredFont;
    private LyricsData measuredData;
    private int measuredCue = -1, measuredUpcoming;
    private boolean measuredPrevious;
    private int rows = 3;
    private List<Caption> captions = List.of();

    private enum Role { PREVIOUS, CURRENT, UPCOMING }
    private record Caption(FormattedCharSequence words, int width, int row, Role role) {}

    LyricsHud(LyricsSettings settings) { this.settings = settings; }

    void prepare(Font font, LyricsData data, int cue) {
        int upcoming = settings.showUpcoming ? settings.upcoming : 0;
        if (font == measuredFont && data == measuredData && cue == measuredCue
                && upcoming == measuredUpcoming && settings.showPrevious == measuredPrevious) return;
        measuredFont = font;
        measuredData = data;
        measuredCue = cue;
        measuredUpcoming = upcoming;
        measuredPrevious = settings.showPrevious;
        var next = new ArrayList<Caption>();
        int row = measuredPrevious ? addCaption(next, font, data.cueWords(cue - 1), 0, Role.PREVIOUS) : 0;
        String current = data.cueWords(cue);
        row = addCaption(next, font, current.isBlank() ? "♪" : current, row, Role.CURRENT);
        for (int i = 1; i <= upcoming; i++) {
            row = addCaption(next, font, data.cueWords(cue + i), row, Role.UPCOMING);
        }
        rows = row;
        captions = List.copyOf(next);
    }

    private static int addCaption(List<Caption> captions, Font font, String words, int row, Role role) {
        if (words.isBlank()) return row + 1;
        var lines = font.split(styled(words, role == Role.CURRENT ? Type.STRONG : Type.BODY), AVAILABLE);
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            captions.add(new Caption(line, font.width(line), row + i, role));
        }
        return row + Math.max(1, lines.size());
    }

    private int height() { return rows * settings.lineHeight + 2 * VERTICAL_PAD; }

    Rect bounds(int width, int height) {
        int cardHeight = height();
        float scale = Math.min(settings.hudSize / 100f,
            Math.min(Math.max(1, width - 12) / (float) WIDTH, Math.max(1, height - 12) / (float) cardHeight));
        return HudPlacementScreen.bounds(width, height, Math.round(WIDTH * scale),
            Math.round(cardHeight * scale), settings.x, settings.y);
    }

    void draw(GuiGraphics g, Font font, Rect box) {
        if (box.w() < 1 || box.h() < 1) return;
        var pose = CompatPose.of(g);
        pose.pushMatrix().translate(box.x(), box.y()).scale(box.w() / (float) WIDTH, box.h() / (float) height());
        boolean card = settings.hudStyle == LyricsSettings.HudStyle.CARD;
        boolean outlined = settings.hudStyle == LyricsSettings.HudStyle.OUTLINED;
        float opacity = settings.hudOpacity / 100f;
        if (card) outline(g, 0, 0, WIDTH, height(), 7, CompatColor.multiplyAlpha(0xff514448, opacity),
            CompatColor.multiplyAlpha(0xff17151a, opacity));
        boolean underline = settings.emphasis == LyricsSettings.Emphasis.UNDERLINE;
        int edge = CompatColor.multiplyAlpha(OUTLINE_GOLD, opacity * .72f);
        for (Caption caption : captions) {
            int x = SIDE_PAD + settings.alignment.inset(AVAILABLE, caption.width);
            int y = VERTICAL_PAD + caption.row * settings.lineHeight + (settings.lineHeight - 10) / 2;
            int color = switch (caption.role) {
                case PREVIOUS -> 0xffaaa4af;
                case CURRENT -> underline ? TEXT : ACCENT_LIGHT;
                case UPCOMING -> outlined || !card ? 0xffeee9ef : MUTED;
            };
            var words = caption.words;
            if (outlined) {
                g.drawString(font, words, x - 1, y, edge, false);
                g.drawString(font, words, x + 1, y, edge, false);
                g.drawString(font, words, x, y - 1, edge, false);
                g.drawString(font, words, x, y + 1, edge, false);
            }
            g.drawString(font, words, x, y, card ? color : CompatColor.multiplyAlpha(color, opacity), false);
            if (underline && caption.role == Role.CURRENT && caption.width > 0) {
                g.fill(x, y + 10, x + caption.width, y + 11, card ? ACCENT : CompatColor.multiplyAlpha(ACCENT, opacity));
            }
        }
        pose.popMatrix();
    }
}
