package studio.king220l.yamusic;

import net.minecraft.client.gui.components.AbstractWidget;

interface MusicVisualizerExtension {
    void initialize(MusicMod mod);
    boolean enabled();
    void setEnabled(boolean enabled);
    void hide();
    int modeControls(MusicUi.PlayerScreen screen, MusicUi.Rect header);
    AbstractWidget panel(MusicUi.Rect bounds);
    boolean wantsSamples(long source);
    Capture capture(long source, float sampleRate);

    interface Capture {
        void samples(byte[] pcm, int length, int frameSize);
    }
}
