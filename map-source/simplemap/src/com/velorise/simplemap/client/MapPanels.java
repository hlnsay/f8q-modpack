package com.velorise.simplemap.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

/** User-supplied map-ui artwork, sliced once during the build, drawn with fixed pixel corners. */
public final class MapPanels {
    private static final ResourceLocation COORDS=id("coords"), ZOOM=id("zoom"), MENU=id("menu");
    private static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("simplemap","textures/gui/ui_"+s+".png");}
    public static void coords(GuiGraphics g,int x,int y,int w,int h){draw(g,COORDS,739,237,0,237,x,y,w,h);}
    public static void zoom(GuiGraphics g,int x,int y,int w,int h){draw(g,ZOOM,322,167,0,167,x,y,w,h);}
    public static void places(GuiGraphics g,int x,int y,int w,int h,int header,int footer){frame(g,id("places"),971,1619,49,49,874,1521,215,1385,x,y,w,h,header,footer);}
    public static void editor(GuiGraphics g,int x,int y,int w,int h,int header,int footer){frame(g,id("editor"),1122,1402,70,80,983,1239,176,1090,x,y,w,h,header,footer);}
    private static void frame(GuiGraphics g,ResourceLocation tex,int tw,int th,int sx,int sy,int sw,int sh,int headEnd,int footStart,int x,int y,int w,int h,int header,int footer){
        region(g,tex,tw,th,sx,sy,sw,headEnd,x,y,w,header);
        region(g,tex,tw,th,sx,sy+headEnd,sw,footStart-headEnd,x,y+header,w,Math.max(0,h-header-footer));
        if(footer>0)region(g,tex,tw,th,sx,sy+footStart,sw,sh-footStart,x,y+h-footer,w,footer);
    }
    private static void region(GuiGraphics g,ResourceLocation tex,int tw,int th,int sx,int sy,int sw,int sh,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        int s=Math.min(28,sh/3),d=Math.min(4,Math.min(w,h)/3);
        int[] xx={sx,sx+s,sx+sw-s,sx+sw},yy={sy,sy+s,sy+sh-s,sy+sh};
        int[] dx={x,x+d,x+w-d,x+w},dy={y,y+d,y+h-d,y+h};
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)g.blit(tex,dx[col],dy[row],dx[col+1]-dx[col],dy[row+1]-dy[row],xx[col],yy[row],xx[col+1]-xx[col],yy[row+1]-yy[row],tw,th);
    }
    public static void menu(GuiGraphics g,int x,int y,int w,int h,int header){
        int split=142;
        draw(g,MENU,1087,546,0,split,x,y,w,header);
        draw(g,MENU,1087,546,split,546-split,x,y+header,w,h-header);
    }
    private static void draw(GuiGraphics g,ResourceLocation tex,int tw,int th,int sy,int sh,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        int s=Math.min(32,sh/3),d=Math.min(3,Math.min(w,h)/3);
        int[] sx={0,s,tw-s,tw}, yy={sy,sy+s,sy+sh-s,sy+sh};
        int[] dx={x,x+d,x+w-d,x+w},dy={y,y+d,y+h-d,y+h};
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)
            g.blit(tex,dx[col],dy[row],dx[col+1]-dx[col],dy[row+1]-dy[row],(float)sx[col],(float)yy[row],sx[col+1]-sx[col],yy[row+1]-yy[row],tw,th);
    }
}
