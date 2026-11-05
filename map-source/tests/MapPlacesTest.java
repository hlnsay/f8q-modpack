package com.f8q.party;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;
public final class MapPlacesTest {
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        String[] ids={"kingsandmonsters:ogre_outpost_plains","ribbits:ribbit_village","ribbits:evil_frog_village","minecraft:ruined_portal","minecraft:ocean_ruin_cold","minecraft:pillager_outpost","minecraft:swamp_hut","minecraft:ancient_city","unknownmod:castle","unknownmod:unusual_hut"};
        int[] kinds={5,6,7,10,8,9,2,1,1,4};
        check(PlaceKinds.classify("croaks:outpost")==7,"actual hostile Ribbits DLC namespace");
        check(PlaceKinds.classify("croaks:shed")==7,"hostile frog shed");
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
        var a=new com.velorise.simplemap.client.TeamMarks.Location("first","minecraft:ancient_city",100,10,200,1,true);
        var b=new com.velorise.simplemap.client.TeamMarks.Location("second","minecraft:spawner",12.5,84,12.5,1,true);
        var team=com.velorise.simplemap.client.TeamMarks.class;
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(a));
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(b));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==2,"delta retains earlier discoveries");
        check(a.title().equals("Древний город"),"actual structure title");
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:the_nether",List.of(b));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==2,"dimensions isolated");
        com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location("first","",0,0,0,-1,false)));
        check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").size()==1,"removed place invalidated");
        com.velorise.simplemap.client.TeamMarks.setLocations("",List.of());
        check(com.velorise.simplemap.client.TeamMarks.unknown("minecraft:the_nether").isEmpty(),"disconnect clears other world's places");
        System.out.println("PASS: classification, bbox positions, discovery distances, network roundtrip and packet bounds");
        System.out.println("PASS: incremental discoveries, real titles, dimensions, deletion and disconnect reset");
    }
}
