package studio.king220l.yamusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LyricsSettings {
    static final int OFFSET_LIMIT = 1_500;
    static final int MAX_UPCOMING = 10;
    static final int MIN_LINE_HEIGHT = 12;
    static final int MAX_LINE_HEIGHT = 22;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    enum Alignment {
        LEFT, CENTER, RIGHT;

        int inset(int available, int textWidth) {
            int space = Math.max(0, available - textWidth);
            return switch (this) {
                case LEFT -> 0;
                case CENTER -> space / 2;
                case RIGHT -> space;
            };
        }
    }

    enum Emphasis { GOLD, UNDERLINE }

    enum HudStyle {
        CARD("Карточка"), TEXT("Только текст"), OUTLINED("Золотой контур");

        final String label;

        HudStyle(String label) { this.label = label; }

        HudStyle next() {
            var styles = values();
            return styles[(ordinal() + 1) % styles.length];
        }
    }

    boolean hud;
    double x = .5, y = .82;
    int offsetMs;
    int hudSize = 100;
    int hudOpacity = 87;
    int upcoming = 1;
    boolean showPrevious = true;
    boolean showUpcoming = true;
    int lineHeight = 14;
    HudStyle hudStyle = HudStyle.CARD;
    Alignment alignment = Alignment.LEFT;
    Emphasis emphasis = Emphasis.GOLD;
    private transient Path path;

    static LyricsSettings load(Path file) {
        LyricsSettings settings = new LyricsSettings();
        if (Files.isRegularFile(file)) {
            try {
                var saved = GSON.fromJson(Files.readString(file), LyricsSettings.class);
                if (saved != null) settings = saved;
            } catch (IOException | RuntimeException ignored) {}
        }
        settings.path = file;
        settings.x = Double.isFinite(settings.x) ? Math.clamp(settings.x, 0, 1) : .5;
        settings.y = Double.isFinite(settings.y) ? Math.clamp(settings.y, 0, 1) : .82;
        settings.offsetMs = Math.clamp(settings.offsetMs, -OFFSET_LIMIT, OFFSET_LIMIT);
        settings.hudSize = Math.clamp(settings.hudSize, 60, 150);
        settings.hudOpacity = Math.clamp(settings.hudOpacity, 40, 100);
        settings.upcoming = Math.clamp(settings.upcoming, 1, MAX_UPCOMING);
        settings.lineHeight = Math.clamp(settings.lineHeight, MIN_LINE_HEIGHT, MAX_LINE_HEIGHT);
        if (settings.hudStyle == null) settings.hudStyle = HudStyle.CARD;
        if (settings.alignment == null) settings.alignment = Alignment.LEFT;
        if (settings.emphasis == null) settings.emphasis = Emphasis.GOLD;
        return settings;
    }

    void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("lyrics.tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            MusicMod.LOG.warn("Could not save lyrics settings", failure);
        }
    }
}
