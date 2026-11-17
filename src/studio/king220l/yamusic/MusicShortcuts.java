package studio.king220l.yamusic;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class MusicShortcuts {
    private static final int MODIFIERS = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT
        | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
    private static final Binding NONE = new Binding(GLFW.GLFW_KEY_UNKNOWN, 0);

    enum Action {
        PLAY("Воспроизвести / пауза", "play", ctrl(GLFW.GLFW_KEY_P)),
        PREVIOUS("Предыдущий трек", "prev", NONE),
        NEXT("Следующий трек", "next", ctrl(GLFW.GLFW_KEY_N)),
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
            return new Binding(event.key(), event.modifiers() & MODIFIERS);
        }

        static Binding of(MouseButtonInfo event) {
            return new Binding(event.button(), event.modifiers() & MODIFIERS, true);
        }

        boolean assigned() {
            return mouse ? key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_LAST : key > GLFW.GLFW_KEY_UNKNOWN;
        }

        String label() {
            if (!assigned()) return "Не назначено";
            StringBuilder result = new StringBuilder();
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) result.append("Ctrl + ");
            if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) result.append("Alt + ");
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) result.append("Shift + ");
            if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) result.append("Win + ");
            String name = mouse ? "Мышь " + (key + 1)
                : key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z ? Character.toString(key)
                : InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
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
                if (key.getType() == InputConstants.Type.KEYSYM || key.getType() == InputConstants.Type.MOUSE) {
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
                if (key.getType() == InputConstants.Type.KEYSYM || key.getType() == InputConstants.Type.MOUSE)
                    return new Binding(key.getValue(), 0, key.getType() == InputConstants.Type.MOUSE);
            }
        } catch (IOException | IllegalArgumentException ignored) {}
        return NONE;
    }

    static boolean modifierKey(int key) {
        return key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    static Binding unbound() {
        return NONE;
    }

    private static Binding ctrl(int key) {
        return new Binding(key, GLFW.GLFW_MOD_CONTROL);
    }
}
