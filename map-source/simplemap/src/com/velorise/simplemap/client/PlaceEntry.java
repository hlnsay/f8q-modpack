package com.velorise.simplemap.client;

import java.util.*;
import com.velorise.simplemap.client.waypoint.PinNavigation.WaypointManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.systems.RenderSystem;

/** One presentation for a generated place, personal waypoint or party marker. */
public record PlaceEntry(String id,String dimension,TeamMarks.Location location,WaypointManager.Waypoint waypoint,TeamMarks.Mark shared) {
    public String title(){return location!=null?location.title():waypoint!=null?waypoint.name:shared.title();}
    public String description(){return location!=null?location.description():waypoint!=null?(waypoint.deathPoint?"Место вашей гибели.":"Ваша личная метка."):"Метка участника пати: "+shared.owner()+".";}
    public String owner(){return location!=null?"Место":waypoint!=null?"Твоя метка":shared.owner();}
    public double x(){return location!=null?location.x():waypoint!=null?waypoint.x:shared.x();}
    public double y(){return location!=null?location.y():waypoint!=null?waypoint.y:shared.y();}
    public double z(){return location!=null?location.z():waypoint!=null?waypoint.z:shared.z();}
    public int color(){return location!=null?LocalPlaces.color(location.id()):shared!=null?shared.color():0xffffff;}
    public void icon(GuiGraphics g,float x,float y,int half){
        if(location!=null){TeamMarks.icon(g,location.open()?TeamMarks.placeIcon(location.kind()):TeamMarks.UNKNOWN,x,y,half,color());return;}
        if(waypoint!=null&&waypoint.deathPoint){TeamMarks.icon(g,TeamMarks.SKULL,x,y,half);return;}
        if(shared!=null){TeamMarks.markIcon(g,shared,x,y,half);return;}
        itemIcon(g,WaypointManager.resolveIconItemId(waypoint),x,y,half);
    }
    public static void itemIcon(GuiGraphics g,String item,float x,float y,int half){
        ResourceLocation id=ResourceLocation.tryParse(item==null?"":item);
        if(id==null||!BuiltInRegistries.ITEM.containsKey(id)){TeamMarks.cross(g,x,y,half,0xffffff,true);return;}
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(half/8f,half/8f,1);g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)),-8,-8);g.pose().popPose();RenderSystem.setShaderColor(1,1,1,1);
    }
    public static PlaceEntry automatic(String dim,TeamMarks.Location p){return new PlaceEntry(p.id(),dim,p,null,null);}
    public static PlaceEntry waypoint(WaypointManager.Waypoint w){return new PlaceEntry("waypoint:"+w.name+":"+w.x+":"+w.z,MapManager.normalizeDimensionResourceId(w.dimension),null,w,null);}
    public static PlaceEntry shared(TeamMarks.Mark m){return new PlaceEntry("party:"+m.owner()+":"+m.label()+":"+m.x()+":"+m.z(),m.dimension(),null,null,m);}
    public static List<PlaceEntry> all(){
        List<PlaceEntry> out=new ArrayList<>();
        if(LocalPlaces.showPois)TeamMarks.allLocations().forEach((dim,list)->{for(var p:list)if(p.open())out.add(automatic(dim,p));});
        for(var w:WaypointManager.getInstance().getAllWaypoints())out.add(waypoint(w));
        for(var m:TeamMarks.allVisible())out.add(shared(m));
        out.sort(Comparator.comparing(PlaceEntry::dimension).thenComparing(PlaceEntry::owner).thenComparing(PlaceEntry::title));
        return out;
    }
}
