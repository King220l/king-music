package studio.king220l.yamusic;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class MusicShortcuts {
    private static final int MODIFIERS = InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT
        | InputConstants.MOD_ALT | InputConstants.MOD_SUPER;
    private static final Binding NONE = new Binding(InputConstants.UNKNOWN.getValue(), 0);

    enum Action {
        PLAY("Воспроизвести / пауза", "play", ctrl(InputConstants.KEY_P)),
        PREVIOUS("Предыдущий трек", "prev", NONE),
        NEXT("Следующий трек", "next", ctrl(InputConstants.KEY_N)),
        HUD("Показать / скрыть HUD", "hud", NONE),
        VOLUME_UP("Громче", "", NONE),
        VOLUME_DOWN("Тише", "", NONE),
        MUTE("Включить / выключить звук", "", NONE),
        LYRICS_HUD("Показать / скрыть HUD текста", "", NONE),
        EDITOR_BACK("Назад на 0,25 с", "", NONE),
        EDITOR_FORWARD("Вперёд на 0,25 с", "", NONE),
        EDITOR_STOP("Стоп и к началу", "", NONE),
        EDITOR_MARK("Отметить время строки", "", NONE),
        EDITOR_UNDO("Отменить последнюю метку", "", NONE),
        EDITOR_SLOWER("Уменьшить скорость", "", NONE),
        EDITOR_FASTER("Увеличить скорость", "", NONE),
        EDITOR_PREVIOUS_LINE("Выбрать предыдущую строку", "", NONE),
        EDITOR_NEXT_LINE("Выбрать следующую строку", "", NONE);

        final String label;
        final String legacyName;
        final Binding defaultBinding;

        Action(String label, String legacyName, Binding defaultBinding) {
            this.label = label;
            this.legacyName = legacyName;
            this.defaultBinding = defaultBinding;
        }

        boolean editorOnly() { return ordinal() >= EDITOR_BACK.ordinal(); }
        boolean requiresLyrics() { return this == LYRICS_HUD || editorOnly(); }
        boolean worksInEditor() { return editorOnly() || this == PLAY || this == HUD || this == LYRICS_HUD; }
    }

    record Binding(int key, int modifiers, boolean mouse) {
        Binding(int key, int modifiers) {
            this(key, modifiers, false);
        }

        static Binding of(KeyEvent event) {
            return new Binding(event.key(), normalizedModifiers(event.modifiers()));
        }

        static Binding of(MouseButtonInfo event) {
            return new Binding(event.button(), normalizedModifiers(event.modifiers()), true);
        }

        boolean assigned() {
            return mouse ? key >= InputConstants.MOUSE_BUTTON_LEFT && key <= InputConstants.MOUSE_BUTTON_8 : key > InputConstants.UNKNOWN.getValue();
        }

        String label() {
            if (!assigned()) return "Не назначено";
            StringBuilder result = new StringBuilder();
            if ((modifiers & InputConstants.MOD_CONTROL) != 0) result.append("Ctrl + ");
            if ((modifiers & InputConstants.MOD_ALT) != 0) result.append("Alt + ");
            if ((modifiers & InputConstants.MOD_SHIFT) != 0) result.append("Shift + ");
            if ((modifiers & InputConstants.MOD_SUPER) != 0) result.append("Win + ");
            String name = (mouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYBOARD)
                .getOrCreate(key).getDisplayName().getString();
            return result.append(name).toString();
        }
    }

    private MusicShortcuts() {
    }

    static EnumMap<Action, Binding> defaults() {
        EnumMap<Action, Binding> result = new EnumMap<>(Action.class);
        for (Action action : Action.values()) result.put(action, action.defaultBinding);
        return result;
    }

    static EnumMap<Action, Binding> migrate(Path options) {
        EnumMap<Action, Binding> result = defaults();
        if (!Files.isRegularFile(options)) return result;
        try {
            Map<String, String> legacy = new java.util.HashMap<>();
            try (var lines = Files.lines(options)) {
                lines.filter(line -> line.startsWith("key_key.yamusic.")).forEach(line -> {
                    int colon = line.indexOf(':');
                    if (colon >= 0) legacy.put(line.substring(0, colon), line.substring(colon + 1));
                });
            }
            for (Action action : Action.values()) {
                String raw = legacy.get("key_key.yamusic." + action.legacyName);
                if (raw == null || raw.equals("key.keyboard.unknown")) continue;
                InputConstants.Key key = InputConstants.getKey(raw);
                if (key.getType() == InputConstants.Type.KEYBOARD || key.getType() == InputConstants.Type.MOUSE) {
                    result.put(action, new Binding(key.getValue(), 0, key.getType() == InputConstants.Type.MOUSE));
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {}
        return result;
    }

    static Binding oldEditorMark(Path options) {
        String prefix = "key_key.king_music_lyrics.mark:";
        if (!Files.isRegularFile(options)) return NONE;
        try (var lines = Files.lines(options)) {
            String entry = lines.filter(line -> line.startsWith(prefix)).findFirst().orElse("");
            if (!entry.isEmpty()) {
                var key = InputConstants.getKey(entry.substring(prefix.length()).strip());
                if (key.getType() == InputConstants.Type.KEYBOARD || key.getType() == InputConstants.Type.MOUSE)
                    return new Binding(key.getValue(), 0, key.getType() == InputConstants.Type.MOUSE);
            }
        } catch (IOException | IllegalArgumentException ignored) {}
        return NONE;
    }

    private static int normalizedModifiers(int modifiers) {
        int result = 0;
        for (int group : new int[]{InputConstants.MOD_CONTROL, InputConstants.MOD_SHIFT,
                InputConstants.MOD_ALT, InputConstants.MOD_SUPER}) {
            if ((modifiers & group) != 0) result |= group;
        }
        return result;
    }

    static Binding fromLegacyGlfw(Binding old) {
        int modifiers = ((old.modifiers() & 1) != 0 ? InputConstants.MOD_SHIFT : 0)
            | ((old.modifiers() & 2) != 0 ? InputConstants.MOD_CONTROL : 0)
            | ((old.modifiers() & 4) != 0 ? InputConstants.MOD_ALT : 0)
            | ((old.modifiers() & 8) != 0 ? InputConstants.MOD_SUPER : 0);
        int key = old.key();
        if (old.mouse()) {
            int button = switch (key) { case 0 -> 1; case 1 -> 3; case 2 -> 2; default -> key >= 3 && key <= 7 ? key + 1 : 0; };
            return new Binding(button, modifiers, true);
        }
        int scan;
        if (key >= 65 && key <= 90) scan = InputConstants.KEY_A + key - 65;
        else if (key >= 49 && key <= 57) scan = InputConstants.KEY_1 + key - 49;
        else if (key >= 290 && key <= 301) scan = InputConstants.KEY_F1 + key - 290;
        else if (key >= 302 && key <= 313) scan = InputConstants.KEY_F13 + key - 302;
        else if (key >= 321 && key <= 329) scan = InputConstants.KEY_NUMPAD1 + key - 321;
        else scan = switch (key) {
            case 32 -> InputConstants.KEY_SPACE; case 39 -> InputConstants.KEY_APOSTROPHE;
            case 44 -> InputConstants.KEY_COMMA; case 45 -> InputConstants.KEY_MINUS;
            case 46 -> InputConstants.KEY_PERIOD; case 47 -> InputConstants.KEY_SLASH;
            case 48 -> InputConstants.KEY_0; case 59 -> InputConstants.KEY_SEMICOLON;
            case 61 -> InputConstants.KEY_EQUALS; case 91 -> InputConstants.KEY_LBRACKET;
            case 92 -> InputConstants.KEY_BACKSLASH; case 93 -> InputConstants.KEY_RBRACKET;
            case 96 -> InputConstants.KEY_GRAVE;
            case 256 -> InputConstants.KEY_ESCAPE; case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB; case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT; case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT; case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN; case 265 -> InputConstants.KEY_UP;
            case 266 -> InputConstants.KEY_PAGEUP; case 267 -> InputConstants.KEY_PAGEDOWN;
            case 268 -> InputConstants.KEY_HOME; case 269 -> InputConstants.KEY_END;
            case 280 -> InputConstants.KEY_CAPSLOCK; case 281 -> InputConstants.KEY_SCROLLLOCK;
            case 282 -> InputConstants.KEY_NUMLOCK; case 283 -> InputConstants.KEY_PRINTSCREEN;
            case 284 -> InputConstants.KEY_PAUSE; case 320 -> InputConstants.KEY_NUMPAD0;
            case 330 -> 99; case 331 -> 84; case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> 86; case 334 -> InputConstants.KEY_ADD; case 335 -> InputConstants.KEY_NUMPADENTER;
            case 336 -> InputConstants.KEY_NUMPADEQUALS;
            case 340 -> InputConstants.KEY_LSHIFT; case 341 -> InputConstants.KEY_LCONTROL;
            case 342 -> InputConstants.KEY_LALT; case 343 -> InputConstants.KEY_LGUI;
            case 344 -> InputConstants.KEY_RSHIFT; case 345 -> InputConstants.KEY_RCONTROL;
            case 346 -> InputConstants.KEY_RALT; case 347 -> InputConstants.KEY_RGUI;
            case 348 -> 101;
            default -> InputConstants.UNKNOWN.getValue();
        };
        return new Binding(scan, modifiers);
    }

    static boolean modifierKey(int key) {
        return key >= InputConstants.KEY_LCONTROL && key <= InputConstants.KEY_RGUI;
    }

    static Binding unbound() {
        return NONE;
    }

    private static Binding ctrl(int key) {
        return new Binding(key, InputConstants.MOD_CONTROL);
    }
}
