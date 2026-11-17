package studio.king220l.yamusic;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

final class MusicPlayer {
    private static final int MAX_GAIN = 10_000;
    record WaveBatch(MusicApi.WaveSession session, List<MusicApi.Track> tracks) {}
    private final MusicApi api;
    private final MusicSettings settings;
    private final MusicVisualizerExtension visualizer;
    private List<MusicApi.Track> queue = List.of();
    private List<MusicApi.Track> recent;
    private int index = -1;
    private Playback current;
    private MusicApi.Track editorTrack;
    private int editorRate = 4;
    private long seekRevision;
    private volatile int gain;
    private volatile int automaticKbps = MusicSettings.YandexQuality.OPTIMAL.targetKbps;
    private long audioSerial, visualizerSerial;
    private String error = "";
    private boolean completedPreview;
    private MusicApi.WaveSession wave;
    private WaveHistory seededHistory = WaveHistory.EMPTY;
    private boolean roomFollowing, roomHost;
    private Thread continuation;
    private int continuationRequest;

    MusicPlayer(MusicApi api, MusicSettings settings, MusicVisualizerExtension visualizer) {
        this.api = api;
        this.settings = settings;
        this.visualizer = visualizer;
        recent = settings.recentTracks();
        setVolume(settings.muted ? 0 : settings.volume);
    }

    MusicApi.Track track() {
        return index >= 0 ? queue.get(index) : null;
    }

    List<MusicApi.Track> recentTracks() { return recent; }

    boolean playingWave() { return wave != null; }

    boolean paused() {
        return current != null && current.paused;
    }

    boolean started() {
        return current != null && current.ready;
    }

    boolean loading() {
        return current != null && !started();
    }

    boolean preview() {
        return current == null ? completedPreview : current.audio.preview();
    }

    boolean seeking() {
        return loading() && current.startedAt != 0;
    }

    long durationMs() {
        MusicApi.Track selected = track();
        if (selected == null) return 0;
        return preview() ? Math.min(30_000, selected.durationMs()) : selected.durationMs();
    }

    boolean canSeek() {
        return durationMs() > 0 && (started() || seeking());
    }

    void seek(MusicApi.Track expected, long milliseconds) {
        if (expected != track() || !canSeek()) return;
        followSeek(milliseconds, current.paused);
        seekRevision++;
    }

    long seekRevision() { return seekRevision; }

    void editorRate(MusicApi.Track expected, int rate) {
        if (rate < 1 || rate > 6) throw new IllegalArgumentException("Playback rate");
        if (rate != 4 && (expected == null || expected.file() == null || expected != track())) return;
        if (rate == 4 && editorTrack != expected) return;
        if (editorTrack == expected && editorRate == rate) return;
        boolean playing = expected == track() && current != null;
        long position = playing ? elapsedMs() : 0;
        editorTrack = rate == 4 ? null : expected;
        editorRate = rate;
        if (playing) {
            var playback = current;
            start(new Playback(expected, position, playback.paused, playback.audio, playback.startedAt));
        }
    }

    void followSeek(long milliseconds, boolean paused) {
        if (!canSeek()) return;
        long target = Math.clamp(milliseconds, 0, Math.max(0, durationMs() - 250));
        var playback = current;
        start(new Playback(track(), target, paused, playback.audio, playback.startedAt));
    }

    void follow(MusicApi.Track track, long position, boolean paused) {
        if (track.file() != null || !api.signedIn()) return;
        clear();
        roomFollowing = true;
        queue = List.of(track);
        index = 0;
        long target = Math.clamp(position, 0, Math.max(0, track.durationMs() - 250));
        start(new Playback(track, target, paused, new TrackAudio(track), 0));
    }

    void endFollowing() { roomFollowing = false; }

    void setPaused(boolean paused) {
        if (current != null && current.paused != paused) current.toggle();
    }

    long playbackId() { return current == null ? 0 : current.audio.id; }

    void beginFollowing() {
        roomHost = false;
        roomFollowing = true;
        wave = null;
        cancelContinuation();
    }

    void beginHosting() {
        roomFollowing = false;
        roomHost = true;
        if (track() != null && track().file() != null) {
            clear();
            return;
        }
        if (queue.stream().noneMatch(track -> track.file() != null)) return;
        int retainedBefore = 0;
        for (int i = 0; i < index; i++) if (queue.get(i).file() == null) retainedBefore++;
        queue = queue.stream().filter(track -> track.file() == null).toList();
        if (index >= 0) index = retainedBefore;
    }

    void endHosting() { roomHost = false; }

    long startedAt() {
        return current == null ? 0 : current.startedAt;
    }

    long elapsedMs() {
        var playing = current;
        if (playing == null) return 0;
        return playing.paused ? playing.pausedPositionMs : playing.positionMs();
    }

    long recordPositionMs() { return elapsedMs(); }

    long visualizationSource() {
        var playing = current;
        return playing == null || !playing.ready || playing.paused ? 0 : playing.visualizerSource;
    }

    String error() {
        return error;
    }

    void setVolume(int percent) {
        gain = volumeGain(percent);
    }

    static int volumeGain(int percent) {
        int level = Math.clamp(percent, 0, 100);
        return level * level;
    }

    void play(List<MusicApi.Track> tracks, int position) {
        if (position < 0 || position >= tracks.size() || roomHost && tracks.get(position).file() != null) return;
        if (roomHost) {
            int selected = 0;
            for (int i = 0; i < position; i++) if (tracks.get(i).file() == null) selected++;
            tracks = tracks.stream().filter(track -> track.file() == null).toList();
            position = selected;
        }
        cancelContinuation();
        wave = null;
        roomFollowing = false;
        queue = List.copyOf(tracks);
        select(position);
    }

    void playRelated(MusicApi.Track track) {
        if (roomFollowing) return;
        play(List.of(track), 0);
        if (track() == track) {
            wave = api.waveByTrack(track);
            seededHistory = WaveHistory.EMPTY;
        }
    }

    WaveBatch loadWave() throws Exception {
        var session = api.waveSession();
        return new WaveBatch(session, api.wave(session, settings.waveHistory(session.accountId()), ""));
    }

    void playWave(WaveBatch batch) {
        if (roomFollowing || batch.tracks.isEmpty() || !api.waveActive(batch.session)) return;
        wave = batch.session;
        queue = List.copyOf(batch.tracks);
        select(0);
    }

    void shuffleUpcoming() {
        if (playingWave() || upcomingCount() < 2) return;
        ArrayList<MusicApi.Track> shuffled = new ArrayList<>(queue);
        Collections.shuffle(shuffled.subList(index + 1, shuffled.size()));
        queue = List.copyOf(shuffled);
    }

    boolean hasPrevious() {
        return index > 0 || elapsedMs() > 3_000;
    }

    boolean hasNext() {
        return upcomingCount() > 0 || wrapsQueue() || canContinueWave();
    }

    int upcomingCount() {
        return queue.size() - index - 1;
    }

    void previous() {
        if (elapsedMs() > 3_000) select(index);
        else if (hasPrevious()) select(index - 1);
    }

    void next() { next(true); }

    private void next(boolean manual) {
        // Only explicit skips are recorded here. Natural track endings are
        // still handled by heardAudio(), preserving the old playback behavior.
        if (manual && wave != null && track() != null && api.waveActive(wave)) {
            if (wave.seeded()) seededHistory = seededHistory.heard(track().id());
            else settings.rememberWaveSkip(wave.accountId(), track().id());
        }

        if (upcomingCount() > 0) select(index + 1);
        else if (wrapsQueue()) select(0);
        else if (canContinueWave()) fetchWaveContinuation();
    }

    private boolean wrapsQueue() {
        return wave == null && !queue.isEmpty() && settings.repeat == MusicSettings.Repeat.QUEUE;
    }

    void toggle() {
        if (current != null) current.toggle();
        else if (index >= 0) select(index);
        else if (hasNext()) next();
    }

    private void select(int position) {
        index = position;
        var track = queue.get(position);
        start(new Playback(track, 0, false, new TrackAudio(track), 0));
    }

    private void start(Playback selected) {
        cancelContinuation();
        if (current != null) {
            boolean switching = current.audio != selected.audio;
            current.cancel(switching);
            if (switching) current.audio.close();
        }
        error = "";
        completedPreview = false;
        current = selected;
        selected.worker = Thread.ofVirtual().name("yamusic-audio").unstarted(selected::run);
        selected.worker.start();
    }

    void stop() {
        cancelContinuation();
        if (current != null) {
            current.cancel(true);
            current.audio.close();
        }
        current = null;
        error = "";
        completedPreview = false;
    }

    void clear() {
        stop();
        queue = List.of();
        index = -1;
        wave = null;
    }

    private void finished(Playback playback, String failure) {
        if (current != playback) return;
        completedPreview = playback.audio.preview();
        playback.audio.close();
        current = null;
        if (failure != null) {
            error = failure;
        } else if (roomFollowing) {
            return;
        } else if (settings.repeat == MusicSettings.Repeat.TRACK) {
            select(index);
        } else if (!settings.autoAdvance) {
            return;
        } else if (hasNext()) {

        next(false);

        }
    }

    private boolean canContinueWave() {
        return track() != null && continuation == null && api.waveActive(wave);
    }

    private void fetchWaveContinuation() {
        int request = ++continuationRequest;
        var session = wave;
        var history = session.seeded() ? seededHistory : settings.waveHistory(session.accountId());
        String previous = track().id();
        continuation = MusicMod.request(() -> api.wave(session, history, previous), tracks -> {
            if (request != continuationRequest) return;
            continuation = null;
            playWave(new WaveBatch(session, tracks));
        }, message -> {
            if (request != continuationRequest) return;
            continuation = null;
            if (api.waveActive(session)) error = message;
        });
    }

    private void cancelContinuation() {
        continuationRequest++;
        if (continuation != null) continuation.interrupt();
        continuation = null;
    }

    private final class TrackAudio {
        private static final int SEEK_BUFFER = 16 * 1024 * 1024;
        private final ReentrantLock reader = new ReentrantLock();
        private final MusicApi.Track track;
        private final long id = ++audioSerial;
        private final MusicApi.WaveSession waveAtStart = wave;
        private final MusicSettings.YandexQuality quality = settings.yandexQuality;
        private int slowReads, steadyReads;
        private BufferedInputStream input;
        private volatile MusicApi.Stream stream;
        private boolean heard;

        TrackAudio(MusicApi.Track track) { this.track = track; }

        void heardAudio() {
            if (heard) return;
            heard = true;
            Minecraft.getInstance().execute(() -> {
                String waveAccount = null;
                if (track.file() == null && api.waveActive(waveAtStart)) {
                    if (waveAtStart.seeded()) {
                        if (waveAtStart == wave) seededHistory = seededHistory.heard(track.id());
                    } else waveAccount = waveAtStart.accountId();
                }
                if (settings.rememberPlayback(track, waveAccount)) recent = settings.recentTracks();
            });
        }

        boolean preview() {
            var resolved = stream;
            return resolved != null && resolved.preview();
        }

        InputStream rewind() throws Exception {
            if (track.file() != null) return Files.newInputStream(track.file());
            if (input != null) {
                try {
                    input.reset();
                } catch (IOException expiredMark) {
                    closeInput();
                }
            }
            if (input == null) {
                if (stream != null) {
                    try {
                        input = new BufferedInputStream(api.openAudio(stream.uri()));
                    } catch (IOException staleUrl) {
                        stream = null;
                    }
                }
                if (input == null) {
                    int target = quality == MusicSettings.YandexQuality.AUTO ? automaticKbps : quality.targetKbps;
                    stream = api.stream(track, target);
                    input = new BufferedInputStream(api.openAudio(stream.uri()));
                }
                input.mark(SEEK_BUFFER);
            }
            return new FilterInputStream(input) {
                @Override
                public void close() {}
            };
        }

        private void readTime(long nanos) {
            if (nanos >= 250_000_000L) {
                steadyReads = 0;
                if (++slowReads >= 2) automaticKbps = 64;
            } else if (++steadyReads == 800) {
                slowReads = 0;
                automaticKbps = automaticKbps < 192 ? 192 : 320;
                steadyReads = 0;
            }
        }

        private void closeInput() {
            if (input == null) return;
            try {
                input.close();
            } catch (IOException ignored) {} finally {
                input = null;
            }
        }

        void close() {
            Thread.startVirtualThread(() -> {
                reader.lock();
                try {
                    closeInput();
                } finally {
                    reader.unlock();
                }
            });
        }
    }

    private record PlaybackClock(SourceDataLine line, long originMicros) {}

    private final class Playback {
        private final MusicApi.Track track;
        private final long targetMicros;
        private final TrackAudio audio;
        private final int rate;
        private Thread worker;
        private volatile boolean cancelled;
        private volatile boolean paused;
        private volatile boolean ready;
        private volatile long startedAt;
        private volatile long elapsedMs;
        private volatile PlaybackClock clock;
        private PcmSpeed speed;
        private final long visualizerSource = ++visualizerSerial;
        private MusicVisualizerExtension.Capture capture;
        private long pausedPositionMs;

        Playback(MusicApi.Track track, long targetMs, boolean paused, TrackAudio audio, long startedAt) {
            this.track = track;
            targetMicros = targetMs * 1_000;
            elapsedMs = targetMs;
            pausedPositionMs = targetMs;
            this.paused = paused;
            this.audio = audio;
            this.startedAt = startedAt;
            rate = track == editorTrack ? editorRate : 4;
        }

        long positionMs() {
            PlaybackClock active = clock;
            long micros = active == null ? -1 : active.line.getMicrosecondPosition();
            return micros < 0 ? elapsedMs : (active.originMicros + micros * rate / 4) / 1_000;
        }

        synchronized void toggle() {
            if (!paused) pausedPositionMs = positionMs();
            paused = !paused;
            notifyAll();
        }

        synchronized void cancel(boolean interrupt) {
            cancelled = true;
            if (interrupt) worker.interrupt();
            notifyAll();
        }

        synchronized void waitForResume(SourceDataLine line) throws InterruptedException {
            if (!paused) return;
            line.stop();
            while (paused && !cancelled) wait();
            if (!cancelled) line.start();
        }

        void run() {
            String failure = null;
            try {
                audio.reader.lockInterruptibly();
                try {
                    if (!cancelled) {
                        if (track.file() != null && !LocalMusic.mp3(track.file())) decodePcm();
                        else decode(audio.rewind());
                    }
                } finally {
                    audio.reader.unlock();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (LineUnavailableException error) {
                failure = "Не удалось запустить звук. Проверь устройство вывода.";
            } catch (Exception error) {
                failure = track.file() == null ? MusicMod.message(error) : "Не удалось прочитать аудиофайл.";
            }
            if (!cancelled) {
                String result = failure;
                Minecraft.getInstance().execute(() -> finished(this, result));
            }
        }

        private void decode(InputStream audio) throws Exception {
            Bitstream bitstream = new Bitstream(audio);
            SourceDataLine output = null;
            try {
                if (this.audio.preview() && targetMicros >= 30_000_000)
                    throw new IOException("Для этого трека тебе доступен только фрагмент.");
                Decoder decoder = new Decoder();
                byte[] bytes = new byte[4_608];
                double decodedMicros = 0;
                long positionMicros = 0;
                Header header;
                boolean adaptive = track.file() == null && this.audio.quality == MusicSettings.YandexQuality.AUTO;
                while (!cancelled) {
                    long readStart = adaptive ? System.nanoTime() : 0;
                    header = bitstream.readFrame();
                    if (header == null) break;
                    if (adaptive && output != null) this.audio.readTime(System.nanoTime() - readStart);
                    SampleBuffer pcm = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    bitstream.closeFrame();
                    int firstSample = 0;
                    if (output == null) {
                        int rate = pcm.getSampleFrequency();
                        int channels = pcm.getChannelCount();
                        int frames = pcm.getBufferLength() / channels;
                        double microsPerFrame = 1_000_000.0 / rate;
                        int skip = Math.clamp((int) Math.ceil((targetMicros - decodedMicros) / microsPerFrame), 0, frames);
                        positionMicros = Math.round(decodedMicros + skip * microsPerFrame);
                        decodedMicros += frames * microsPerFrame;
                        if (skip == frames) continue;
                        firstSample = skip * channels;
                        if (cancelled) break;
                        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                        output = openLine(format, positionMicros);
                    }
                    waitForResume(output);
                    if (cancelled) break;
                    short[] samples = pcm.getBuffer();
                    int level = gain;
                    int sampleCount = pcm.getBufferLength() - firstSample;
                    for (int i = 0; i < sampleCount; i++) {
                        int sample = samples[firstSample + i] * level / MAX_GAIN;
                        bytes[i * 2] = (byte) sample;
                        bytes[i * 2 + 1] = (byte) (sample >> 8);
                    }
                    writeAudio(output, bytes, sampleCount * 2);
                }
                if (output == null && decodedMicros == 0 && !cancelled) throw new IOException("Не удалось получить аудио.");
                drain(output);
            } finally {
                if (output != null) closeLine(output);
                bitstream.close();
            }
        }

        private void decodePcm() throws Exception {
            try (var original = AudioSystem.getAudioInputStream(track.file().toFile())) {
                var source = original.getFormat();
                var pcm = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
                try (var data = AudioSystem.getAudioInputStream(pcm, original)) {
                    long frames = (long) (targetMicros * (double) pcm.getFrameRate() / 1_000_000);
                    if (data.getFrameLength() >= 0) frames = Math.min(frames, data.getFrameLength());
                    long position = Math.round(frames * 1_000_000.0 / pcm.getFrameRate());
                    data.skipNBytes(frames * pcm.getFrameSize());
                    if (cancelled) return;
                    var output = openLine(pcm, position);
                    try {
                        byte[] bytes = new byte[Math.max(1, (int) pcm.getFrameRate() / 40) * pcm.getFrameSize()];
                        while (!cancelled) {
                            waitForResume(output);
                            if (cancelled) break;
                            int count = data.read(bytes);
                            if (count < 0) break;
                            int level = gain;
                            if (level < MAX_GAIN) {
                                for (int i = 0; i < count; i += 2) {
                                    int sample = (short) ((bytes[i] & 255) | bytes[i + 1] << 8) * level / MAX_GAIN;
                                    bytes[i] = (byte) sample;
                                    bytes[i + 1] = (byte) (sample >> 8);
                                }
                            }
                            writeAudio(output, bytes, count);
                        }
                        drain(output);
                    } finally {
                        closeLine(output);
                    }
                }
            }
        }

        private void writeAudio(SourceDataLine output, byte[] bytes, int length) {
            if (speed != null) {
                length = speed.resample(bytes, length);
                bytes = speed.bytes();
            }
            if (length == 0) return;
            if (output.write(bytes, 0, length) > 0) audio.heardAudio();
            if (visualizer == null || !visualizer.wantsSamples(visualizerSource)) return;
            AudioFormat format = output.getFormat();
            if (length < format.getFrameSize()) return;
            if (capture == null) capture = visualizer.capture(visualizerSource, format.getSampleRate());
            capture.samples(bytes, length, format.getFrameSize());
        }

        private SourceDataLine openLine(AudioFormat format, long positionMicros) throws LineUnavailableException {
            SourceDataLine output = AudioSystem.getSourceDataLine(format);
            int buffer = (int) (format.getFrameRate() / 10) * format.getFrameSize();
            output.open(format, buffer);
            if (rate != 4) speed = new PcmSpeed(format.getChannels(), rate);
            if (!paused) output.start();
            if (startedAt == 0) startedAt = System.currentTimeMillis();
            elapsedMs = positionMicros / 1_000;
            clock = new PlaybackClock(output, positionMicros);
            ready = true;
            return output;
        }

        private void closeLine(SourceDataLine output) {
            elapsedMs = positionMs();
            clock = null;
            output.close();
        }

        private void drain(SourceDataLine output) throws InterruptedException {
            if (output == null || cancelled) return;
            waitForResume(output);
            if (!cancelled) output.drain();
        }
    }
}
