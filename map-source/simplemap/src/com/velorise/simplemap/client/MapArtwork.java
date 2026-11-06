package com.velorise.simplemap.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

public final class MapArtwork {
    private static final ResourceLocation ARROW=ResourceLocation.fromNamespaceAndPath("simplemap","textures/gui/ui_chevron.png");
    private static final ResourceLocation FOG=ResourceLocation.fromNamespaceAndPath("simplemap","textures/gui/ui_fog.png");
    public static void arrow(GuiGraphics g,int centerX,int centerY,boolean right,boolean hover){
        g.pose().pushPose();g.pose().translate(centerX,centerY,0);
        if(!right)g.pose().mulPose(Axis.ZP.rotationDegrees(180));
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        float tint=hover?1f:.82f;RenderSystem.setShaderColor(tint,tint,tint,1);
        g.blit(ARROW,-5,-7,10,14,0,0,32,40,32,40);
        RenderSystem.setShaderColor(1,1,1,1);g.pose().popPose();
    }
    /** One static cloud quad; no particles, live noise, allocations or terrain rescan. */
    public static void fog(GuiGraphics g,int x,int y,int w,int h){
        RenderSystem.setShaderColor(1,1,1,1);
        g.blit(FOG,x,y,w,h,0,0,256,256,256,256);
    }
}
