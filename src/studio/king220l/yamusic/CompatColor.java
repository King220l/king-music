package studio.king220l.yamusic;

/** ARGB helpers used by the music UI, independent of newer Minecraft utilities. */
final class CompatColor {
    private CompatColor() {}
    static int alpha(int color) { return color >>> 24; }
    static int multiplyAlpha(int color, float factor) {
        return (color & 0xffffff) | (Math.clamp((int) (alpha(color) * factor), 0, 255) << 24);
    }
    static int srgbLerp(float delta, int from, int to) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= Math.clamp((int) (a + delta * (b - a)), 0, 255) << shift;
        }
        return result;
    }
    static int scaleRGB(int color, float scale) {
        return (color & 0xff000000) | (Math.clamp((int) ((color >> 16 & 255) * scale), 0, 255) << 16)
            | (Math.clamp((int) ((color >> 8 & 255) * scale), 0, 255) << 8)
            | Math.clamp((int) ((color & 255) * scale), 0, 255);
    }
}
