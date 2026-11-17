package studio.king220l.yamusic;

final class PcmSpeed {
    private final int step;
    private final int[] previous;
    private final int[] next;
    private byte[] output = new byte[0];
    private int phase;
    private boolean started;

    PcmSpeed(int channels, int step) {
        this.step = step;
        previous = new int[channels];
        next = new int[channels];
    }

    byte[] bytes() { return output; }

    int resample(byte[] source, int length) {
        int frame = previous.length * 2;
        if (output.length < length * 4) output = new byte[length * 4];
        int written = 0;
        for (int offset = 0; offset + frame <= length; offset += frame) {
            for (int ch = 0; ch < previous.length; ch++) {
                int at = offset + ch * 2;
                next[ch] = (short) ((source[at] & 255) | source[at + 1] << 8);
            }
            if (!started) {
                System.arraycopy(source, offset, output, written, frame);
                written += frame;
                phase = step;
                started = true;
            } else {
                while (phase <= 4) {
                    for (int ch = 0; ch < previous.length; ch++) {
                        int sample = (previous[ch] * (4 - phase) + next[ch] * phase) / 4;
                        output[written++] = (byte) sample;
                        output[written++] = (byte) (sample >> 8);
                    }
                    phase += step;
                }
                phase -= 4;
            }
            System.arraycopy(next, 0, previous, 0, next.length);
        }
        return written;
    }
}
