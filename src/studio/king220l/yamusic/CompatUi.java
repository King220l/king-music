package studio.king220l.yamusic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class CompatUi {
    private CompatUi() {}
    static void tooltip(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.setTooltipForNextFrame(font, text, x, y);
    }
}
