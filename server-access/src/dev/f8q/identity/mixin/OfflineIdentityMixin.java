package dev.f8q.identity.mixin;
import dev.f8q.identity.F8QIdentity;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class OfflineIdentityMixin {
    @Redirect(method="handleHello", at=@At(value="INVOKE", target="Lnet/minecraft/core/UUIDUtil;createOfflineProfile(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;"))
    private GameProfile f8q$preserveIdentity(String name) { return F8QIdentity.profile(name); }
}
