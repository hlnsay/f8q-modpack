package com.f8q.party.mixin;

import com.f8q.party.Kneel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after Punchy Moves has posed the player, so the kneel wins over its walk and idle. */
@Pseudo
@Mixin(targets = "punchymoves.client.PlayerAnimationEngine")
public class KneelPoseMixin {
    @Inject(method = "apply(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/PlayerModel;FFF)V", at = @At("RETURN"))
    private static void f8qparty$kneel(AbstractClientPlayer player, PlayerModel<?> model, float a, float b, float c, CallbackInfo info) {
        Kneel.apply(player, model);
    }
}
