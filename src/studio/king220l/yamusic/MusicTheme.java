package studio.king220l.yamusic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import studio.king220l.yamusic.CompatColor;

final class MusicTheme {
    static final int BASE = 0xff171619;
    static final int RAIL = 0xff1c1b20;
    static final int SURFACE = 0xff242329;
    static final int ELEVATED = 0xff2e2c33;
    static final int HOVER = 0xff38353e;
    static final int BORDER = 0xff403d46;
    static final int BORDER_HOVER = 0xff615967;
    static final int TEXT = 0xfff5f2f5;
    static final int MUTED = 0xffb8b2be;
    static final int DIM = 0xff8f8796;
    static final int KING_GOLD = 0xffffd465;
    static final int INK = 0xff251e22;
    static final int ACCENT = KING_GOLD;
    static final int ACCENT_LIGHT = CompatColor.srgbLerp(.30f, ACCENT, TEXT);
    static final int ACCENT_SOFT = CompatColor.srgbLerp(.18f, ELEVATED, ACCENT);
    static final int ACCENT_HOVER = CompatColor.srgbLerp(.29f, HOVER, ACCENT);
    static final int ACCENT_EDGE = CompatColor.srgbLerp(.56f, BORDER, ACCENT);
    static final int ROSE = 0xfff286aa;
    private static final ResourceLocation EMBLEM = ResourceLocation.fromNamespaceAndPath("yamusic", "icon.png");
    private static final int[][] CORNERS = roundedCorners();

    enum Type {
        BODY("regular"), STRONG("bold"), TITLE("title"), HERO("hero");
        final ResourceLocation font;

        Type(String file) {
            font = ResourceLocation.fromNamespaceAndPath("yamusic", file);
        }
    }

    enum Icon {
        MUSIC(".......##/.....####/.....##../.....##../.....##../.....##../..#####../.######../..####..."),
        HOME("....#..../...###.../..#####../.#######./###.#.###/..#####../..##.##../..##.##../..##.##.."),
        SEARCH(".####..../##..##.../#....#.../#....#.../##..##.../.#####.../.....##../......##./.......#."),
        LIBRARY("##.##.#../##.##.##./##.##..#./##.##..##/##.##...#/##.##...#/##.##...#/##.##..../........."),
        WAVE("....#..../....#..../..#.#.#../..#.#.#../#.#.#.#.#/#.#.#.#.#/..#.#.#../....#..../........."),
        BARS("........./......##./...##.##./...##.##./##.##.##./##.##.##./##.##.##./##.##.##./........."),
        HEART(".##...##./#..#.#..#/#...#...#/#.......#/.#.....#./..#...#../...#.#.../....#..../........."),
        HEART_FILLED(".##...##./####.####/#########/#########/.#######./..#####../...###.../....#..../........."),
        CROWN("....#..../#..###..#/#..###..#/##..#..##/#########/.#######./.#######./........./.#######."),
        LIST("........./######.../........./######.../.......#./####...#./.......#./......##./........."),
        DISC("..#####../.##...##./##.....##/#...#...#/#..###..#/#...#...#/##.....##/.##...##./..#####.."),
        USER("...###.../..#...#../..#...#../...###.../........./..#####../.#.....#./.#.....#./.#.....#."),
        GEAR("...###.../.#.#.#.#./.#######./####.####/##.....##/####.####/.#######./.#.#.#.#./...###..."),
        HISTORY("..#####../.#.....#./#...#...#/#...#...#/#...###.#/#.......#/#.......#/.#.....#./..#####.."),
        CLOSE("#.......#/.#.....#./..#...#../...#.#.../....#..../...#.#.../..#...#../.#.....#./#.......#"),
        PLAY("..#....../..###..../..####.../..######./..#######/..######./..####.../..###..../..#......"),
        PAUSE("........./.##...##./.##...##./.##...##./.##...##./.##...##./.##...##./.##...##./........."),
        PREVIOUS("........./.#....#../.#...##../.#..###../.#.####../.#..###../.#...##../.#....#../........."),
        NEXT("........./..#....#./..##...#./..###..#./..####.#./..###..#./..##...#./..#....#./........."),
        SHUFFLE(".......#./......###/##...#.../..#.#..../...#...../..#.#..../##...#.../......###/.......#."),
        REPEAT("......#../.########/#.....#../#......../#.......#/........#/..#.....#/########./..#......"),
        SPEAKER("....#..../...##.#../#####..#./#####...#/#####...#/#####...#/.....#.#./......#../........."),
        MUTED("....#..../...##..../#####.#.#/#####..#./#####.#.#/#####..../....#..../........./........."),
        BACK("........./...#...../..#....../.#......./########./.#......./..#....../...#...../........."),
        LEFT("........./....#..../...#...../..#....../.#......./..#....../...#...../....#..../........."),
        RIGHT("........./....#..../.....#.../......#../.......#./......#../.....#.../....#..../........."),
        PLUS("........./....#..../....#..../....#..../.#######./....#..../....#..../....#..../........."),
        UP("....#..../...###.../..#.#.#../.#..#..#./....#..../....#..../....#..../........./........."),
        TRASH("...###.../#########/........./.#.#.#.#./.#.#.#.#./.#.#.#.#./.#.#.#.#./.#######./........."),
        KEY("........./.#######./#.......#/##.#.#.##/#.......#/##.###.##/#.......#/.#######./........."),
        SCREEN("#########/#.......#/#.......#/#.......#/#.......#/#########/....#..../..#####../........."),
        ALIGN_LEFT("#########/........./######.../........./#########/........./######.../........./#########"),
        ALIGN_CENTER("#########/........./..#####../........./#########/........./..#####../........./#########"),
        ALIGN_RIGHT("#########/........./...######/........./#########/........./...######/........./#########"),
        GOLD_TEXT("...###.../..##.##../..#...#../.##...##./.#######./.#.....#./##.....##/........./........."),
        UNDERLINE(".#.....#./.#.....#./.#.....#./.#.....#./.#.....#./..#####../........./........./#########"),
        POSITION("...###.../.#.....#./.#..#..#./...###.../..#####../...###.../.#..#..#./.#.....#./...###..."),
        RESIZE("####.####/#......##/#.....#.#/.....#.../....#..../...#...../#.#.....#/##......#/####.####"),
        OPACITY("....#..../...###.../..##.##../..#...#../.#....##./.#...###./.#..####./..#####../...###..."),
        REFRESH("..#####../.#.....#./#.......#/#....####/#.......#/####....#/#.......#/.#.....#./..#####..");

        private final int[][] spans = new int[9][];

        Icon(String pattern) {
            String[] rows = pattern.split("/");
            for (int i = 0; i < rows.length; i++) {
                String row = rows[i];
                int[] segments = new int[row.length() + 1];
                int j = 0;
                int count = 0;
                while (j < row.length()) {
                    if (row.charAt(j) != '#') {
                        j++;
                        continue;
                    }
                    segments[count++] = j++;
                    while (j < row.length() && row.charAt(j) == '#') j++;
                    segments[count++] = j;
                }
                spans[i] = java.util.Arrays.copyOf(segments, count);
            }
        }

        void draw(GuiGraphics g, int x, int y, int color) {
            for (int row = 0; row < spans.length; row++) {
                int[] line = spans[row];
                for (int i = 0; i < line.length; i += 2) {
                    g.fill(x + line[i], y + row, x + line[i + 1], y + row + 1, color);
                }
            }
        }
    }

    private MusicTheme() {
    }

    static Component styled(String value, Type type) {
        return Component.literal(MusicI18n.text(value)).withStyle(style -> style.withFont(type.font));
    }

    static int width(Font font, String value, Type type) {
        return font.width(styled(value, type));
    }

    static void text(GuiGraphics g, Font font, String value, int x, int y, int color, Type type, int available) {
        if (available <= 0 || value.isEmpty()) return;
        Component line = styled(value, type);
        if (font.width(line) > available) {
            Component ellipsis = styled("…", type);
            int limit = available - font.width(ellipsis);
            if (limit < 0) return;
            line = Component.empty().append(font.substrByWidth(line, limit).getString())
                .append("…").withStyle(style -> style.withFont(type.font));
        }
        g.drawString(font, line, x, y, color, false);
    }

    static void rounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (h > r * 2) g.fill(x, y + r, x + w, y + h - r, color);
        int start = 0;
        while (start < r) {
            int inset = cornerInset(r, start);
            int end = start + 1;
            while (end < r && cornerInset(r, end) == inset) end++;
            g.fill(x + inset, y + start, x + w - inset, y + end, color);
            g.fill(x + inset, y + h - end, x + w - inset, y + h - start, color);
            start = end;
        }
    }

    private static int cornerInset(int radius, int row) {
        return CORNERS[radius][row];
    }

    private static int[][] roundedCorners() {
        int[][] corners = new int[15][];
        for (int radius = 0; radius < corners.length; radius++) {
            int[] offsets = new int[radius];
            for (int row = 0; row < radius; row++) {
                double y = radius - row - .5;
                offsets[row] = Math.max(0, (int) Math.round(radius - Math.sqrt(radius * radius - y * y)));
            }
            corners[radius] = offsets;
        }
        return corners;
    }

    static void maskCorners(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        for (int row = 0; row < radius; row++) {
            int inset = cornerInset(radius, row);
            if (inset == 0) continue;
            g.fill(x, y + row, x + inset, y + row + 1, color);
            g.fill(x + w - inset, y + row, x + w, y + row + 1, color);
            g.fill(x, y + h - row - 1, x + inset, y + h - row, color);
            g.fill(x + w - inset, y + h - row - 1, x + w, y + h - row, color);
        }
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int radius, int rim, int fill) {
        rounded(g, x, y, w, h, radius, rim);
        rounded(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
    }

    static void emblem(GuiGraphics g, int x, int y, int size) {
        g.blit(RenderType::guiTextured, EMBLEM, x, y, 0f, 0f, size, size, 128, 128, 128, 128);
    }

    static void drawRecord(GuiGraphics g, int x, int y, int size, long positionMs) {
        long cycle = positionMs % 2600L;
        if (cycle == 0) {
            emblem(g, x, y, size);
            return;
        }
        float angle = cycle * (float) (Math.TAU / 2600);
        float cx = x + size * .5f, cy = y + size * .5f;
        var pose = CompatPose.of(g);
        pose.pushMatrix().translate(cx, cy).rotate(angle).translate(-cx, -cy);
        emblem(g, x, y, size);
        pose.popMatrix();
    }
}
