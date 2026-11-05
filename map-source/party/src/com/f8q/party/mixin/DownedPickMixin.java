package com.f8q.party.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.creative.playerrevive.server.PlayerReviveServer;

/** A body on the ground is a low target and a backpack hides it: make it easier to aim at. */
@Mixin(Entity.class)
public class DownedPickMixin {
    @Inject(method = "getPickRadius", at = @At("HEAD"), cancellable = true)
    private void f8qparty$wider(CallbackInfoReturnable<Float> info) {
        Object self = this;
        if (self instanceof Player player && PlayerReviveServer.getBleeding(player).isBleeding()) {
            info.setReturnValue(0.6F);
        }
    }
}
