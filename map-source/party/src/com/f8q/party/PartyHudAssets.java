package com.f8q.party;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Uses the active resource pack's real health sprites, including XP-like HUD. */
public final class PartyHudAssets {
    private static final ResourceLocation HEART=ResourceLocation.fromNamespaceAndPath("f8qparty","textures/gui/downed_atlas.png");
    private static ResourceLocation sprite(String name){return ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/heart/"+name+".png");}
    public static void health(GuiGraphics g,int x,int y,int width,int height,float amount,int effect,float extra){
        float health=Math.max(0,Math.min(1,amount));String prefix=effect==2?"withered_":effect==1?"poisoned_":"";
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(width/81f,height/5f,1);
        // Nine source pixels per slot overlap by one exactly like the vanilla HUD.
        for(int i=0;i<10;i++){
            g.blit(sprite("container"),i*8,0,9,5,0f,2f,9,5,9,9);
            float units=health*20-i*2;if(units>0)g.blit(sprite(prefix+(units>=2?"full":"half")),i*8,0,9,5,0f,2f,9,5,9,9);
            float gold=Math.max(0,Math.min(1,extra))*20-i*2;if(gold>0)g.blit(sprite("absorbing_"+(gold>=2?"full":"half")),i*8,0,9,5,0f,2f,9,5,9,9);
        }g.pose().popPose();
    }
    public static void heart(GuiGraphics g,int centerX,int centerY,int half){
        int frame=(int)((System.nanoTime()/100_000_000L)%8);int u=(frame%4)*362+54,v=(frame/4)*362+428;
        g.blit(HEART,centerX-half,centerY-half,half*2,half*2,(float)u,(float)v,254,254,1448,1086);
    }
}
