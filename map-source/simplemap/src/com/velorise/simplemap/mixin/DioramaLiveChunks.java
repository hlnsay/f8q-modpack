package com.velorise.simplemap.mixin;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunk.class)
public abstract class DioramaLiveChunks {
 @Inject(method="setBlockState",at=@At("RETURN"))
 private void f8q$blockGeometry(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> cir){if(cir.getReturnValue()!=null)com.velorise.simplemap.client.DioramaView.changed((LevelChunk)(Object)this);}
}
