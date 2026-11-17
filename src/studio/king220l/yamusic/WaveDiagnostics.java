package studio.king220l.yamusic;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** Local radio troubleshooting. Never record access tokens, HTTP bodies, or account IDs. */
final class WaveDiagnostics {
    private static final long MAX_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();
    private WaveDiagnostics() {}

    static String ids(List<MusicApi.Track> tracks) {
        return tracks.stream().limit(25).map(t -> WaveHistory.trackKey(t.id()))
            .collect(Collectors.joining(",", "[", tracks.size() > 25 ? ",...]" : "]"));
    }

    static void log(String event) {
        synchronized (LOCK) {
            try {
                Path path = FabricLoader.getInstance().getConfigDir().resolve("king-music-wave-debug.log");
                Files.createDirectories(path.getParent());
                if (Files.isRegularFile(path) && Files.size(path) > MAX_BYTES) {
                    Files.move(path, path.resolveSibling("king-music-wave-debug.previous.log"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                Files.writeString(path, Instant.now() + " " + event + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
                // Diagnostics must never interrupt playback.
            }
        }
    }
}
