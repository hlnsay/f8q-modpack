package com.f8q.party.mixin;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import team.creative.playerrevive.server.PlayerReviveServer;

@Mixin(ItemInHandRenderer.class)
public class DownedHandsMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
    private boolean keepHands(LocalPlayer player){return !PlayerReviveServer.getBleeding(player).isBleeding()&&player.isHandsBusy();}
}
