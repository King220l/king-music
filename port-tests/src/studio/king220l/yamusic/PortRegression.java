package studio.king220l.yamusic;

import java.nio.file.Files;
import java.util.Arrays;

/** Audio and lyrics regression tests with no account, network or game required. */
public final class PortRegression {
    static int passed;
    static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        var data = LyricsData.parse("[offset:100]\n[00:01.00]One\n[00:02.50][00:04.00]Two");
        check(data.at(1099) == -1 && data.at(1100) == 0, "LRC offset and first cue boundary");
        check(data.cueWords(data.cueAt(2600)).equals("Two") && data.lines.size() == 3, "Multiple timestamps on one lyric line");
        check(!LyricsData.parse("One\nTwo").synced(), "Plain lyrics stay untimed");
        var directory = Files.createTempDirectory("king-music-lyrics-test-");
        try {
            var audio = directory.resolve("sample.wav");
            var path = data.save(audio);
            var restored = LyricsData.read(audio);
            check(restored.lines.equals(data.lines), "LRC atomic save and reload preserve timestamps and words");
            Files.delete(path);
        } finally { Files.delete(directory); }

        byte[] silence = new byte[4096];
        var analyzer = new AudioVisualizer(44100);
        check(Arrays.equals(analyzer.spectrum(silence, silence.length, 4, 1), new float[16]), "Silent PCM has no spectrum energy");
        check(Arrays.equals(AudioVisualizer.levels(silence, silence.length, 4, 1), new float[2]), "Silent stereo has zero meters");
        byte[] pcm = new byte[4096];
        for (int frame = 0; frame < pcm.length / 4; frame++) {
            short sample = (short) (16000 * Math.sin(2 * Math.PI * 1000 * frame / 44100));
            pcm[frame * 4] = (byte) sample;
            pcm[frame * 4 + 1] = (byte) (sample >> 8);
        }
        float[] levels = AudioVisualizer.levels(pcm, pcm.length, 4, 1);
        check(levels[0] > .5f && levels[1] == 0, "Stereo meters distinguish active left from silent right");
        float[] bands = analyzer.spectrum(pcm, pcm.length, 4, 1);
        float peak = 0;
        for (float band : bands) {
            check(Float.isFinite(band) && band >= 0 && band <= 1, "FFT band stays finite and bounded");
            peak = Math.max(peak, band);
        }
        check(peak > .5f, "1 kHz tone produces spectrum energy");
        var resampler = new PcmSpeed(2, 4);
        int count = resampler.resample(pcm, pcm.length);
        check(count == pcm.length && Arrays.equals(Arrays.copyOf(resampler.bytes(), count), pcm), "Normal speed preserves stereo PCM exactly");
        var subscriber = MusicHttp.text(4).apply(null);
        boolean[] cancelled = {false};
        subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() {
            public void request(long amount) {}
            public void cancel() { cancelled[0] = true; }
        });
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[] {65, 66})));
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[] {67, 68, 69})));
        check(cancelled[0] && subscriber.getBody().toCompletableFuture().isCompletedExceptionally(),
            "Oversized chunked HTTP responses cancel the subscription before buffering beyond the limit");
        var exact = MusicHttp.text(2).apply(null);
        exact.onSubscribe(new java.util.concurrent.Flow.Subscription() {
            public void request(long amount) {}
            public void cancel() { throw new AssertionError("Unexpected cancellation"); }
        });
        exact.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[] {65, 66})));
        exact.onComplete();
        check(exact.getBody().toCompletableFuture().join().equals("AB"), "HTTP response exactly at the limit succeeds");
        System.out.println("Port regression checks passed: " + passed);
    }
}
