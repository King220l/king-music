package studio.king220l.yamusic.mixin;

import net.minecraft.client.MouseHandler;
import studio.king220l.yamusic.CompatInput.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.king220l.yamusic.MusicMod;

@Mixin(MouseHandler.class)
abstract class MouseMixin {
    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void yamusic$handleMouse(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (MusicMod.handleMouse(window, action, new MouseButtonInfo(button, modifiers))) ci.cancel();
    }
}
