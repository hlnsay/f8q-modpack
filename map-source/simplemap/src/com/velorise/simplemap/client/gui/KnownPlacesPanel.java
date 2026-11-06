package com.velorise.simplemap.client.gui;

import com.velorise.simplemap.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Scrollable discovered places and personal/party marks, sharing one icon presentation. */
public final class KnownPlacesPanel {
    private List<PlaceEntry> entries=List.of();
    private List<PlaceGroups.Group> groups=List.of();
    private long refreshed;
    private double offset,target;
    private int x,y,w,h;
    private boolean collapsed=true,dragging;
    private static final int ROW=38;
    public void refresh(){refreshed=0;}
    private int bodyTop(){return y+28;}
    private int bodyBottom(){return y+h-18;}
    private double max(){return Math.max(0,groups.size()*ROW-Math.max(0,bodyBottom()-bodyTop()));}
    private boolean inside(double mx,double my){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    public void render(MapScreen parent,GuiGraphics g,int mouseX,int mouseY){
        var mc=Minecraft.getInstance();var font=mc.font;
        w=Math.min(200,Math.max(130,parent.width/3));x=parent.width-w-8;y=parent.placeListTop();h=Math.max(38,parent.height-y-25);
        if(collapsed){w=19;x=parent.width-w-2;h=30;
            g.pose().pushPose();g.pose().translate(0,0,85);
            MapArtwork.arrow(g,x+w/2,y+14,false,inside(mouseX,mouseY));g.pose().popPose();return;
        }
        if(System.nanoTime()-refreshed>1_000_000_000L){entries=PlaceEntry.all();groups=PlaceGroups.collect(entries,groups);refreshed=System.nanoTime();}
        g.pose().pushPose();g.pose().translate(0,0,85);
        MapPanels.places(g,x,y,w,h,28,collapsed?0:18);
        g.drawString(font,"Известные места · "+entries.size(),x+8,y+9,0xFFE9DEC0,false);
        MapArtwork.arrow(g,x+w-13,y+14,true,mouseX>=x+w-26&&mouseY>=y&&mouseY<y+28);
        if(!collapsed){
            target=Math.max(0,Math.min(max(),target));offset+=(target-offset)*.35;if(Math.abs(target-offset)<.2)offset=target;
            int first=Math.max(0,(int)(offset/ROW)),last=Math.min(groups.size(),first+(bodyBottom()-bodyTop())/ROW+2);
            g.enableScissor(x+4,bodyTop(),x+w-9,bodyBottom());
            for(int i=first;i<last;i++){
                var group=groups.get(i);var e=group.current();int rowY=bodyTop()+i*ROW-(int)Math.round(offset);
                boolean hover=mouseX>=x+4&&mouseX<x+w-10&&mouseY>=rowY&&mouseY<rowY+ROW;
                if(hover||parent.isSelectedEntry(e.id()))g.fill(x+4,rowY,x+w-10,rowY+ROW-1,0x553D3A2C);
                e.icon(g,x+17,rowY+14,9);
                g.drawString(font,font.plainSubstrByWidth(e.title(),w-42),x+33,rowY+5,e.shared()!=null?0xff000000|e.color():0xFFE6DEC8,false);
                String detail=e.owner();
                if(e.dimension().equals(MapManager.getInstance().getLiveDimensionResourceId())&&mc.player!=null)detail+=" · "+Math.round(Math.hypot(e.x()-mc.player.getX(),e.z()-mc.player.getZ()))+" м";
                else detail+=" · "+MapManager.displayDimensionName(e.dimension());
                g.drawString(font,font.plainSubstrByWidth(detail,group.size()>1?w-100:w-42),x+33,rowY+22,0xFFAAA38F,false);
                if(group.size()>1){
                    MapArtwork.arrow(g,x+w-62,rowY+26,false,hover&&mouseX>=x+w-72&&mouseX<x+w-51);
                    String page=(group.index()+1)+" из "+group.size();
                    g.drawCenteredString(font,page,x+w-37,rowY+22,0xFFCAC2AA);
                    MapArtwork.arrow(g,x+w-13,rowY+26,true,hover&&mouseX>=x+w-24);
                }
            }
            g.disableScissor();
            if(max()>0){int track=bodyBottom()-bodyTop(),thumb=Math.max(14,(int)(track*(track/(double)(groups.size()*ROW))));int ty=bodyTop()+(int)((track-thumb)*offset/max());g.fill(x+w-7,bodyTop(),x+w-5,bodyBottom(),0x553B3B32);g.fill(x+w-7,ty,x+w-5,ty+thumb,0xFFB2AA8D);}
            if(entries.isEmpty())g.drawString(font,"Пока нет открытых мест",x+9,bodyTop()+9,0xFFAAA38F,false);
            g.drawString(font,"ЛКМ — перейти к месту",x+8,y+h-13,0xFFAAA38F,false);
        }
        g.pose().popPose();
    }
    public boolean click(MapScreen parent,double mx,double my,int button){
        if(!inside(mx,my))return false;
        if(my<y+28){if(button==0)collapsed=!collapsed;return true;}
        if(collapsed)return true;
        if(button==0&&mx>=x+w-8){dragging=true;drag(mx,my);return true;}
        if(my>=bodyTop()&&my<bodyBottom()){
            int index=(int)((my-bodyTop()+offset)/ROW);
            if(index>=0&&index<groups.size()){
                var group=groups.get(index);
                if(button==0&&group.size()>1&&my>=bodyTop()+index*ROW-offset+16){
                    if(mx>=x+w-72&&mx<x+w-51){group.cycle(-1);parent.focusEntry(group.current());return true;}
                    if(mx>=x+w-24&&mx<x+w-8){group.cycle(1);parent.focusEntry(group.current());return true;}
                }
                if(button==0)parent.focusEntry(group.current());else if(button==1)parent.openPlacePopup(group.current(),mx,my);
            }
        }
        return true;
    }
    public boolean scroll(double mx,double my,double amount){if(collapsed||!inside(mx,my))return false;target=Math.max(0,Math.min(max(),target-amount*ROW*2));return true;}
    public boolean drag(double mx,double my){if(!dragging)return false;target=Math.max(0,Math.min(max(),((my-bodyTop())/Math.max(1.0,bodyBottom()-bodyTop()))*max()));offset=target;return true;}
    public void release(){dragging=false;}
}
