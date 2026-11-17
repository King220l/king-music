package studio.king220l.yamusic;

import java.util.Arrays;

final class AudioVisualizer {
    static final int BAND_COUNT = 16;
    static final int WAVE_POINTS = 64;

    private static final int SIZE = 1024;
    private static final int BITS = Integer.numberOfTrailingZeros(SIZE);
    private static final double[] WINDOW = new double[SIZE];
    private static final double[] COS = new double[SIZE / 2];
    private static final double[] SIN = new double[SIZE / 2];

    static {
        for (int i = 0; i < SIZE; i++) WINDOW[i] = .5 - .5 * Math.cos(Math.TAU * i / (SIZE - 1));
        for (int i = 0; i < SIZE / 2; i++) {
            COS[i] = Math.cos(Math.TAU * i / SIZE);
            SIN[i] = -Math.sin(Math.TAU * i / SIZE);
        }
    }

    private final double[] real = new double[SIZE];
    private final double[] imaginary = new double[SIZE];
    private final int[] bandEdges = new int[BAND_COUNT + 1];

    AudioVisualizer(float sampleRate) {
        double low = Math.max(45, sampleRate / SIZE);
        double high = Math.min(14_000, sampleRate * .46);
        for (int i = 0; i <= BAND_COUNT; i++) {
            double frequency = low * Math.pow(high / low, i / (double) BAND_COUNT);
            int bin = (int) Math.round(frequency * SIZE / sampleRate);
            bandEdges[i] = Math.clamp(bin, i == 0 ? 1 : bandEdges[i - 1] + 1, SIZE / 2 - BAND_COUNT + i);
        }
    }

    static float sensitivity(int volume) {
        return Math.min(5f, (float) Math.pow(100f / Math.clamp(volume, 1, 100), .8));
    }

    static float[] waveform(byte[] pcm, int length, int frameSize, float sampleRate, float sensitivity) {
        int frames = length / frameSize;
        float[] wave = new float[WAVE_POINTS];
        int span = Math.min(frames, Math.max(1, (int) (sampleRate * .014)));
        for (int i = 0; i < wave.length; i++) {
            int frame = frames - span + (span - 1) * i / (wave.length - 1);
            wave[i] = sample(pcm, frame * frameSize, frameSize) * sensitivity;
        }
        return wave;
    }

    static float[] levels(byte[] pcm, int length, int frameSize, float sensitivity) {
        double leftEnergy = 0, rightEnergy = 0;
        int frames = length / frameSize;
        int rightOffset = frameSize >= 4 ? 2 : 0;
        for (int i = 0; i < frames; i++) {
            int offset = i * frameSize;
            float left = channel(pcm, offset);
            float right = channel(pcm, offset + rightOffset);
            leftEnergy += left * left;
            rightEnergy += right * right;
        }
        float gain = sensitivity * sensitivity;
        return new float[] { meter(leftEnergy * gain / frames), meter(rightEnergy * gain / frames) };
    }

    private static float meter(double energy) {
        return (float) Math.clamp((10 * Math.log10(Math.max(1e-8, energy)) + 52) / 52, 0, 1);
    }

    private static float channel(byte[] pcm, int offset) {
        return (short) ((pcm[offset] & 255) | pcm[offset + 1] << 8) / 32_768f;
    }

    float[] spectrum(byte[] pcm, int length, int frameSize, float sensitivity) {
        Arrays.fill(real, 0);
        Arrays.fill(imaginary, 0);
        int frames = length / frameSize, count = Math.min(frames, SIZE);
        for (int i = 0; i < count; i++) {
            int index = Integer.reverse(i) >>> (Integer.SIZE - BITS);
            real[index] = sample(pcm, (frames - count + i) * frameSize, frameSize) * WINDOW[i * SIZE / count];
        }
        transform();

        float[] bands = new float[BAND_COUNT];
        for (int band = 0; band < bands.length; band++) {
            double peak = 0;
            for (int bin = bandEdges[band]; bin < bandEdges[band + 1]; bin++) {
                peak = Math.max(peak, real[bin] * real[bin] + imaginary[bin] * imaginary[bin]);
            }
            double amplitude = Math.sqrt(peak) * 4 * sensitivity / count;
            bands[band] = (float) Math.clamp((20 * Math.log10(Math.max(1e-6, amplitude)) + 54) / 54, 0, 1);
        }
        return bands;
    }

    private void transform() {
        for (int width = 2; width <= SIZE; width *= 2) {
            int half = width / 2;
            for (int start = 0; start < SIZE; start += width) {
                for (int j = 0; j < half; j++) {
                    int left = start + j, right = left + half, angle = j * SIZE / width;
                    double r = real[right] * COS[angle] - imaginary[right] * SIN[angle];
                    double i = real[right] * SIN[angle] + imaginary[right] * COS[angle];
                    real[right] = real[left] - r;
                    imaginary[right] = imaginary[left] - i;
                    real[left] += r;
                    imaginary[left] += i;
                }
            }
        }
    }

    private static float sample(byte[] pcm, int offset, int frameSize) {
        int sum = 0;
        for (int i = offset; i < offset + frameSize; i += 2) {
            sum += (short) ((pcm[i] & 255) | pcm[i + 1] << 8);
        }
        return sum / (16_384f * frameSize);
    }
}
