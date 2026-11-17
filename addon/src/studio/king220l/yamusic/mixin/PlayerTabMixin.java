package studio.king220l.yamusic.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.king220l.yamusic.TogetherAddon;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void appendMusicBadge(PlayerInfo player, CallbackInfoReturnable<Component> result) {
        if (TogetherAddon.musicBadge(player.getProfile().id())) {
            var badge = Component.literal(" ♪").withStyle(style -> style.withColor(0xc3a972));
            result.setReturnValue(result.getReturnValue().copy().append(badge));
        }
    }
}
