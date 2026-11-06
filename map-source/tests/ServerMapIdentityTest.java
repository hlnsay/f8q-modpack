package com.f8q.party;
import com.velorise.simplemap.client.*;
import com.velorise.simplemap.client.color.MapBlockData;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import com.google.gson.*;

public final class ServerMapIdentityTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static RegionDataStore.StoredRegion region(int index,String block){long[] pixels=new long[512*512];Arrays.fill(pixels,MapBlockData.EMPTY_PACKED);pixels[index]=MapBlockData.packRaw((short)70,(short)0,(byte)0,(byte)0);return new RegionDataStore.StoredRegion(pixels,new int[pixels.length],new String[]{"minecraft:plains"},new String[]{block});}
    public static void main(String[] args)throws Exception {
        check(ServerMapIdentity.canonicalAddress(" F8QUIT.EXAROTON.ME ",30096).equals("f8quit.exaroton.me:30096"),"verified SRV endpoint maps to explicit port");
        check(ServerMapIdentity.canonicalAddress("f8quit.exaroton.me:30096",30096).equals("f8quit.exaroton.me:30096"),"explicit port stable");
        check(ServerMapIdentity.canonicalAddress("f8quit.exaroton.me",25565).equals("f8quit.exaroton.me"),"different connected port not aliased");
        check(ServerMapIdentity.canonicalAddress("other.exaroton.me",30096).equals("other.exaroton.me"),"different server never combined");
        check(ServerMapIdentity.canonicalAddress("f8quit.exaroton.me:30097",30097).endsWith(":30097"),"other explicit port retained");
        Path root=Files.createTempDirectory("f8q-native-alias-test");Path old=root.resolve("old/overworld"),latest=root.resolve("alias/overworld");Files.createDirectories(old);Files.createDirectories(latest);
        Files.writeString(old.resolve(".dimension_id"),"minecraft:overworld");Files.writeString(latest.resolve(".dimension_id"),"minecraft:overworld");
        RegionDataStore.writeAtomic(old.resolve("r.0.0.smdat").toFile(),region(0,"minecraft:stone"));
        RegionDataStore.writeAtomic(latest.resolve("r.0.0.smdat").toFile(),region(20*512+20,"minecraft:grass_block"));
        RegionDataStore.writeAtomic(latest.resolve("r.1.0.smdat").toFile(),region(0,"minecraft:sand"));
        Files.writeString(latest.getParent().resolve("terrain.dat"),"must not import");Files.createDirectories(latest.resolve(".lod_v2"));Files.writeString(latest.resolve(".lod_v2/foreign.gz"),"must not import");
        Files.createDirectories(old.resolve(".lod_v2"));Files.writeString(old.resolve(".lod_v2/stale.gz"),"derived");
        String name="f8q-places-00000000-0000-0000-0000-000000000001.json";
        Path oldJournal=old.getParent().resolve(name),newJournal=latest.getParent().resolve(name);
        Files.writeString(oldJournal,"{\"version\":1,\"places\":{\"overworld\":{\"removed\":{}}},\"styles\":{\"custom\":{\"name\":\"Home\",\"color\":12}},\"routeId\":\"old\"}");
        Files.writeString(newJournal,"{\"version\":1,\"places\":{\"overworld\":{\"new\":{}}},\"styles\":{},\"routeId\":\"\"}");
        Files.setLastModifiedTime(oldJournal,FileTime.fromMillis(1000));Files.setLastModifiedTime(newJournal,FileTime.fromMillis(2000));
        check(ServerMapIdentity.recover(root,"alias","old"),"native region recovery performed");
        var merged=RegionDataStore.read(old.resolve("r.0.0.smdat").toFile());
        check(!MapBlockData.isEmpty(merged.pixels()[0]),"old observation retained");
        check(!MapBlockData.isEmpty(merged.pixels()[20*512+20]),"new observation retained");
        check(merged.blockPalette()[MapBlockData.blockId(merged.pixels()[20*512+20])].equals("minecraft:grass_block"),"palette indexes remapped");
        check(Files.exists(old.resolve("r.1.0.smdat")),"new region copied");
        check(!Files.exists(old.getParent().resolve("terrain.dat"))&&!Files.exists(old.resolve(".lod_v2/foreign.gz")),"no legacy raster or LOD import");
        check(Files.exists(old.getParent().resolve(".address-recovery-v22/overworld/r.0.0.smdat")),"prior native region backed up");
        var journal=JsonParser.parseString(Files.readString(oldJournal)).getAsJsonObject();
        check(journal.getAsJsonObject("styles").has("custom"),"personal style retained");
        check(!journal.getAsJsonObject("places").getAsJsonObject("overworld").has("removed"),"old deleted POI not resurrected");
        check(journal.get("routeId").getAsString().isEmpty(),"new route preference retained");
        check(!ServerMapIdentity.recover(root,"alias","old"),"migration runs once");
        boolean rejected=false;try{ServerMapIdentity.recover(root,"../escape","old");}catch(java.io.IOException e){rejected=true;}check(rejected,"path traversal rejected");
        System.out.println("PASS: verified endpoint aliases, native pixel/palette union, styles, deletions, backup, no JourneyMap imports, idempotence and path containment");
        if(args.length>0){
            Path saves=Path.of(args[0]);long before=count(saves.resolve("canonical"));long source=count(saves.resolve("alias"));long started=System.nanoTime();
            ServerMapIdentity.recover(saves,"alias","canonical");long after=count(saves.resolve("canonical"));
            check(after>=before&&after>=source,"real cache observations preserved");
            System.out.println("REAL_NATIVE_CACHE: old="+before+" alias="+source+" merged="+after+" cells; recoveryMs="+(System.nanoTime()-started)/1_000_000);
        }
    }
    private static long count(Path world)throws Exception{
        long result=0;try(var files=Files.walk(world)){for(Path p:files.filter(p->p.getParent().getFileName().toString().equals("overworld")&&p.getFileName().toString().endsWith(".smdat")&&!p.toString().contains(".address-recovery")).toList()){
            var data=RegionDataStore.read(p.toFile());for(long pixel:data.pixels())if(!MapBlockData.isEmpty(pixel))result++;
        }}return result;
    }
}
