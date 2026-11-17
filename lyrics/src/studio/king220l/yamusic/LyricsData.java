package studio.king220l.yamusic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

final class LyricsData {
    static final int MAX_TEXT = 262_144;
    static final int MAX_LINES = 2_000;
    static final LyricsData EMPTY = new LyricsData(List.of());
    private static final Pattern TIMES = Pattern.compile("\\[(\\d{1,4}):([0-5]\\d)(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d{1,8})\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("\\[(?:ar|al|ti|au|by|re|ve|length|offset):[^]]*\\]", Pattern.CASE_INSENSITIVE);

    record Line(long timeMs, String words) {}

    final List<Line> lines;
    private final int[] timed;

    LyricsData(List<Line> lines) {
        this.lines = List.copyOf(lines);
        timed = IntStream.range(0, lines.size()).filter(i -> lines.get(i).timeMs >= 0).boxed()
            .sorted(Comparator.comparingLong(i -> lines.get(i).timeMs)).mapToInt(Integer::intValue).toArray();
    }

    boolean synced() { return timed.length != 0; }

    int at(long position) {
        int cue = cueAt(position);
        return cue < 0 ? -1 : timed[cue];
    }

    int cueAt(long position) {
        int low = 0, high = timed.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(timed[mid]).timeMs <= position) low = mid + 1;
            else high = mid;
        }
        return low - 1;
    }

    String cueWords(int cue) {
        return cue < 0 || cue >= timed.length ? "" : lines.get(timed[cue]).words();
    }

    static LyricsData parse(String source) {
        var offset = OFFSET.matcher(source);
        long shift = offset.find() ? Long.parseLong(offset.group(1)) : 0;
        var rows = new ArrayList<Line>();
        for (String raw : source.replaceFirst("^\\uFEFF", "").split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || META.matcher(line).matches()) continue;
            var timestamps = new ArrayList<Long>();
            var match = TIMES.matcher(line);
            while (timestamps.size() < 100 && match.lookingAt()) {
                String fraction = match.group(3);
                int ms = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
                long time = Long.parseLong(match.group(1)) * 60_000 + Integer.parseInt(match.group(2)) * 1_000L + ms;
                timestamps.add(Math.max(0, time + shift));
                line = line.substring(match.end()).stripLeading();
                match.reset(line);
            }
            if (timestamps.isEmpty()) rows.add(new Line(-1, line));
            else for (long time : timestamps) {
                if (rows.size() == MAX_LINES) break;
                rows.add(new Line(time, line));
            }
            if (rows.size() == MAX_LINES) break;
        }
        if (rows.stream().allMatch(line -> line.timeMs >= 0)) rows.sort(Comparator.comparingLong(Line::timeMs));
        return new LyricsData(rows);
    }

    String plain() {
        return String.join("\n", lines.stream().map(Line::words).toList());
    }

    static String stamp(long ms) {
        return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, ms / 1_000 % 60, ms % 1_000);
    }

    static Path sidecar(Path file, String extension) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return file.toAbsolutePath().resolveSibling((dot > 0 ? name.substring(0, dot) : name) + extension);
    }

    static LyricsData read(Path audio) throws IOException {
        Path source = sidecar(audio, ".lrc");
        if (Files.notExists(source)) source = sidecar(audio, ".txt");
        if (Files.notExists(source)) return EMPTY;
        byte[] bytes;
        try (var input = Files.newInputStream(source)) {
            bytes = input.readNBytes(MAX_TEXT + 1);
        }
        if (bytes.length > MAX_TEXT) throw new IOException("Файл текста слишком большой.");
        if (bytes.length >= 2 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff))) {
            return parse(new String(bytes, StandardCharsets.UTF_16));
        }
        try {
            return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException invalidUtf8) {
            return parse(new String(bytes, Charset.forName("windows-1251")));
        }
    }

    Path save(Path audio) throws IOException {
        Path destination = sidecar(audio, ".lrc");
        Path temp = Files.createTempFile(destination.getParent(), "king-lyrics-", ".tmp");
        try {
            var output = new StringBuilder();
            for (Line line : lines) {
                if (line.timeMs >= 0) output.append('[').append(stamp(line.timeMs)).append(']');
                output.append(line.words).append('\n');
            }
            Files.writeString(temp, output, StandardCharsets.UTF_8);
            try {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException localShare) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
