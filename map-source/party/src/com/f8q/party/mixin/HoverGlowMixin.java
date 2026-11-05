package com.f8q.party.mixin;

import com.f8q.party.PartyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player under the crosshair gets a white outline, on this screen only. */
@Mixin(Minecraft.class)
public class HoverGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void f8qparty$hover(Entity entity, CallbackInfoReturnable<Boolean> info) {
        if (entity == PartyClient.hovered()) {
            info.setReturnValue(true);
        }
    }
}
