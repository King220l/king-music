package studio.king220l.yamusic.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.king220l.yamusic.MusicMod;

@Mixin(KeyboardHandler.class)
abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void yamusic$handleShortcut(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (MusicMod.handleKey(window, action, event)) ci.cancel();
    }
}
