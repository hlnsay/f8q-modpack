package com.velorise.simplemap.mixin;
import com.velorise.simplemap.client.DioramaView;
import com.velorise.simplemap.client.renderer.MinimapFramebufferRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=MinimapFramebufferRenderer.class,remap=false)
public abstract class DioramaMinimap {
 @Inject(method="render",at=@At("HEAD"),cancellable=true,remap=false)
 private void f8q$voxelMini(GuiGraphics g,int x,int y,int size,double cx,double cz,float zoom,boolean rotate,float partial,CallbackInfoReturnable<Boolean> cir){if(DioramaView.get().renderMini(g,x,y,size,cx,cz,zoom,rotate,partial))cir.setReturnValue(true);}
 @Inject(method="projectWorldToHud",at=@At("HEAD"),cancellable=true,remap=false)
 private void f8q$voxelMarker(double x,double z,CallbackInfoReturnable<MinimapFramebufferRenderer.HudPoint> cir){var point=DioramaView.get().miniPoint(x,z);if(point!=null)cir.setReturnValue(point);}
}
