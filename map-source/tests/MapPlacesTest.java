package com.f8q.party;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;
public final class MapPlacesTest {
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        String[] ids={"kingsandmonsters:ogre_outpost_plains","ribbits:ribbit_village","ribbits:evil_frog_village","minecraft:ruined_portal","minecraft:ocean_ruin_cold","minecraft:pillager_outpost","minecraft:swamp_hut","minecraft:ancient_city","unknownmod:castle","unknownmod:unusual_hut"};
        int[] kinds={5,6,7,10,8,9,2,1,1,-1};
        check(PlaceKinds.classify("croaks:outpost")==7,"actual hostile Ribbits DLC namespace");
        check(PlaceKinds.classify("croaks:shed")==7,"hostile frog shed");
        check(PlaceKinds.classify("trek:overworld/medium/desert_outpost")==9,"verified pillager camp without pillager in registry ID");
        check(PlaceKinds.classify("trek:overworld/medium/jungle_outpost")==9,"verified jungle pillager outpost");
        check(PlaceKinds.classify("trek:overworld/rare/villager_castle")==3,"villager must not match illager substring");
        check(PlaceKinds.classify("unidentified:some_object")==-1,"unrecognised objects have no marker");
        check(PlaceKinds.classify("nova_structures:tavern_jungle")==3,"jungle tavern is a settlement");
        check(PlaceKinds.hidden("trek:monster_room/upper/default"),"TREK upper room grid rejected");
        check(PlaceKinds.hidden("trek:monster_room/lower/lush"),"TREK lower room grid rejected");
        check(PlaceKinds.portalUnderground("UNDERGROUND"),"buried vanilla portal rejected");
        check(PlaceKinds.portalUnderground("IN_MOUNTAIN"),"mountain portal rejected");
        check(!PlaceKinds.portalUnderground("ON_LAND_SURFACE"),"visible portal retained");
        check(PlaceKinds.buried(45,64),"buried ruins/camps excluded regardless of category");
        check(!PlaceKinds.buried(70,64),"above-ground ruins/camps retained");
        for(int i=0;i<ids.length;i++)check(PlaceKinds.classify(ids[i])==kinds[i],"classification "+ids[i]);
        check(PlaceKinds.center(-144,-113)==-128,"negative bbox center");
        check(PlaceKinds.center(100,199)==150,"actual footprint center, not start chunk");
        check(PlaceKinds.distance(150,150,100,199,100,199)==0,"reveal inside large structure");
        check(PlaceKinds.distance(250,150,100,199,100,199)==2601,"outside reveal threshold");
        var original=new Net.Unknowns("minecraft:overworld",List.of(
            new Net.Place("a","mod:ogre",-128,71,150,5,true),
            new Net.Place("b","",-126.5,12,-42.5,4,false)));
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        Net.Unknowns.CODEC.encode(buf,original);
        check(original.equals(Net.Unknowns.CODEC.decode(buf)),"packet exact coords + same chunk different places");
        check(!buf.isReadable(),"packet consumed");buf.release();
        var bad=new FriendlyByteBuf(Unpooled.buffer());bad.writeUtf("minecraft:overworld");bad.writeVarInt(129);
        boolean rejected=false;try{Net.Unknowns.CODEC.decode(bad);}catch(IllegalArgumentException e){rejected=true;}finally{bad.release();}
        check(rejected,"oversized packet rejected");
        var a=new com.velorise.simplemap.client.TeamMarks.Location("first","mod:surface_castle",100,90,200,1,true);
        var b=new com.velorise.simplemap.client.TeamMarks.Location("second","mod:tower",12.5,84,12.5,1,true);
        check(PlaceKinds.hidden("minecraft:spawner"),"spawner room excluded");
        check(PlaceKinds.hidden("minecraft:mineshaft"),"mineshafts excluded");
        check(PlaceKinds.hidden("minecraft:ancient_city"),"ancient cities excluded");
        check(PlaceKinds.undergroundStep("UNDERGROUND_STRUCTURES"),"modded underground generation excluded");
        check(!PlaceKinds.undergroundStep("SURFACE_STRUCTURES"),"surface modded structures retained");
        var team=com.velorise.simplemap.client.TeamMarks.class;
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(a));
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(b));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==2,"delta retains earlier discoveries");
        check(a.title().equals("Данж"),"generic dungeon title");
        check(com.velorise.simplemap.client.TeamMarks.structureTitle("nova_structures:wild_ruin",8).equals("Заброшенные руины"),"wild ruin translated");
        check(!com.velorise.simplemap.client.TeamMarks.structureTitle("unknown:random_ruin",8).matches(".*[A-Za-z].*"),"unknown ruins use Russian category");
        check(com.velorise.simplemap.client.TeamMarks.placeHalf(8,true)>com.velorise.simplemap.client.TeamMarks.placeHalf(1,true),"ruins icon enlarged");
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location("old-spawner","minecraft:spawner",0,0,0,1,true)));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==2,"old spawner marker rejected by client");
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:the_nether",List.of(b));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==2,"dimensions isolated");
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location("first","",0,0,0,-1,false)));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==1,"removed place invalidated");
        com.velorise.simplemap.client.TeamMarks.setLocations("",List.of());
        check(com.velorise.simplemap.client.TeamMarks.unknown("minecraft:the_nether").isEmpty(),"disconnect clears other world's places");
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location("unsupported","mod:something",0,0,0,4,true)));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").isEmpty(),"old generic interesting place suppressed");
        var layout=new com.velorise.simplemap.client.MapMarkerLayout();
        check(layout.label(0,0,100,12,false),"first visible label accepted");
        check(!layout.label(40,4,100,12,false),"overlapping label rejected");
        check(layout.label(40,4,100,12,true),"selected label stays visible");
        check(layout.icon(0,0,6,false),"first icon accepted");
        check(!layout.icon(5,5,6,false),"overlapping icon rejected");
        for(int i=0;i<130;i++) {
            float scale=com.velorise.simplemap.client.MapMarkerLayout.pulseScale(i/100.0);
            check(scale>=1.19&&scale<=1.37,"selection pulse stays enlarged and bounded");
        }
        System.out.println("PASS: classification, bbox positions, discovery distances, network roundtrip and packet bounds");
        System.out.println("PASS: incremental discoveries, real titles, dimensions, deletion and disconnect reset");
        System.out.println("PASS: TREK grid filtering, underground portals, settlements, Russian fallback, decluttering and selection pulse");
    }
}
