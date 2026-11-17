package studio.king220l.yamusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

final class MusicSettings {
    static final int MIN_UI_SCALE = 50;
    private static final int RECENT_LIMIT = 40;
    enum HudMode {
        OFF("Не показывать"), CHANGE("При смене трека"), ALWAYS("Всегда");

        final String label;

        HudMode(String label) {
            this.label = label;
        }
    }

    enum Repeat {
        OFF("Выключен"), TRACK("Текущий трек"), QUEUE("Весь список");

        final String label;

        Repeat(String label) {
            this.label = label;
        }
    }

    enum YandexQuality {
        AUTO("Авто", 192), EXCELLENT("Превосходное", Integer.MAX_VALUE),
        OPTIMAL("Оптимальное", 192), ECONOMY("Экономичное", 0);

        final String label;
        final int targetKbps;

        YandexQuality(String label, int targetKbps) {
            this.label = label;
            this.targetKbps = targetKbps;
        }

        YandexQuality next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    HudMode hud = HudMode.CHANGE;
    HudMode previousHud = HudMode.CHANGE;
    double x = 1.0;
    double y = 0.0;
    int seconds = 5;
    int volume = 60;
    boolean muted;
    boolean uiSounds = true;
    String language = "en_us";
    Repeat repeat = Repeat.OFF;
    boolean autoAdvance = true;
    boolean miniPlayer = true;
    boolean showArtist = true;
    int uiScale = 85;
    int hudScale = 90;
    int hudTextScale = 85;
    int hudOpacity = 90;
    static final String QUALITY_LABEL = "Качество музыки";
    static final String QUALITY_HINT = "Качество Яндекс Музыки";
    YandexQuality yandexQuality = YandexQuality.AUTO;
    String localFolder = "";
    private volatile WaveHistory waveHistory = WaveHistory.EMPTY;
    private List<RecentTrack> recent = List.of();
    EnumMap<MusicShortcuts.Action, MusicShortcuts.Binding> shortcuts;
    private transient Path path;

    private record RecentTrack(String id, String title, String artist, long duration, String file) {
        RecentTrack(MusicApi.Track track) {
            this(track.id(), track.title(), track.artist(), track.durationMs(), track.file() == null ? null : track.file().toString());
        }

        MusicApi.Track track() {
            if (id == null || id.isBlank() || title == null || title.isBlank()) return null;
            try {
                return new MusicApi.Track(id, title, artist == null ? "" : artist, Math.max(0, duration), file == null ? null : Path.of(file));
            } catch (java.nio.file.InvalidPathException invalid) {
                return null;
            }
        }

        String key() { return file == null ? WaveHistory.trackKey(id) : id; }
    }

    static MusicSettings load(Path path, Path options) {
        MusicSettings loaded = new MusicSettings();
        if (Files.isRegularFile(path)) {
            try {
                MusicSettings saved = GSON.fromJson(Files.readString(path), MusicSettings.class);
                if (saved != null) loaded = saved;
            } catch (IOException | RuntimeException ignored) {}
        }
        loaded.path = path;
        if (loaded.hud == null) loaded.hud = HudMode.CHANGE;
        if (loaded.previousHud == null || loaded.previousHud == HudMode.OFF) loaded.previousHud = HudMode.CHANGE;
        loaded.x = Double.isFinite(loaded.x) ? Math.clamp(loaded.x, 0, 1) : 1;
        loaded.y = Double.isFinite(loaded.y) ? Math.clamp(loaded.y, 0, 1) : 0;
        loaded.seconds = Math.clamp(loaded.seconds, 2, 20);
        loaded.volume = Math.clamp(loaded.volume, 0, 100);
        loaded.uiScale = Math.clamp(loaded.uiScale, MIN_UI_SCALE, 100);
        loaded.hudScale = Math.clamp(loaded.hudScale, 60, 150);
        loaded.hudTextScale = Math.clamp(loaded.hudTextScale, 70, 110);
        loaded.hudOpacity = Math.clamp(loaded.hudOpacity, 40, 100);
        if (loaded.repeat == null) loaded.repeat = Repeat.OFF;
        MusicI18n.initialize(path.getParent());
        if (loaded.language == null || !MusicI18n.hasLanguage(loaded.language)) loaded.language = "en_us";
        MusicI18n.use(loaded.language);
        if (loaded.yandexQuality == null) loaded.yandexQuality = YandexQuality.AUTO;
        if (loaded.localFolder == null) loaded.localFolder = "";
        loaded.recent = loaded.recent == null ? List.of() : loaded.recent.stream()
            .filter(entry -> entry != null && entry.track() != null).limit(RECENT_LIMIT).toList();
        if (loaded.shortcuts == null) {
            loaded.shortcuts = MusicShortcuts.migrate(options);
            loaded.shortcuts.put(MusicShortcuts.Action.EDITOR_MARK, MusicShortcuts.oldEditorMark(options));
        } else if (!loaded.shortcuts.containsKey(MusicShortcuts.Action.EDITOR_MARK)) {
            loaded.shortcuts.put(MusicShortcuts.Action.EDITOR_MARK, MusicShortcuts.oldEditorMark(options));
        }
        for (MusicShortcuts.Action action : MusicShortcuts.Action.values())
            loaded.shortcuts.putIfAbsent(action, action.defaultBinding);
        return loaded;
    }

    void toggleHud() {
        setHud(hud == HudMode.OFF ? previousHud : HudMode.OFF);
    }

    void setHud(HudMode mode) {
        hud = mode;
        if (mode != HudMode.OFF) previousHud = mode;
        save();
    }

    void cycleQuality() {
        yandexQuality = yandexQuality.next();
        save();
    }

    WaveHistory waveHistory(String account) {
        WaveHistory current = waveHistory;
        return current != null && current.account().equals(account) ? current : new WaveHistory(account, List.of());
    }

    // A manual skip should affect Wave recommendations even when audio
    // was skipped before the first audio buffer was decoded.
    void rememberWaveSkip(String account, String trackId) {
        WaveHistory before = waveHistory(account);
        WaveHistory after = before.heard(trackId);
        if (after != before) {
            waveHistory = after;
            save();
        }
    }

    List<MusicApi.Track> recentTracks() { return recent.stream().map(RecentTrack::track).filter(Objects::nonNull).toList(); }

    boolean rememberPlayback(MusicApi.Track track, String waveAccount) {
        var entry = new RecentTrack(track);
        boolean recentChanged = recent.isEmpty() || !entry.equals(recent.getFirst());
        if (recentChanged) {
            var next = new ArrayList<RecentTrack>(Math.min(RECENT_LIMIT, recent.size() + 1));
            next.add(entry);
            for (RecentTrack item : recent) {
                if (next.size() >= RECENT_LIMIT) break;
                if (!entry.key().equals(item.key())) next.add(item);
            }
            recent = List.copyOf(next);
        }
        boolean waveChanged = false;
        if (waveAccount != null) {
            WaveHistory current = waveHistory(waveAccount);
            WaveHistory updated = current.heard(track.id());
            waveChanged = updated != current;
            if (waveChanged) waveHistory = updated;
        }
        if (recentChanged || waveChanged) save();
        return recentChanged;
    }

    void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("settings.tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            MusicMod.LOG.warn("Could not save music settings", failure);
        }
    }
}
