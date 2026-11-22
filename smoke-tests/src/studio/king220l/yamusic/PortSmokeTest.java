package studio.king220l.yamusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Optional integration mod; never included in the three distributable JARs. */
public final class PortSmokeTest implements ClientModInitializer {
    private MusicMod mod;
    private boolean started, finished;
    private int tick, stage, entered;
    private long pausedAt;
    private Screen title;
    private static final Path REPORT = FabricLoader.getInstance().getGameDir().resolve("smoke-result.txt");

    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> started = true);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        MusicMod.LOG.info("SMOKE PASS {}", message);
    }

    private void next() { stage++; entered = tick; }
    private boolean ready() { return tick - entered >= 25; }
    private void shot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, "king-music-1.21.1-" + name + ".png", client.getMainRenderTarget(), ignored -> {});
    }

    private void tick(Minecraft client) {
        if (!started || finished) return;
        tick++;
        try {
            if (tick > 1800) throw new AssertionError("Smoke test timeout in stage " + stage);
            if (stage == 0) {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null || tick < 40) return;
                mod = (MusicMod) FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).stream()
                    .filter(MusicMod.class::isInstance).findFirst().orElseThrow();
                title = client.screen;
                check(mod.lyrics instanceof LyricsAddon && mod.visualizer instanceof VisualizerAddon, "All three modules initialized");
                if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
                    BuildTrust.requireOfficial();
                    check(!BuildTrust.modified(), "Production JAR hashes verified by Ed25519 manifest");
                }
                check(title.children().stream().noneMatch(MusicUi.TitleIcon.class::isInstance), "No mod button added to the 1.21.1 title layout");
                check(MusicMod.handleKey(client.getWindow().getWindow(), GLFW.GLFW_PRESS,
                    new CompatInput.KeyEvent(GLFW.GLFW_KEY_F8, 0, 0)), "F8 shortcut handled");
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
                mod.player.setPaused(false);
                ((MusicUi.PlayerScreen) client.screen).showLyrics();
                next();
            } else if (stage == 4 && ready()) {
                var lyrics = (LyricsAddon) mod.lyrics;
                if (!lyrics.data().synced()) return;
                check(lyrics.data().lines.size() == 5 && lyrics.canEdit(), "Local LRC loaded and editable");
                shot(client, "lyrics");
                mod.lyrics.edit(client.screen);
                next();
            } else if (stage == 5 && ready()) {
                check(client.screen instanceof LyricsEditor, "Lyrics editor opens and renders");
                var edit = (MultiLineEditBox) client.screen.children().stream().filter(MultiLineEditBox.class::isInstance).findFirst().orElseThrow();
                edit.setValue("Integration editor test\nРусский текст\nThird line");
                check(edit.getValue().contains("Русский"), "Multi-line editor accepts Unicode text");
                edit.setValue("line\n".repeat(LyricsData.MAX_LINES + 2));
                check(edit.getValue().lines().count() == LyricsData.MAX_LINES, "Editor enforces the lyrics line limit on 1.21.1");
                shot(client, "editor");
                mod.settings.uiScale = 50;
                mod.lyrics.edit(title);
                next();
            } else if (stage == 6 && ready()) {
                check(client.screen instanceof LyricsEditor, "Lyrics editor renders at 50 percent scale");
                shot(client, "editor-50");
                client.setScreen(new VisualizationScreen(mod, VisualizerAddon.Mode.BARS));
                next();
            } else if (stage >= 7 && stage <= 11 && ready()) {
                var visualizer = (VisualizerAddon) mod.visualizer;
                var mode = VisualizerAddon.Mode.values()[stage - 7];
                var frame = visualizer.readAudio(mode.input, System.nanoTime());
                if (frame == null) return;
                check(frame.values().length > 0, "Visualizer " + mode + " renders and receives decoded PCM");
                shot(client, "visualizer-" + mode.name().toLowerCase());
                if (stage < 11) client.setScreen(new VisualizationScreen(mod, VisualizerAddon.Mode.values()[stage - 6]));
                next();
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
                shot(client, "title-no-button");
                Files.writeString(REPORT, "PASS: Java 21 / Fabric 1.21.1 / Core + Lyrics + Visualizer\n");
                MusicMod.LOG.info("SMOKE COMPLETE");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            MusicMod.LOG.error("SMOKE FAILED in stage " + stage, failure);
            try { Files.writeString(REPORT, "FAIL stage " + stage + ": " + failure + "\n"); } catch (Exception ignored) {}
            client.stop();
        }
    }

    private static final class VisualizationScreen extends Screen {
        private final MusicMod mod;
        private final VisualizerAddon.Mode mode;
        VisualizationScreen(MusicMod mod, VisualizerAddon.Mode mode) { super(Component.literal("Visualizer integration test")); this.mod = mod; this.mode = mode; }
        protected void init() {
            try {
                var field = VisualizerAddon.class.getDeclaredField("settings"); field.setAccessible(true);
                var settings = field.get(mod.visualizer);
                var value = settings.getClass().getDeclaredField("mode"); value.setAccessible(true); value.set(settings, mode);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            addRenderableWidget(mod.visualizer.panel(new MusicUi.Rect(10, 25, width - 20, height - 45)));
        }
        public void render(GuiGraphics g, int x, int y, float delta) {
            g.fill(0, 0, width, height, MusicTheme.BASE);
            g.drawString(font, mode.name(), 12, 10, MusicTheme.TEXT);
            super.render(g, x, y, delta);
        }
    }
}
