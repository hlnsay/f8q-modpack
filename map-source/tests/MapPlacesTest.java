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
        var shared=new Net.MarksUp(List.of(new Net.Spot(false,"Деревня",1,65,2,"minecraft:overworld","minecraft:compass",13)));
        var packet=new FriendlyByteBuf(Unpooled.buffer());Net.MarksUp.CODEC.encode(packet,shared);
        check(shared.equals(Net.MarksUp.CODEC.decode(packet)),"party marker actual icon survives protocol");packet.release();
        try {
            var dir=java.nio.file.Files.createTempDirectory("f8q-place-journal-");
            var path=dir.resolve("world-a-player-one.json");
            var journal=new com.velorise.simplemap.client.LocalPlaces.Journal(path);
            journal.receive("minecraft:overworld",List.of(a,b));journal.style(a.id(),"Моя крепость",0xff5555);journal.flush();
            var reloaded=new com.velorise.simplemap.client.LocalPlaces.Journal(path);
            check(reloaded.state.places.get("minecraft:overworld").size()==2,"discovered places survive restart");
            check(reloaded.state.styles.get(a.id()).name().equals("Моя крепость"),"personal title survives restart");
            check(reloaded.state.styles.get(a.id()).color()==0xff5555,"personal color survives restart");
            check(new com.velorise.simplemap.client.LocalPlaces.Journal(dir.resolve("world-b-player-one.json")).state.places.isEmpty(),"worlds isolated");
            check(new com.velorise.simplemap.client.LocalPlaces.Journal(dir.resolve("world-a-player-two.json")).state.styles.isEmpty(),"players isolated");
            java.nio.file.Files.delete(path);java.nio.file.Files.delete(dir);
        }catch(java.io.IOException e){throw new AssertionError(e);}
        check(com.velorise.simplemap.client.LocalPlaces.parseColor("red")==0xff5555,"Minecraft named color");
        check(com.velorise.simplemap.client.LocalPlaces.parseColor("&c")==0xff5555,"Minecraft formatting color");
        check(com.velorise.simplemap.client.LocalPlaces.parseColor("15")==0xffffff,"Minecraft numeric color");
        check(com.velorise.simplemap.client.LocalPlaces.parseColor("not-a-color")==null,"invalid color rejected");
        try {
            var field=Unknowns.class.getDeclaredField("GSON");field.setAccessible(true);var gson=(com.google.gson.Gson)field.get(null);
            Class<?> record=java.util.Arrays.stream(Unknowns.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Place")).findFirst().orElseThrow();
            Object place=gson.fromJson("{\"key\":\"verified\",\"dimension\":\"minecraft:overworld\",\"structure\":\"minecraft:village_plains\",\"kind\":3,\"x\":12.5,\"y\":70,\"z\":-32.5,\"minX\":0,\"maxX\":25,\"minZ\":-40,\"maxZ\":-25,\"block\":false}",record);
            var x=record.getDeclaredMethod("x");x.setAccessible(true);check(((Double)x.invoke(place))==12.5,"server record reload preserves exact coordinates with older Gson");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        try {
            var tmp=java.nio.file.Files.createTempDirectory("map-reconnect-");var file=tmp.resolve("account.json");
            com.velorise.simplemap.client.TeamMarks.openJournal(file);
            var discovered=new com.velorise.simplemap.client.TeamMarks.Location("reconnect", "minecraft:village_plains",32,70,64,3,true);
            com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(discovered));
            com.velorise.simplemap.client.TeamMarks.closeJournal();
            com.velorise.simplemap.client.TeamMarks.openJournal(file);
            check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").contains(discovered),"actual logout/rebind restores discovery without network packets");
            com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location("reconnect","minecraft:village_plains",32,70,64,4,false)));
            check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").contains(discovered),"stale reconnect delta cannot hide opened place");
            check(com.velorise.simplemap.client.TeamMarks.autoPlacesVisible(),"POIs visible without depending on cave projection");
            com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location("reconnect","",32,70,64,-1,false)));
            com.velorise.simplemap.client.TeamMarks.closeJournal();com.velorise.simplemap.client.TeamMarks.openJournal(file);
            check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").isEmpty(),"explicit removals remain removed after reload");
            com.velorise.simplemap.client.TeamMarks.closeJournal();java.nio.file.Files.delete(file);try(var files=java.nio.file.Files.list(tmp)){for(var extra:files.toList())java.nio.file.Files.delete(extra);}java.nio.file.Files.delete(tmp);
        } catch(java.io.IOException e){throw new AssertionError(e);}
        try {
            var root=java.nio.file.Files.createTempDirectory("early-delete-");var file=root.resolve("journal.json");
            var stone=new com.velorise.simplemap.client.TeamMarks.Location("broken-stone","waystones:waystone",1,120,1,0,true,"Дом");
            com.velorise.simplemap.client.TeamMarks.openJournal(file);com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(stone));com.velorise.simplemap.client.TeamMarks.closeJournal();
            com.velorise.simplemap.client.TeamMarks.setLocations("minecraft:overworld",List.of(new com.velorise.simplemap.client.TeamMarks.Location(stone.id(),"",0,0,0,-1,false)));
            com.velorise.simplemap.client.TeamMarks.openJournal(file);
            check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").isEmpty(),"deletion arriving before journal bind must not resurrect saved obelisk");
            com.velorise.simplemap.client.TeamMarks.closeJournal();java.nio.file.Files.delete(file);try(var files=java.nio.file.Files.list(root)){for(var extra:files.toList())java.nio.file.Files.delete(extra);}
            java.nio.file.Files.delete(root);
        }catch(java.io.IOException e){throw new AssertionError(e);}
        var obelisk=new com.velorise.simplemap.client.TeamMarks.Location("obelisk-test","waystones:waystone",10,70,12,0,true,"Дом");
        check(obelisk.title().equals("Обелиск [Дом]"),"obelisk uses actual Waystones name");
        var namedPacket=new Net.Unknowns("minecraft:overworld",List.of(new Net.Place("obelisk-test","waystones:waystone",10,70,12,0,true,"Дом")));
        var nameBuf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());Net.Unknowns.CODEC.encode(nameBuf,namedPacket);check(Net.Unknowns.CODEC.decode(nameBuf).equals(namedPacket),"obelisk name survives network codec");nameBuf.release();
        check(com.velorise.simplemap.client.WorldClock.format(0).equals("06:00"),"world clock sunrise offset");
        check(com.velorise.simplemap.client.WorldClock.format(6000).equals("12:00"),"world clock noon");
        check(com.velorise.simplemap.client.WorldClock.format(18000).equals("00:00"),"world clock midnight");
        int[] raster=new int[256];for(int i=0;i<256;i++)raster[i]=0xff000000|i;
        var tiles=new Net.TilesDown(java.util.UUID.randomUUID(),List.of(new Net.Tile("minecraft:overworld",-21,17,raster)));
        var tileBuf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());Net.TilesDown.CODEC.encode(tileBuf,tiles);var decoded=Net.TilesDown.CODEC.decode(tileBuf);
        check(decoded.owner().equals(tiles.owner())&&java.util.Arrays.equals(decoded.tiles().get(0).pixels(),raster),"party raster payload exact roundtrip");tileBuf.release();
        var own=new com.velorise.simplemap.client.TeamMarks.Location("shared-settlement","minecraft:village_plains",42,70,18,3,true);
        try {
            var root=java.nio.file.Files.createTempDirectory("party-learned-");var file=root.resolve("journal.json");var owner=java.util.UUID.randomUUID();
            com.velorise.simplemap.client.TeamMarks.openJournal(file);
            com.velorise.simplemap.client.TeamMarks.retainParty(java.util.Set.of(owner));
            com.velorise.simplemap.client.TeamMarks.setSharedLocations(owner,"minecraft:overworld",true,List.of(own));
            com.velorise.simplemap.client.TeamMarks.retainParty(java.util.Set.of());
            check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").contains(own),"learned party places survive leaving party");
            com.velorise.simplemap.client.TeamMarks.closeJournal();com.velorise.simplemap.client.TeamMarks.openJournal(file);
            check(com.velorise.simplemap.client.TeamMarks.locations("minecraft:overworld").contains(own),"learned party places survive reconnect");
            com.velorise.simplemap.client.TeamMarks.closeJournal();java.nio.file.Files.delete(file);try(var files=java.nio.file.Files.list(root)){for(var extra:files.toList())java.nio.file.Files.delete(extra);}
            java.nio.file.Files.delete(root);
        }catch(java.io.IOException e){throw new AssertionError(e);}
        var alice=java.util.UUID.randomUUID();var stranger=java.util.UUID.randomUUID();
        try {
            var f=Exploration.class.getDeclaredField("watched");f.setAccessible(true);
            var watched=(java.util.Map<java.util.UUID,java.util.Set<Exploration.Key>>)f.get(null);
            watched.put(alice,new java.util.HashSet<>(java.util.List.of(new Exploration.Key("minecraft:overworld",-2,3))));
            check(Exploration.knows(alice,"minecraft:overworld",-17,49),"own chunk coverage grants own POI discovery");
            check(!Exploration.knows(stranger,"minecraft:overworld",-17,49),"strangers cannot disclose POIs through global generated cache");Exploration.clear();
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        System.out.println("PASS: party icon codec, journal restart persistence, world/account isolation, personal names/colors and Minecraft color IDs");
    }
}
