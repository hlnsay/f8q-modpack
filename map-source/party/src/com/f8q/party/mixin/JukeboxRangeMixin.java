package com.f8q.party.mixin;

import com.f8q.party.JukeboxSound;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleSoundInstance.class)
public class JukeboxRangeMixin {
    @Inject(method = "forJukeboxSong", at = @At("HEAD"), cancellable = true)
    private static void f8qparty$near(SoundEvent song, Vec3 at, CallbackInfoReturnable<SimpleSoundInstance> info) {
        info.setReturnValue(new JukeboxSound(song, at));
    }
}
