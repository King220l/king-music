package studio.king220l.yamusic.mixin;

import net.minecraft.client.KeyboardHandler;
import studio.king220l.yamusic.CompatInput.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.king220l.yamusic.MusicMod;

@Mixin(KeyboardHandler.class)
abstract class KeyboardMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void yamusic$handleShortcut(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (MusicMod.handleKey(window, action, new KeyEvent(key, scancode, modifiers))) ci.cancel();
    }
}
