package studio.king220l.yamusic.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.king220l.yamusic.MusicMod;

@Mixin(MouseHandler.class)
abstract class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void yamusic$handleMouse(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (MusicMod.handleMouse(window, action, button)) ci.cancel();
    }
}
