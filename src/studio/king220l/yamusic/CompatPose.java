package studio.king220l.yamusic;

import org.joml.Matrix3x2fStack;
import net.minecraft.client.gui.GuiGraphics;

/** Fluent 2D operations backed by the Minecraft 1.21.6 GUI matrix stack. */
final class CompatPose {
    private final Matrix3x2fStack stack;
    private CompatPose(Matrix3x2fStack stack) { this.stack = stack; }
    static CompatPose of(GuiGraphics graphics) { return new CompatPose(graphics.pose()); }
    static void scissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.enableScissor(left, top, right, bottom);
    }
    CompatPose pushMatrix() { stack.pushMatrix(); return this; }
    void popMatrix() { stack.popMatrix(); }
    CompatPose translate(float x, float y) { stack.translate(x, y); return this; }
    CompatPose scale(float scale) { return scale(scale, scale); }
    CompatPose scale(float x, float y) { stack.scale(x, y); return this; }
    CompatPose rotate(float radians) { stack.rotate(radians); return this; }
    float m00() { return stack.m00(); }
    float m11() { return stack.m11(); }
    float m20() { return stack.m20(); }
    float m21() { return stack.m21(); }
}
