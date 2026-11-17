package studio.king220l.yamusic;

import javazoom.jl.decoder.Bitstream;

import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LocalMusic {
    private static final Set<String> FORMATS = Set.of("mp3", "wav", "aif", "aiff", "au");

    private LocalMusic() {}

    static List<MusicApi.Track> scan(Path directory) throws IOException, InterruptedException {
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            throw new IOException("Папка с музыкой недоступна.");
        }
        List<MusicApi.Track> tracks = new ArrayList<>();
        try (var files = Files.walk(directory)) {
            var iterator = files.filter(LocalMusic::supported).iterator();
            while (iterator.hasNext()) {
                if (Thread.interrupted()) throw new InterruptedException();
                tracks.add(describe(directory, iterator.next()));
            }
        }
        tracks.sort(Comparator.comparing(MusicApi.Track::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(MusicApi.Track::id));
        return List.copyOf(tracks);
    }

    static MusicApi.Track describe(Path directory, Path file) {
        String name = file.getFileName().toString();
        String title = name.substring(0, name.lastIndexOf('.'));
        String artist = directory.equals(file.getParent()) || file.getParent().getFileName() == null
            ? "Локальный файл" : file.getParent().getFileName().toString();
        int separator = title.indexOf(" - ");
        if (separator > 0 && separator + 3 < title.length()) {
            artist = title.substring(0, separator);
            title = title.substring(separator + 3);
        }
        return new MusicApi.Track(file.toUri().toString(), title, artist, duration(file), file);
    }

    static boolean supported(Path file) {
        var name = file.getFileName();
        return name != null && FORMATS.contains(extension(file)) && Files.isRegularFile(file);
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static boolean mp3(Path file) { return extension(file).equals("mp3"); }

    private static long duration(Path file) {
        try {
            if (mp3(file)) {
                try (var stream = Files.newInputStream(file)) {
                    var bitstream = new Bitstream(stream);
                    var header = bitstream.readFrame();
                    long bytes = Files.size(file) - bitstream.header_pos();
                    return header == null ? 0 : Math.max(0, Math.round(header.total_ms((int) Math.min(bytes, Integer.MAX_VALUE))));
                }
            }
            var audio = AudioSystem.getAudioFileFormat(file.toFile());
            return audio.getFrameLength() > 0 && audio.getFormat().getFrameRate() > 0
                ? Math.round(audio.getFrameLength() * 1000.0 / audio.getFormat().getFrameRate()) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
