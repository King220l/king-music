package studio.king220l.yamusic.smoke.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.king220l.yamusic.PortSmokeTest;

@Mixin(GameRenderer.class)
abstract class CompletedFrameMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("RETURN"))
    private void kingmusic$completedFrame(DeltaTracker delta, boolean tick, CallbackInfo ci) {
        PortSmokeTest.captureCompletedFrame();
    }
}
