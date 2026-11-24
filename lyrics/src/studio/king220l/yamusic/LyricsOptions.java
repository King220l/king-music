package studio.king220l.yamusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

import static studio.king220l.yamusic.LyricsSettings.*;
import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.*;

final class LyricsOptions {
    private static final String LINE_HEIGHT = "Высота строки";
    private static final String UPCOMING = "Строк вперёд";
    private static final LyricsData SAMPLE = LyricsData.parse("""
        [00:00.00]Эту строку уже спели
        [00:01.00]Сейчас звучит эта строка
        [00:02.00]Дальше будет эта строка
        [00:03.00]Затем начинается новый куплет
        [00:04.00]Мы продолжаем петь
        [00:05.00]И снова слышим знакомый мотив
        [00:06.00]Так выглядит шестая строка
        [00:07.00]Текст идёт дальше
        [00:08.00]Ещё немного до припева
        [00:09.00]Следом за мелодией
        [00:10.00]И ещё одна строка
        [00:11.00]Последняя строка предпросмотра
        """);

    private LyricsOptions() {}

    static List<Preference> settings(PlayerScreen screen, LyricsAddon addon) {
        LyricsSettings settings = addon.appearance();
        return List.of(
            hudToggle(screen, settings, "Текст поверх игры", settings.hud, () -> settings.hud = !settings.hud),
            new Preference("Расположение HUD", row -> screen.valueButton(row, "Переместить", Icon.POSITION, false,
                () -> Minecraft.getInstance().setScreen(new Placement(screen.mod, screen, addon)))),
            new Preference("Оформление HUD", row -> screen.valueButton(row, settings.hudStyle.label, null, false,
                () -> change(screen, settings, () -> settings.hudStyle = settings.hudStyle.next()))),
            hudToggle(screen, settings, "Уже спетая строка", settings.showPrevious, () -> settings.showPrevious = !settings.showPrevious),
            hudToggle(screen, settings, "Следующие строки", settings.showUpcoming, () -> settings.showUpcoming = !settings.showUpcoming),
            new Preference(UPCOMING, row -> {
                Slider slider = new Slider(PlayerScreen.sliderArea(row), UPCOMING, "", settings.upcoming, 1, MAX_UPCOMING, true,
                    value -> settings.upcoming = value, settings::save).fineSteps();
                slider.active = settings.showUpcoming;
                screen.preferenceWidget(slider);
            }),
            new Preference(LINE_HEIGHT, row -> screen.preferenceWidget(new Slider(PlayerScreen.sliderArea(row), LINE_HEIGHT, " пкс",
                settings.lineHeight, MIN_LINE_HEIGHT, MAX_LINE_HEIGHT, true,
                value -> settings.lineHeight = value, settings::save).fineSteps())),
            new Preference("Выравнивание", row -> {
                alignment(screen, row, settings, Alignment.LEFT, "Слева", Icon.ALIGN_LEFT, row.right() - 88);
                alignment(screen, row, settings, Alignment.CENTER, "По центру", Icon.ALIGN_CENTER, row.right() - 60);
                alignment(screen, row, settings, Alignment.RIGHT, "Справа", Icon.ALIGN_RIGHT, row.right() - 32);
            }),
            new Preference("Текущая строка", row -> {
                boolean labels = row.w() >= 305;
                emphasis(screen, row, settings, Emphasis.GOLD, "Золото", Icon.GOLD_TEXT, labels, 63);
                emphasis(screen, row, settings, Emphasis.UNDERLINE, "Подчеркнуть", Icon.UNDERLINE, labels, 106);
            }),
            new Preference("Сдвиг строк", row -> offsetControls(screen, row, settings)));
    }

    private static Preference hudToggle(PlayerScreen screen, LyricsSettings settings, String label, boolean enabled, Runnable action) {
        return new Preference(label, row -> screen.preferenceWidget(new Toggle(
            new Rect(row.right() - 32, row.y() + 4, 28, 15), enabled, () -> change(screen, settings, action))).hint(label));
    }

    private static void alignment(PlayerScreen screen, Rect row, LyricsSettings settings, Alignment value, String name, Icon icon, int x) {
        screen.icon(new Rect(x, row.y() + 3, 26, row.h() - 6), name, icon,
            settings.alignment == value ? Style.SELECTED : Style.FILLED,
            () -> change(screen, settings, () -> settings.alignment = value));
    }

    private static void emphasis(PlayerScreen screen, Rect row, LyricsSettings settings, Emphasis value, String name, Icon icon,
                                 boolean labels, int width) {
        int x = row.right() - (labels ? (value == Emphasis.GOLD ? 176 : 110) : (value == Emphasis.GOLD ? 60 : 32));
        screen.button(new Rect(x, row.y() + 3, labels ? width : 26, row.h() - 6), name, icon,
            settings.emphasis == value ? Style.SELECTED : Style.FILLED, labels, false,
            () -> change(screen, settings, () -> settings.emphasis = value)).hint(name);
    }

    private static void offsetControls(PlayerScreen screen, Rect row, LyricsSettings settings) {
        int y = row.y() + 3, h = row.h() - 6, right = row.right() - 4;
        screen.icon(new Rect(right - 106, y, 20, h), "Позже на 25 мс", Icon.LEFT, Style.FILLED,
            () -> offset(screen, settings, -25)).active = settings.offsetMs > -OFFSET_LIMIT;
        screen.icon(new Rect(right - 20, y, 20, h), "Раньше на 25 мс", Icon.RIGHT, Style.FILLED,
            () -> offset(screen, settings, 25)).active = settings.offsetMs < OFFSET_LIMIT;
        String prefix = settings.offsetMs > 0 ? "+" : "";
        screen.button(new Rect(right - 83, y, 60, h), prefix + settings.offsetMs + " мс", null,
            Style.FILLED, true, false, () -> change(screen, settings, () -> settings.offsetMs = 0))
            .hint("Сбросить сдвиг");
    }

    private static void offset(PlayerScreen screen, LyricsSettings settings, int amount) {
        change(screen, settings, () -> settings.offsetMs = Math.clamp(settings.offsetMs + amount, -OFFSET_LIMIT, OFFSET_LIMIT));
    }

    private static void change(PlayerScreen screen, LyricsSettings settings, Runnable action) {
        action.run();
        settings.save();
        screen.refreshPreferences();
    }

    private static final class Placement extends HudPlacementScreen {
        private final LyricsSettings settings;
        private final LyricsHud hud;
        private final LyricsData displayedLyrics;
        private final int displayedCue;

        Placement(MusicMod mod, Screen parent, LyricsAddon addon) {
            super(mod, parent, "Положение HUD текста");
            settings = addon.appearance();
            hud = new LyricsHud(settings);
            LyricsData lyrics = addon.data();
            int cue = lyrics.cueAt(addon.displayPosition());
            displayedLyrics = cue < 0 ? SAMPLE : lyrics;
            displayedCue = cue < 0 ? 1 : cue;
        }

        @Override
        List<PlacementSlider> extraSliders() {
            var height = new PlacementSlider(LINE_HEIGHT, Icon.RESIZE, " пкс", settings.lineHeight,
                MIN_LINE_HEIGHT, MAX_LINE_HEIGHT, value -> settings.lineHeight = value);
            return settings.showUpcoming ? List.of(height, new PlacementSlider(UPCOMING, Icon.LIST, "", settings.upcoming,
                1, MAX_UPCOMING, value -> settings.upcoming = value)) : List.of(height);
        }

        @Override
        Rect previewBounds() {
            hud.prepare(font, displayedLyrics, displayedCue);
            return hud.bounds(width, height);
        }

        @Override
        void drawPreview(GuiGraphicsExtractor g) { hud.draw(g, font, previewBounds()); }

        @Override void movePreview(double x, double y) {
            settings.x = x;
            settings.y = y;
        }

        @Override void resetPosition() { movePreview(.5, .82); }
        @Override int sizePercent() { return settings.hudSize; }
        @Override void sizePercent(int value) { settings.hudSize = value; }
        @Override int opacityPercent() { return settings.hudOpacity; }
        @Override void opacityPercent(int value) { settings.hudOpacity = value; }
        @Override void save() { settings.save(); }
    }
}
