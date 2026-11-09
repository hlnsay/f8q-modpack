package com.velorise.simplemap.mixin;
import dev.diorama.render.MapViewport;
import dev.diorama.render.gpu.MapCanvas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** The original 0.7 fog predates 0.8's offscreen-layer parameter bit. Draw it directly, as in 0.7. */
@Mixin(value=MapViewport.class,remap=false)
public abstract class DioramaFogCompatibility {
 @Inject(method="drawFogLayer",at=@At("HEAD"),cancellable=true,remap=false)
 private void f8q$originalSoftClouds(MapCanvas canvas,MapViewport.View view,double u,double v,double span,int params,CallbackInfoReturnable<Boolean> cir){cir.setReturnValue(false);}
}
