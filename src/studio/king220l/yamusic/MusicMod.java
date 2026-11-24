/*
 * ╔══════════════════════════════════════╗
 * ║                                      ║
 * ║              KING MUSIC              ║
 * ║               ── ◆ ──                ║
 * ║           Автор · King220l           ║
 * ║                                      ║
 * ╚══════════════════════════════════════╝
 */

package studio.king220l.yamusic;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class MusicMod implements ClientModInitializer {
    static final String NAME = "King Music";
    static final Logger LOG = LoggerFactory.getLogger(NAME);
    static final String AUTH_URL = "https://ya.ru/device";
    private static MusicMod instance;
    MusicSettings settings;
    MusicApi api;
    MusicPlayer player;
    MusicExtension extension;
    MusicLyricsExtension lyrics;
    MusicVisualizerExtension visualizer;
    MusicApi.Account account;
    MusicApi.DeviceCode deviceCode;
    String authStatus = "";
    private Thread authWorker;
    private Thread accountWorker;
    private int accountRequest;
    private MusicUi.PlayerScreen playerScreen;
    private KeyMapping openKey;

    static boolean uiSoundsEnabled() { return instance != null && instance.settings.uiSounds; }
    boolean follows() { return extension != null && extension.follows(); }
    boolean inRoom() { return extension != null && extension.inRoom(); }

    @Override
    public void onInitializeClient() {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("yamusic");
        Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        BuildTrust.start(directory);
        settings = MusicSettings.load(directory.resolve("settings.json"), options);
        api = new MusicApi(directory);
        visualizer = FabricLoader.getInstance().getEntrypoints("king_music_visualizer", MusicVisualizerExtension.class)
            .stream().findFirst().orElse(null);
        player = new MusicPlayer(api, settings, visualizer);
        if (visualizer != null) visualizer.initialize(this);
        extension = FabricLoader.getInstance().getEntrypoints("king_music_extension", MusicExtension.class)
            .stream().findFirst().orElse(null);
        if (extension != null) {
            extension.initialize(this);
            ClientTickEvents.END_CLIENT_TICK.register(extension::tick);
        }
        KeyMapping.Category category = KeyMapping.Category.register(id("controls"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yamusic.open", GLFW.GLFW_KEY_F8, category));
        lyrics = FabricLoader.getInstance().getEntrypoints("king_music_lyrics", MusicLyricsExtension.class)
            .stream().findFirst().orElse(null);
        if (lyrics != null) {
            lyrics.initialize(this, category);
            ClientTickEvents.END_CLIENT_TICK.register(lyrics::tick);
        }
        instance = this;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (api.signedIn()) fetchAccount();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            cancelAuth();
            if (extension != null) extension.stop();
            if (lyrics != null) lyrics.stop();
            player.stop();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof PauseScreen && settings.miniPlayer) addMiniPlayer(screen, w, h);
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id("track"),
            (graphics, delta) -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null || minecraft.screen != null || !player.started()
                        || settings.hud == MusicSettings.HudMode.OFF) return;
                long age = System.currentTimeMillis() - player.startedAt();
                if (settings.hud == MusicSettings.HudMode.CHANGE && age >= settings.seconds * 1_000L) return;
                MusicUi.drawHud(graphics, minecraft.font, settings, player.track(), player.paused(), player.recordPositionMs());
            });
        LOG.info("Native music player ready");
    }

    public static boolean handleKey(long window, int action, KeyEvent event) {
        Minecraft client = inputClient(window, action);
        if (client == null) return false;
        return instance.handleShortcut(client, action, MusicShortcuts.Binding.of(event), instance.openKey.matches(event));
    }

    public static boolean handleMouse(long window, int action, MouseButtonInfo event) {
        Minecraft client = inputClient(window, action);
        if (client == null) return false;
        MusicShortcuts.Binding binding = MusicShortcuts.Binding.of(event);
        return instance.handleShortcut(client, action, binding, instance.matchesOpenKey(binding));
    }

    private static Minecraft inputClient(long window, int action) {
        if (instance == null || action == GLFW.GLFW_RELEASE) return null;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getOverlay() != null
                || client.getWindow() == null || window != client.getWindow().handle()) return null;
        return client;
    }

    private boolean handleShortcut(Minecraft client, int eventAction, MusicShortcuts.Binding chord, boolean opens) {
        Screen current = client.screen;
        if (current instanceof MusicUi.PlayerScreen screen) {
            if (screen.recording()) {
                if (eventAction == GLFW.GLFW_PRESS) screen.record(chord);
                return true;
            }
            if (screen.typing()) return false;
        }
        if (lyrics != null && lyrics.shortcut(client, current, chord, eventAction)) return true;
        if (!(current instanceof MusicUi.PlayerScreen) && (current == null ? client.level == null
                : !(current instanceof PauseScreen || current instanceof TitleScreen))) {
            return false;
        }
        if (!chord.assigned()) return false;
        if (chord.mouse() && current != null && chord.key() <= GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        if (eventAction == GLFW.GLFW_PRESS && chord.modifiers() == 0 && opens) {
            if (current instanceof MusicUi.PlayerScreen screen) screen.closePlayer();
            else open(current);
            return true;
        }
        for (MusicShortcuts.Action action : MusicShortcuts.Action.values()) {
            if (action.editorOnly() || action.requiresLyrics() && lyrics == null) continue;
            if (!chord.equals(settings.shortcuts.get(action))) continue;
            if (eventAction == GLFW.GLFW_PRESS) {
                if (follows() && (action == MusicShortcuts.Action.PLAY || action == MusicShortcuts.Action.PREVIOUS
                    || action == MusicShortcuts.Action.NEXT)) return true;
                switch (action) {
                    case PLAY -> player.toggle();
                    case PREVIOUS -> player.previous();
                    case NEXT -> player.next();
                    case HUD -> settings.toggleHud();
                    case LYRICS_HUD -> lyrics.toggleHud();
                    case VOLUME_UP -> stepVolume(1);
                    case VOLUME_DOWN -> stepVolume(-1);
                    case MUTE -> toggleMute();
                }
            }
            return true;
        }
        return false;
    }

    String openKeyLabel() {
        return openKey.getTranslatedKeyMessage().getString();
    }

    boolean matchesOpenKey(MusicShortcuts.Binding chord) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(openKey);
        InputConstants.Type type = chord.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        return chord.modifiers() == 0 && key.getType() == type
            && chord.key() == key.getValue();
    }

    void setVolume(int value) {
        settings.volume = Math.clamp(value, 0, 100);
        settings.muted = false;
        player.setVolume(settings.volume);
    }

    private void stepVolume(int direction) {
        int step = settings.volume <= 15 ? 1 : 5;
        setVolume(settings.volume + direction * step);
        settings.save();
    }

    void toggleMute() {
        settings.muted = !settings.muted;
        player.setVolume(settings.muted ? 0 : settings.volume);
        settings.save();
    }

    private void addMiniPlayer(Screen screen, int width, int height) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        int cardWidth = Math.min(172, width - 16);
        if (cardWidth < 104 || height < 65) return;
        int centered = (width - cardWidth) / 2;
        int bottom = height - 29;
        int[][] candidates = {{centered, bottom}, {8, bottom}, {width - cardWidth - 8, bottom},
            {8, height / 2 + 12}, {width - cardWidth - 8, height / 2 + 12}, {8, 8}};
        for (int[] position : candidates) {
            int x = position[0];
            int y = position[1];
            if (widgets.stream().noneMatch(w -> w.visible && x < w.getRight() + 2 && x + cardWidth > w.getX() - 2
                    && y < w.getBottom() + 2 && y + 21 > w.getY() - 2)) {
                var label = new MusicUi.MiniTrack(new MusicUi.Rect(x, y, cardWidth - 64, 20), player, () -> open(screen));
                var prev = new MusicUi.Control(new MusicUi.Rect(x + cardWidth - 62, y, 20, 20), "Предыдущий трек",
                    MusicTheme.Icon.PREVIOUS, MusicUi.Style.FILLED, false, false, player::previous);
                var play = new MusicUi.Control(new MusicUi.Rect(x + cardWidth - 41, y, 20, 20), "Воспроизвести / пауза",
                    MusicTheme.Icon.PLAY, MusicUi.Style.TRANSPORT, false, false,
                    () -> { if (player.track() == null) open(screen); else player.toggle(); });
                var next = new MusicUi.Control(new MusicUi.Rect(x + cardWidth - 20, y, 20, 20), "Следующий трек",
                    MusicTheme.Icon.NEXT, MusicUi.Style.FILLED, false, false, player::next);
                prev.hint("Предыдущий трек");
                play.hint("Воспроизвести / пауза");
                next.hint("Следующий трек");
                widgets.addAll(List.of(label, prev, play, next));
                ScreenEvents.beforeExtract(screen).register((ignored, graphics, mx, my, tick) -> {
                    label.setLabel(player.track() == null ? NAME : player.track().title());
                    prev.active = player.hasPrevious() && !follows();
                    play.active = !follows();
                    play.icon = (player.started() || player.seeking()) && !player.paused()
                        ? MusicTheme.Icon.PAUSE : MusicTheme.Icon.PLAY;
                    next.active = player.hasNext() && !follows();
                });
                return;
            }
        }
    }

    void open(Screen parent) {
        if (playerScreen == null) playerScreen = new MusicUi.PlayerScreen(this, parent);
        playerScreen.parent = parent;
        Minecraft.getInstance().setScreen(playerScreen);
    }

    boolean authorizing() {
        return authWorker != null;
    }

    void beginAuth() {
        if (authorizing() || api.signedIn()) return;
        authStatus = "Запрашиваем код…";
        Minecraft client = Minecraft.getInstance();
        Thread worker = Thread.ofVirtual().name("yamusic-login").unstarted(() -> {
            Thread self = Thread.currentThread();
            try {
                MusicApi.DeviceCode code = api.requestCode();
                client.execute(() -> {
                    if (authWorker == self) {
                        deviceCode = code;
                        authStatus = "Введи код на странице Яндекса.";
                    }
                });
                MusicApi.Token token = api.waitForToken(code);
                client.execute(() -> {
                    if (authWorker != self) return;
                    boolean saved = api.signIn(token);
                    authStatus = saved ? "" : "Вход выполнен на этот запуск.";
                    deviceCode = null;
                    authWorker = null;
                    fetchAccount();
                });
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                client.execute(() -> {
                    if (authWorker != self) return;
                    authStatus = message(error);
                    deviceCode = null;
                    authWorker = null;
                });
            }
        });
        authWorker = worker;
        worker.start();
    }

    void signInWithToken(String token) {
        if (authorizing() || api.signedIn()) return;
        String value = token == null ? "" : token.strip();
        if (value.isEmpty()) {
            authStatus = "Вставь токен Яндекс Музыки.";
            return;
        }
        authStatus = "Проверяем токен…";
        Thread worker = Thread.ofVirtual().name("yamusic-token-login").unstarted(() -> {
            Thread self = Thread.currentThread();
            try {
                MusicApi.Account owner = api.signInWithAccessToken(value);
                Minecraft.getInstance().execute(() -> {
                    if (authWorker != self) return;
                    account = owner;
                    authStatus = "";
                    authWorker = null;
                });
            } catch (Exception error) {
                Minecraft.getInstance().execute(() -> {
                    if (authWorker != self) return;
                    authStatus = message(error);
                    authWorker = null;
                });
            }
        });
        authWorker = worker;
        worker.start();
    }

    private void fetchAccount() {
        if (accountWorker != null) accountWorker.interrupt();
        int serial = ++accountRequest;
        accountWorker = request(api::account, value -> {
            if (accountRequest != serial) return;
            accountWorker = null;
            if (api.signedIn()) account = value;
        }, error -> {
            if (accountRequest != serial) return;
            accountWorker = null;
            authStatus = error;
        });
    }

    void cancelAuth() {
        if (authWorker != null) authWorker.interrupt();
        authWorker = null;
        deviceCode = null;
        authStatus = "";
    }

    void signOut() {
        cancelAuth();
        if (accountWorker != null) accountWorker.interrupt();
        accountWorker = null;
        accountRequest++;
        if (player.track() == null || player.track().file() == null) player.clear();
        account = null;
        try {
            api.signOut();
        } catch (IOException error) {
            authStatus = "Не удалось удалить сохранённый сеанс.";
        }
    }

    static <T> Thread request(java.util.concurrent.Callable<T> task, Consumer<T> success, Consumer<String> failure) {
        Minecraft client = Minecraft.getInstance();
        return Thread.ofVirtual().name("yamusic-request").start(() -> {
            try {
                T result = task.call();
                if (!Thread.currentThread().isInterrupted()) client.execute(() -> success.accept(result));
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (!Thread.currentThread().isInterrupted()) client.execute(() -> failure.accept(message(error)));
            }
        });
    }

    static String message(Exception error) {
        if (error instanceof IOException && error.getMessage() != null
                && error.getMessage().matches("^[А-ЯЁ][^\n]{3,140}$")) return error.getMessage();
        return "Не удалось связаться с Яндекс Музыкой. Попробуй ещё раз.";
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("yamusic", path);
    }

}
