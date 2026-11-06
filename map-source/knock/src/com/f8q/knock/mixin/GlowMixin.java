package com.f8q.knock.mixin;

import com.f8q.knock.F8qKnock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class GlowMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("HEAD"),cancellable=true)
    private void f8qknock$party(Entity entity,CallbackInfoReturnable<Boolean> cir){
        if(F8qKnock.partyOutlineColor(entity)>=0)cir.setReturnValue(true);
    }
}
