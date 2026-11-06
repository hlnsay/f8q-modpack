package com.f8q.knock.mixin;

import com.f8q.knock.F8qKnock;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class OutlineColorMixin {
    @Inject(method="getTeamColor",at=@At("HEAD"),cancellable=true)
    private void f8qknock$partyColor(CallbackInfoReturnable<Integer> cir){
        int color=F8qKnock.partyOutlineColor((Entity)(Object)this);
        if(color>=0)cir.setReturnValue(color);
    }
}
