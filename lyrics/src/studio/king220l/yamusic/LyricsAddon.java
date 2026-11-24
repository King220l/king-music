/*
 * ╔══════════════════════════════════════╗
 * ║           KING MUSIC LYRICS          ║
 * ║               ── ◆ ──                ║
 * ║           Автор · King220l           ║
 * ╚══════════════════════════════════════╝
 */

package studio.king220l.yamusic;

import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

public final class LyricsAddon implements MusicLyricsExtension {
    private MusicMod mod;
    private KeyMapping toggle;
    private LyricsSettings settings;
    private LyricsHud hud;
    private MusicApi.Track observed;
    private LyricsData data = LyricsData.EMPTY;
    private Thread worker;
    private int serial;
    private boolean loaded, editable, wasVisible, authenticated;
    private String status = "";

    @Override
    public void initialize(MusicMod mod, KeyMapping.Category category) {
        this.mod = mod;
        settings = LyricsSettings.load(FabricLoader.getInstance().getConfigDir().resolve("yamusic/lyrics.json"));
        hud = new LyricsHud(settings);
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.king_music_lyrics.toggle", InputConstants.UNKNOWN.getValue(), category));
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("king_music_lyrics", "lyrics"),
            (graphics, time) -> {
                var game = Minecraft.getInstance();
                if (!settings.hud || game.level == null || game.gui.screen() != null || !mod.player.started()) return;
                LyricsData lyrics = data();
                int current = lyrics.cueAt(displayPosition());
                if (current < 0) return;
                hud.prepare(game.font, lyrics, current);
                hud.draw(graphics, game.font, hud.bounds(graphics.guiWidth(), graphics.guiHeight()));
            });
    }

    @Override
    public void tick(Minecraft game) {
        boolean visible = game.gui.screen() instanceof MusicUi.PlayerScreen screen && screen.showingLyrics();
        if (!visible && !(settings.hud && game.level != null)) {
            if (worker != null) stop();
            if (wasVisible && observed != null && local(observed)) loaded = false;
            wasVisible = false;
            return;
        }
        wasVisible = visible;
        MusicApi.Track track = mod.player.track();
        boolean account = track != null && track.file() == null && mod.api.signedIn();
        if (track == observed && account == authenticated && (loaded || worker != null)) return;
        stop();
        observed = track;
        authenticated = account;
        editable = false;
        data = LyricsData.EMPTY;
        loaded = true;
        if (track == null) {
            status = "Выбери трек, чтобы открыть текст.";
            return;
        }
        if (track.file() != null && !local(track)) {
            status = "Для этой записи текст отсутствует.";
            return;
        }
        loaded = false;
        status = "Загружаем текст…";
        int request = serial;
        worker = MusicMod.request(() -> local(track) ? LyricsData.read(track.file()) : LyricsData.parse(mod.api.lyrics(track.id())),
            result -> {
                if (request != serial) return;
                worker = null;
                loaded = true;
                editable = local(track);
                data = result;
                status = result.lines.isEmpty() ? (editable
                    ? "Добавь текст в редакторе или положи .lrc рядом с треком." : "У этого трека нет текста.") : "";
            }, error -> {
                if (request != serial) return;
                worker = null;
                loaded = true;
                status = local(track) ? "Не удалось прочитать текст рядом с треком." : error;
            });
    }

    @Override
    public void stop() {
        serial++;
        if (worker != null) worker.interrupt();
        worker = null;
    }

    LyricsData data() { return observed == mod.player.track() ? data : LyricsData.EMPTY; }
    String status() { return observed == mod.player.track() ? status : "Загружаем текст…"; }
    String markKey() {
        var binding = mod.settings.shortcuts.get(MusicShortcuts.Action.EDITOR_MARK);
        return binding.assigned() ? binding.label() : "";
    }
    long displayPosition() { return mod.player.elapsedMs() + settings.offsetMs; }
    long cuePosition(long stamp) { return stamp - settings.offsetMs; }
    LyricsSettings appearance() { return settings; }

    @Override
    public AbstractWidget panel(MusicUi.Rect bounds) { return new LyricsPanel(mod, this, bounds); }

    @Override
    public boolean canEdit() { return editable && observed == mod.player.track(); }

    @Override
    public void edit(Screen parent) {
        if (canEdit()) Minecraft.getInstance().gui.setScreen(new LyricsEditor(mod, parent, this, observed, data));
    }

    @Override
    public void configure(Screen parent) {
        if (parent instanceof MusicUi.PlayerScreen screen) screen.showLyricsSettings();
    }

    @Override
    public List<MusicUi.Preference> settings(MusicUi.PlayerScreen screen) {
        return LyricsOptions.settings(screen, this);
    }

    @Override
    public void toggleHud() {
        settings.hud = !settings.hud;
        settings.save();
    }

    void saved(MusicApi.Track track, LyricsData result) {
        if (track != observed) return;
        data = result;
        loaded = true;
        status = result.lines.isEmpty() ? "Добавь строки в редакторе." : "";
    }

    private static boolean local(MusicApi.Track track) {
        return track.file() != null && track.id().startsWith("file:");
    }

    @Override
    public boolean shortcut(Minecraft game, Screen screen, MusicShortcuts.Binding key, int action) {
        if (!key.assigned() || key.mouse() && screen != null && key.modifiers() == 0
                && (key.key() == InputConstants.MOUSE_BUTTON_LEFT || key.key() == InputConstants.MOUSE_BUTTON_RIGHT)) return false;
        if (screen instanceof LyricsEditor editor) {
            for (var binding : MusicShortcuts.Action.values()) {
                if (!binding.worksInEditor() || !key.equals(mod.settings.shortcuts.get(binding))) continue;
                if (editor.typing() && (binding != MusicShortcuts.Action.PLAY || key.modifiers() == 0)) return false;
                if (action == InputConstants.PRESS) {
                    if (binding == MusicShortcuts.Action.HUD) mod.settings.toggleHud();
                    else if (binding == MusicShortcuts.Action.LYRICS_HUD) toggleHud();
                    else editor.shortcut(binding);
                }
                return true;
            }
            return false;
        }
        if (key.modifiers() != 0 || action != InputConstants.PRESS || !matches(toggle, key)) return false;
        if (screen instanceof MusicUi.PlayerScreen player) {
            if (player.typing() || player.recording()) return false;
            player.toggleLyrics();
            return true;
        }
        if (screen == null ? game.level == null : !(screen instanceof PauseScreen || screen instanceof TitleScreen)) return false;
        mod.open(screen);
        if (game.gui.screen() instanceof MusicUi.PlayerScreen player) player.showLyrics();
        return true;
    }

    private static boolean matches(KeyMapping mapping, MusicShortcuts.Binding key) {
        var bound = KeyMappingHelper.getBoundKeyOf(mapping);
        return !mapping.isUnbound() && bound.getValue() == key.key()
            && bound.getType() == (key.mouse() ? com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                : com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD);
    }
}
