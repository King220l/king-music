package studio.king220l.yamusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

interface MusicExtension {
    void initialize(MusicMod player);
    void tick(Minecraft game);
    void stop();
    String settingsLabel();
    void openSettings(Screen parent);
    boolean follows();
    default boolean inRoom() { return follows(); }
}
