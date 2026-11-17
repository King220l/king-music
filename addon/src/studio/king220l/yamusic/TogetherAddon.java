/*
 * ╔══════════════════════════════════════╗
 * ║                                      ║
 * ║         KING MUSIC TOGETHER          ║
 * ║               ── ◆ ──                ║
 * ║           Автор · King220l           ║
 * ║                                      ║
 * ╚══════════════════════════════════════╝
 */

package studio.king220l.yamusic;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.UUID;

public final class TogetherAddon implements MusicExtension {
    private static TogetherAddon active;
    private MusicMod mod;
    TogetherSettings settings;
    MusicSocial social;

    @Override
    public void initialize(MusicMod mod) {
        this.mod = mod;
        var directory = FabricLoader.getInstance().getConfigDir().resolve("yamusic");
        settings = TogetherSettings.load(directory.resolve("together.json"));
        social = new MusicSocial(mod, settings);
        active = this;
    }

    public static boolean musicBadge(UUID player) { return active != null && active.social.marks(player); }

    @Override
    public void tick(Minecraft game) {
        social.tick(game);
    }

    @Override
    public void stop() { social.stop(); }

    @Override
    public String settingsLabel() { return "Вместе"; }

    @Override
    public void openSettings(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new TogetherScreen(mod, parent, this));
    }

    @Override
    public boolean follows() { return social.following(); }

    @Override
    public boolean inRoom() { return social.room() != null; }

}
