package studio.king220l.yamusic;

import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

interface MusicLyricsExtension {
    void initialize(MusicMod mod, String category);
    void tick(Minecraft game);
    void stop();
    AbstractWidget panel(MusicUi.Rect bounds);
    boolean canEdit();
    void edit(Screen parent);
    void configure(Screen parent);
    default List<MusicUi.Preference> settings(MusicUi.PlayerScreen screen) {
        return List.of(new MusicUi.Preference("Оформление текста", row -> screen.valueButton(row,
            "Открыть", MusicTheme.Icon.GEAR, false, () -> configure(screen))));
    }
    void toggleHud();
    boolean shortcut(Minecraft game, Screen screen, MusicShortcuts.Binding key, int action);
}
