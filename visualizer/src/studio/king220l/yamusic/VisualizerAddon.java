package studio.king220l.yamusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.*;

public final class VisualizerAddon implements MusicVisualizerExtension {
    private static final long REQUEST_LIFETIME = 260_000_000L;
    private static final long SAMPLE_INTERVAL = 28_000_000L;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private MusicMod mod;
    private Path config;
    private Settings settings;
    private volatile Demand demand;
    private volatile Frame latest;

    enum Input { SPECTRUM, WAVEFORM, LEVELS }

    enum Mode {
        BARS("Спектр", Icon.BARS, Input.SPECTRUM),
        WAVE("Волна", Icon.WAVE, Input.WAVEFORM),
        RING("Кольцо", Icon.DISC, Input.SPECTRUM),
        PULSE("Пульс", Icon.HEART, Input.SPECTRUM),
        STEREO("Стерео", Icon.SPEAKER, Input.LEVELS);

        final String label;
        final Icon icon;
        final Input input;

        Mode(String label, Icon icon, Input input) {
            this.label = label;
            this.icon = icon;
            this.input = input;
        }
    }

    private static final class Settings {
        boolean enabled = true;
        Mode mode = Mode.BARS;
    }

    private record Demand(long source, Input input, int volume, float sensitivity, long requestedAt) {}
    record Frame(long source, Input input, float[] values, long time) {}

    @Override
    public void initialize(MusicMod mod) {
        this.mod = mod;
        config = FabricLoader.getInstance().getConfigDir().resolve("yamusic/visualizer.json");
        settings = load();
    }

    @Override public boolean enabled() { return settings.enabled; }

    @Override
    public void setEnabled(boolean enabled) {
        settings.enabled = enabled;
        if (!enabled) hide();
        save();
    }

    @Override
    public void hide() {
        demand = null;
        latest = null;
    }

    Mode mode() { return settings.mode; }
    boolean hasTrack() { return mod.player.track() != null; }

    @Override
    public int modeControls(PlayerScreen screen, Rect header) {
        Font font = Minecraft.getInstance().font;
        Mode[] modes = Mode.values();
        int[] widths = new int[modes.length];
        int gap = 4, total = (modes.length - 1) * gap;
        for (int i = 0; i < modes.length; i++) {
            String label = modes[i].label;
            widths[i] = Math.max(width(font, label, Type.BODY), width(font, label, Type.STRONG)) + 25;
            total += widths[i];
        }
        boolean labels = total + width(font, "Визуализация", Type.TITLE) + 12 <= header.w();
        int right = header.right();
        for (int i = modes.length - 1; i >= 0; i--) {
            Mode mode = modes[i];
            int width = labels ? widths[i] : 21;
            right -= width;
            screen.button(new Rect(right, header.y(), width, 22), mode.label, mode.icon,
                mode == mode() ? Style.SELECTED : Style.FILLED, labels, false, () -> {
                    if (mode == mode()) return;
                    settings.mode = mode;
                    hide();
                    save();
                    screen.refreshPreferences();
                }).hint(mode.label);
            right -= gap;
        }
        return header.right() - right + 3;
    }

    @Override
    public AbstractWidget panel(Rect bounds) { return new VisualizerPanel(this, bounds); }

    Frame readAudio(Input input, long now) {
        long source = mod.player.visualizationSource();
        int volume = mod.settings.muted ? 0 : mod.settings.volume;
        if (source == 0 || volume == 0) {
            if (demand != null) hide();
            return null;
        }
        Demand previous = demand;
        float sensitivity = previous != null && previous.source == source && previous.volume() == volume
            ? previous.sensitivity : AudioVisualizer.sensitivity(volume);
        demand = new Demand(source, input, volume, sensitivity, now);
        Frame frame = latest;
        return frame != null && frame.source == source && frame.input == input && now - frame.time < REQUEST_LIFETIME ? frame : null;
    }

    @Override
    public boolean wantsSamples(long source) {
        Demand active = demand;
        return active != null && active.source == source && System.nanoTime() - active.requestedAt < REQUEST_LIFETIME;
    }

    @Override
    public Capture capture(long source, float sampleRate) {
        return new Capture() {
            private AudioVisualizer spectrum;
            private long lastSample;

            @Override
            public void samples(byte[] pcm, int length, int frameSize) {
                Demand active = demand;
                if (active == null || active.source != source || frameSize < 2 || length < frameSize) return;
                long now = System.nanoTime();
                if (now - active.requestedAt >= REQUEST_LIFETIME || lastSample != 0 && now - lastSample < SAMPLE_INTERVAL) return;
                lastSample = now;
                float[] values = switch (active.input) {
                    case WAVEFORM -> AudioVisualizer.waveform(pcm, length, frameSize, sampleRate, active.sensitivity);
                    case LEVELS -> AudioVisualizer.levels(pcm, length, frameSize, active.sensitivity);
                    case SPECTRUM -> {
                        if (spectrum == null) spectrum = new AudioVisualizer(sampleRate);
                        yield spectrum.spectrum(pcm, length, frameSize, active.sensitivity);
                    }
                };
                Demand current = demand;
                if (current != null && current.source == source && current.input == active.input) {
                    latest = new Frame(source, active.input, values, now);
                }
            }
        };
    }

    private Settings load() {
        try {
            if (Files.isRegularFile(config)) {
                Settings saved = JSON.fromJson(Files.readString(config), Settings.class);
                if (saved != null && saved.mode != null) return saved;
            } else {
                Path previous = config.resolveSibling("settings.json");
                if (Files.isRegularFile(previous)) {
                    var value = JsonParser.parseString(Files.readString(previous)).getAsJsonObject().get("visualizer");
                    if (value != null && value.isJsonPrimitive()) {
                        var migrated = new Settings();
                        migrated.mode = Mode.valueOf(value.getAsString());
                        return migrated;
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            MusicMod.LOG.warn("Could not load visualizer settings", failure);
        }
        return new Settings();
    }

    private void save() {
        try {
            Files.createDirectories(config.getParent());
            Files.writeString(config, JSON.toJson(settings));
        } catch (IOException failure) {
            MusicMod.LOG.warn("Could not save visualizer settings", failure);
        }
    }
}

