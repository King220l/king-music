package studio.king220l.yamusic;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Internal input values; vanilla callbacks still use the 1.21.1 signatures. */
public final class CompatInput {
    private CompatInput() {}
    public record KeyEvent(int key, int scancode, int modifiers) {
        boolean isSelection() { return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE; }
        boolean hasShiftDown() { return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0; }
    }
    public record MouseButtonInfo(int button, int modifiers) {}
    public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) {
        int button() { return buttonInfo.button(); }
        boolean hasShiftDown() { return (buttonInfo.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0; }
    }
    static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers()));
    }
    public static int modifiers() {
        return (Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0)
            | (Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
            | (Screen.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
    }
}
