package com.f8q.party.mixin;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.world.level.biome.Biome;
import com.velorise.simplemap.client.color.SurfaceColorizer;
import com.velorise.simplemap.client.color.MapBlockData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SurfaceColorizer.class)
public class LiveSurfaceColorMixin {
    @Inject(method="resolveBaseColor",at=@At("HEAD"),cancellable=true)
    private static void liveTint(long packed,int tint,List<String> palette,Map<String,Integer> colors,
        Map<String,SurfaceColorizer.BlockTintPolicy> policies,Set<String> disabled,IntFunction<Biome> biomes,
        long[] all,int px,int pz,int w,int h,int x,int z,int mode,CallbackInfoReturnable<Integer> ci){
        if(mode!=0||MapBlockData.isFluid(packed)||!SurfaceColorizer.SurfaceTintData.hasColor(tint))return;
        int index=MapBlockData.blockId(packed)&65535;if(index>=palette.size())return;String block=palette.get(index);
        if(disabled.contains(block))return;var visual=SurfaceColorizer.MapVisualClassifier.getInstance().info(block);
        if(visual!=null&&visual.fixedTextureColor())return;Integer texture=colors.get(block);if(texture==null||texture==0)return;
        ci.setReturnValue(SurfaceColorizer.applyBiomeTint(texture,0xff000000|SurfaceColorizer.SurfaceTintData.rgb(tint),1f));
    }
}
