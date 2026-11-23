package studio.king220l.yamusic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

/** Fluent 2D operations backed by the 1.21.1 GUI pose stack. */
final class CompatPose {
    private final PoseStack stack;
    private CompatPose(PoseStack stack) { this.stack = stack; }
    static CompatPose of(GuiGraphics graphics) { return new CompatPose(graphics.pose()); }
    static void scissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        // Since 1.21.4 GuiGraphics transforms scissor rectangles using its current pose.
        graphics.enableScissor(left, top, right, bottom);
    }
    CompatPose pushMatrix() { stack.pushPose(); return this; }
    void popMatrix() { stack.popPose(); }
    CompatPose translate(float x, float y) { stack.translate(x, y, 0); return this; }
    CompatPose scale(float scale) { return scale(scale, scale); }
    CompatPose scale(float x, float y) { stack.scale(x, y, 1); return this; }
    CompatPose rotate(float radians) { stack.mulPose(Axis.ZP.rotation(radians)); return this; }
    float m00() { return stack.last().pose().m00(); }
    float m11() { return stack.last().pose().m11(); }
    float m20() { return stack.last().pose().m30(); }
    float m21() { return stack.last().pose().m31(); }
}
