/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.world.item.Item
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.f8q.party.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"net.bittercat.pikafishing.item.misc.MagicConchShellItem"})
public class ConchWaystonesMixin {
    @Inject(method={"isAutomaticallyEligible(Lnet/minecraft/world/item/Item;)Z"}, at={@At(value="HEAD")}, cancellable=true)
    private static void f8q$skipWaystones(Item item, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if ("waystones".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
            callbackInfoReturnable.setReturnValue(false);
        }
    }
}
