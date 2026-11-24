package studio.king220l.yamusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Optional integration mod; never included in the distributable JARs. */
public final class PortSmokeTest implements ClientModInitializer {
    private static Runnable pendingFrame;
    private volatile boolean waitingFrame;
    private volatile Runnable pendingAdvance;
    private MusicMod mod;
    private boolean started, finished;
    private int tick, stage, entered;
    private long pausedAt;
    private Screen title;
    private static final Path REPORT = FabricLoader.getInstance().getGameDir().resolve("smoke-result.txt");

    public void onInitializeClient() {
        try { Files.deleteIfExists(REPORT); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot reset smoke report", error); }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> started = true);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        MusicMod.LOG.info("SMOKE PASS {}", message);
    }

    private void next() { stage++; entered = tick; }
    private boolean ready() { return tick - entered >= 25; }
    private void shot(Minecraft client, String name, Runnable after) {
        waitingFrame = true;
        pendingFrame = () -> Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                if (name.equals("lyrics")) checkTextPixels(client, LyricsPanel.class, "interfaceScale", 30, image);
                if (name.startsWith("editor")) checkTextPixels(client, MultiLineEditBox.class, "scale", 2, image);
                Path directory = client.gameDirectory.toPath().resolve("screenshots");
                Files.createDirectories(directory);
                image.writeToFile(directory.resolve("king-music-26.1-" + name + ".png"));
                pendingAdvance = after;
            } catch (Throwable failure) {
                finished = true;
                MusicMod.LOG.error("SMOKE frame capture failed", failure);
                try { Files.writeString(REPORT, "FAIL frame capture: " + failure + "\n"); } catch (Exception ignored) {}
                client.stop();
            }
        });
    }

    public static void captureCompletedFrame() {
        Runnable capture = pendingFrame;
        pendingFrame = null;
        if (capture != null) capture.run();
    }

    private void checkTextPixels(Minecraft client, Class<?> widgetType, String scaleField, int topPadding, com.mojang.blaze3d.platform.NativeImage image) {
        try {
            var widget = (net.minecraft.client.gui.components.AbstractWidget) client.screen.children().stream()
                .filter(widgetType::isInstance).findFirst().orElseThrow();
            var field = client.screen.getClass().getDeclaredField(scaleField);
            field.setAccessible(true);
            double scale = ((Number) field.get(client.screen)).doubleValue()
                * image.getWidth() / client.getWindow().getGuiScaledWidth();
            int left = (int) ((widget.getX() + 8) * scale), top = (int) ((widget.getY() + topPadding) * scale);
            int right = (int) ((widget.getRight() - 8) * scale), bottom = (int) ((widget.getBottom() - 8) * scale);
            int bright = 0;
            for (int y = Math.max(0, top); y < Math.min(image.getHeight(), bottom); y++) {
                for (int x = Math.max(0, left); x < Math.min(image.getWidth(), right); x++) {
                    int color = image.getPixel(x, y);
                    if ((color >> 16 & 255) + (color >> 8 & 255) + (color & 255) > 360) bright++;
                }
            }
            check(bright > 20, widgetType.getSimpleName() + " text pixels are visible in the completed frame");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void exercisePlayerInput(Minecraft client) throws ReflectiveOperationException {
        var screen = (MusicUi.PlayerScreen) client.screen;
        var playField = screen.getClass().getDeclaredField("play");
        var scaleField = screen.getClass().getDeclaredField("interfaceScale");
        playField.setAccessible(true);
        scaleField.setAccessible(true);
        var play = (MusicUi.Control) playField.get(screen);
        float scale = ((Number) scaleField.get(screen)).floatValue();
        var click = new net.minecraft.client.input.MouseButtonEvent(
            (play.getX() + play.getWidth() / 2.0) * scale,
            (play.getY() + play.getHeight() / 2.0) * scale,
            new net.minecraft.client.input.MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        check(screen.mouseClicked(click, false), "New mouse event reaches the scaled play control");
        check(!mod.player.paused(), "Scaled mouse click resumes playback");
        screen.mouseReleased(click);
        screen.setFocused(play);
        play.setFocused(true);
        check(screen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)),
            "New key event reaches the focused play control");
        check(mod.player.paused(), "Space activates the focused play control");
    }

    private void tick(Minecraft client) {
        if (!started || finished) return;
        tick++;
        try {
            if (tick > 3600) throw new AssertionError("Smoke test timeout in stage " + stage);
            if (pendingAdvance != null) {
                Runnable advance = pendingAdvance;
                pendingAdvance = null;
                waitingFrame = false;
                advance.run();
                return;
            }
            if (waitingFrame) return;
            if (stage == 0) {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null || tick < 40) return;
                mod = (MusicMod) FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).stream()
                    .filter(MusicMod.class::isInstance).findFirst().orElseThrow();
                title = client.screen;
                mod.settings.uiScale = 85;
                check(mod.lyrics instanceof LyricsAddon, "Core and Lyrics initialized");
                check(mod.visualizer == null, "Visualizer is absent from the test client");
                if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
                    BuildTrust.requireOfficial();
                    check(!BuildTrust.modified(), "Production JAR hashes verified by Ed25519 manifest");
                    if (Boolean.getBoolean("kingmusic.smoke.onlineTrust")) {
                        check(BuildTrust.message().equals("Подлинность: официальная сборка King220l"),
                            "Fresh production trust verified online without cached signature");
                    }
                }
                check(title.children().stream().noneMatch(MusicUi.TitleIcon.class::isInstance), "No mod button added to the 26.1 title layout");
                check(MusicMod.handleKey(client.getWindow().handle(), GLFW.GLFW_PRESS,
                    new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_F8, 0, 0)), "F8 shortcut handled");
                check(client.screen instanceof MusicUi.PlayerScreen, "F8 opens the player");
                var tracks = LocalMusic.scan(Path.of(mod.settings.localFolder));
                check(tracks.size() >= 2, "Synthetic local music scanned");
                mod.player.play(tracks, 0);
                next();
            } else if (stage == 1 && ready()) {
                if (!mod.player.started() || mod.player.elapsedMs() < 200) return;
                check(mod.player.durationMs() == 60000 && mod.player.error().isEmpty(), "WAV audio starts with correct duration");
                mod.player.setPaused(true);
                pausedAt = mod.player.elapsedMs();
                next();
            } else if (stage == 2 && ready()) {
                check(mod.player.paused() && Math.abs(mod.player.elapsedMs() - pausedAt) < 100, "Pause freezes playback position");
                mod.player.seek(mod.player.track(), 20000);
                next();
            } else if (stage == 3 && ready()) {
                if (!mod.player.started()) return;
                check(mod.player.paused() && Math.abs(mod.player.elapsedMs() - 20000) < 500, "Seek preserves paused state and reaches target");
                exercisePlayerInput(client);
                mod.player.setPaused(false);
                ((MusicUi.PlayerScreen) client.screen).showLyrics();
                next();
            } else if (stage == 4 && ready()) {
                var lyrics = (LyricsAddon) mod.lyrics;
                if (!lyrics.data().synced()) return;
                if (Boolean.getBoolean("kingmusic.smoke.inspect")) return;
                check(lyrics.data().lines.size() == 5 && lyrics.canEdit(), "Local LRC loaded and editable");
                shot(client, "lyrics", () -> { mod.lyrics.edit(client.screen); next(); });
            } else if (stage == 5 && ready()) {
                check(client.screen instanceof LyricsEditor, "Lyrics editor opens and renders");
                var edit = (MultiLineEditBox) client.screen.children().stream().filter(MultiLineEditBox.class::isInstance).findFirst().orElseThrow();
                edit.setValue("Integration editor test\nРусский текст\nThird line");
                check(edit.getValue().contains("Русский"), "Multi-line editor accepts Unicode text");
                client.screen.setFocused(edit);
                edit.setFocused(true);
                check(client.screen.charTyped(new net.minecraft.client.input.CharacterEvent('Ж')),
                    "New character event reaches the lyrics editor");
                check(edit.getValue().contains("Ж"), "Character event inserts Unicode text");
                edit.setValue("line\n".repeat(LyricsData.MAX_LINES + 2));
                check(edit.getValue().lines().count() == LyricsData.MAX_LINES, "Editor enforces the lyrics line limit on 26.1");
                edit.setValue(((LyricsAddon) mod.lyrics).data().plain());
                shot(client, "editor", () -> { mod.settings.uiScale = 50; mod.lyrics.edit(title); next(); });
            } else if (stage == 6 && ready()) {
                check(client.screen instanceof LyricsEditor, "Lyrics editor renders at 50 percent scale");
                shot(client, "editor-50", () -> { client.setScreen(title); stage = 12; entered = tick; });
            } else if (stage == 12 && ready()) {
                mod.player.next();
                next();
            } else if (stage == 13 && ready()) {
                if (!mod.player.started()) return;
                check(mod.player.error().isEmpty() && mod.player.hasPrevious(), "Next track starts and history supports previous");
                mod.player.previous();
                next();
            } else if (stage == 14 && ready()) {
                if (!mod.player.started()) return;
                check(mod.player.error().isEmpty(), "Previous track resumes without decoder errors");
                mod.player.stop();
                client.setScreen(title);
                next();
            } else if (stage == 15 && ready()) {
                shot(client, "title-no-button", () -> {
                if (Boolean.getBoolean("kingmusic.smoke.world")) {
                    var data = WorldDataConfiguration.DEFAULT;
                    var settings = new LevelSettings("KING Music smoke", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), false, data);
                    client.createWorldOpenFlows().createFreshLevel("KingMusicSmoke-" + System.currentTimeMillis(),
                        settings, new WorldOptions(12345L, false, false), WorldPresets::createFlatWorldDimensions, title);
                    next();
                } else { try { finish(client); } catch (java.io.IOException e) { throw new IllegalStateException(e); } }
                });
            } else if (stage == 16 && ready()) {
                if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) return;
                check(true, "Isolated flat world loaded");
                mod.settings.hud = MusicSettings.HudMode.ALWAYS;
                ((LyricsAddon) mod.lyrics).appearance().hud = true;
                mod.player.play(LocalMusic.scan(Path.of(mod.settings.localFolder)), 0);
                next();
            } else if (stage == 17 && ready()) {
                if (!mod.player.started() || !((LyricsAddon) mod.lyrics).data().synced()) return;
                check(client.screen == null && mod.player.error().isEmpty(), "Track HUD and Lyrics HUD render during world playback");
                shot(client, "world-huds", () -> { client.setScreen(new PauseScreen(true)); next(); });
            } else if (stage == 18 && ready()) {
                var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(client.screen);
                check(widgets.stream().filter(MusicUi.MiniTrack.class::isInstance).count() == 1, "Pause menu contains one mini-player");
                boolean clear = true;
                for (var music : widgets) {
                    if (!(music instanceof MusicUi.Control)) continue;
                    for (var vanilla : widgets) {
                        if (vanilla instanceof MusicUi.Control || !vanilla.visible) continue;
                        clear &= !(music.getX() < vanilla.getRight() && music.getRight() > vanilla.getX()
                            && music.getY() < vanilla.getBottom() && music.getBottom() > vanilla.getY());
                    }
                }
                check(clear, "All mini-player controls stay clear of vanilla pause-menu buttons");
                shot(client, "pause-mini-player", () -> {
                client.setScreen(null);
                check(MusicMod.handleKey(client.getWindow().handle(), GLFW.GLFW_PRESS,
                    new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_F8, 0, 0)), "F8 opens the player from the world");
                check(client.screen instanceof MusicUi.PlayerScreen, "In-world player screen opens");
                MusicMod.handleKey(client.getWindow().handle(), GLFW.GLFW_PRESS,
                    new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_F8, 0, 0));
                check(client.screen == null, "F8 returns to the world");
                MusicApi.Track mp3;
                try { mp3 = LocalMusic.scan(Path.of(mod.settings.localFolder)).stream()
                    .filter(track -> track.file().getFileName().toString().endsWith(".mp3")).findFirst().orElseThrow(); }
                catch (Exception e) { throw new IllegalStateException(e); }
                mod.player.play(List.of(mp3), 0);
                next();
                });
            } else if (stage == 19 && ready()) {
                check(mod.player.error().isEmpty(), "MP3 decoder reports no error");
                if (!mod.player.started() || mod.player.elapsedMs() < 200) return;
                check(mod.player.durationMs() > 58000, "Nested JLayer decodes and plays the synthetic MP3");
                mod.player.seek(mod.player.track(), 10000);
                next();
            } else if (stage == 20 && ready()) {
                if (!mod.player.started()) return;
                check(mod.player.error().isEmpty() && mod.player.elapsedMs() >= 9900 && mod.player.elapsedMs() < 13000,
                    "MP3 seek reaches the requested position");
                finish(client);
            }
        } catch (Throwable failure) {
            finished = true;
            MusicMod.LOG.error("SMOKE FAILED in stage " + stage, failure);
            try { Files.writeString(REPORT, "FAIL stage " + stage + ": " + failure + "\n"); } catch (Exception ignored) {}
            client.stop();
        }
    }

    private void finish(Minecraft client) throws java.io.IOException {
        Files.writeString(REPORT, "PASS: Java 25 / Fabric 26.1 / Core + Lyrics"
            + (Boolean.getBoolean("kingmusic.smoke.world") ? " / world HUDs and pause menu" : "") + "\n");
        MusicMod.LOG.info("SMOKE COMPLETE");
        finished = true;
        client.stop();
    }

}
