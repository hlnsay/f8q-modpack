package com.velorise.simplemap.client.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat charcoal action buttons matching the map's beige pixel frame. */
public final class MapActionButton extends Button {
    public MapActionButton(Component text,int x,int y,int w,int h,OnPress click){super(x,y,w,h,text,click,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        g.fill(getX(),getY(),getX()+width,getY()+height,isHoveredOrFocused()?0xFF302F26:0xFF171813);
        g.renderOutline(getX(),getY(),width,height,isHoveredOrFocused()?0xFFE9DEC0:0xFF706D59);
        g.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()+(height-8)/2,active?0xFFE9DEC0:0xFF777569);
    }
}
