package com.f8q.party;

import java.util.*;
import com.velorise.simplemap.client.*;
import com.f8q.knock.PartyOutlinePolicy;

public final class MapUiPolicyTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        String dim="minecraft:overworld";
        var a=new TeamMarks.Location("village-a","minecraft:village_plains",10,70,20,3,true);
        var b=new TeamMarks.Location("village-b","minecraft:village_plains",100,70,200,3,true);
        var undergroundDim=new PlaceEntry("other", "minecraft:the_nether",a,null,null);
        var manual=PlaceEntry.shared(new TeamMarks.Mark("Mate",0xff0000,a.title(),1,70,1,dim,true));
        var entries=List.of(PlaceEntry.automatic(dim,a),PlaceEntry.automatic(dim,b),undergroundDim,manual);
        var groups=PlaceGroups.collect(entries,List.of());
        check(groups.size()==3,"automatic places group but dimensions/manual marks stay separate");
        var group=groups.get(0);check(group.size()==2&&group.current().id().equals("village-a"),"first group member");
        group.cycle(1);check(group.current().x()==100&&group.index()==1,"next member uses real second coordinates");
        groups=PlaceGroups.collect(entries,groups);group=groups.get(0);
        check(group.current().id().equals("village-b"),"refresh preserves selected member");
        group.cycle(1);check(group.index()==0,"next wraps");group.cycle(-1);check(group.index()==1,"previous wraps");
        groups=PlaceGroups.collect(List.of(entries.get(0)),groups);check(groups.get(0).index()==0,"deleted group member falls back safely");
        var panel=new com.velorise.simplemap.client.gui.KnownPlacesPanel();
        var field=panel.getClass().getDeclaredField("collapsed");field.setAccessible(true);check(field.getBoolean(panel),"panel defaults collapsed");
        check(MapManager.MapConfig.getEffectiveCaveMapMode()==0,"cave mode unavailable even with saved settings");
        check(TeamMarks.obeliskTitle("Waystone").equals("Обелиск"),"default English name hidden");
        check(TeamMarks.obeliskTitle(" waySTONE ").equals("Обелиск"),"case and whitespace normalized");
        check(TeamMarks.obeliskTitle("Дом").equals("Обелиск [Дом]"),"meaningful custom name retained");
        var unknown=new TeamMarks.Location("closed","minecraft:castle",1,70,2,1,false);
        check(TeamMarks.routeIcon(unknown,"poi:1").equals("poi:unknown"),"old cached route cannot leak dungeon icon");
        check(TeamMarks.routeIcon(a,"poi:unknown").equals("poi:3"),"discovery upgrades route icon");
        check(TeamMarks.routeIcon(a,"minecraft:diamond").equals("minecraft:diamond"),"custom route icons preserved");
        for(boolean mate:new boolean[]{false,true})for(boolean alive:new boolean[]{false,true})
            for(boolean viewer:new boolean[]{false,true})for(boolean target:new boolean[]{false,true}){
                int expected=!mate||!alive?-1:target?0xF04444:viewer?0xFFFFFF:-1;
                check(PartyOutlinePolicy.color(viewer,target,mate,alive)==expected,"outline truth table");
            }
        System.out.println("PASS: grouped places, cycling, refresh/deletion, collapsed default, cave policy, obelisk names, unknown routes and party outlines");
    }
}
