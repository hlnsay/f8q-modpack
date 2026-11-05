package com.f8q.party;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-player coverage and raster tiles. Party access is checked at every send. */
final class Exploration {
    record Key(String dim,int x,int z) {}
    private static final Map<UUID,LinkedHashMap<Key,Net.Tile>> tiles=new HashMap<>();
    private static final Map<UUID,Set<Key>> watched=new HashMap<>();
    private static final Map<UUID,Map<String,Integer>> sent=new HashMap<>();
    private static final Map<Net.Tile,Integer> tileHashes=new WeakHashMap<>();
    private static final Map<UUID,String> membership=new HashMap<>();
    private static boolean loaded;
    private static volatile boolean dirty;
    private static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"F8Q-party-map-save");t.setDaemon(true);return t;});
    private static java.util.concurrent.CompletableFuture<Void> diskJob=java.util.concurrent.CompletableFuture.completedFuture(null);
    private static Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("f8q-personal-exploration-v1.dat");}
    private static void load(MinecraftServer s){
        if(loaded)return;loaded=true;
        if(!Files.exists(file(s)))return;
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file(s))))){
            if(in.readInt()!=0x46385131)throw new IOException("exploration version");
            int players=in.readInt();if(players<0||players>4096)throw new IOException("player count");
            for(int i=0;i<players;i++){
                UUID id=new UUID(in.readLong(),in.readLong());int n=in.readInt();if(n<0||n>131072)throw new IOException("coverage count");
                var coverage=new HashSet<Key>();for(int j=0;j<n;j++)coverage.add(new Key(in.readUTF(),in.readInt(),in.readInt()));watched.put(id,coverage);
                n=in.readInt();if(n<0||n>131072)throw new IOException("tile count");var map=new LinkedHashMap<Key,Net.Tile>();
                for(int j=0;j<n;j++){Key k=new Key(in.readUTF(),in.readInt(),in.readInt());int[] p=new int[256];for(int a=0;a<256;a++)p[a]=in.readInt();map.put(k,new Net.Tile(k.dim,k.x,k.z,p));}tiles.put(id,map);
            }
        }catch(IOException e){F8qParty.LOG.warn("Could not load personal exploration",e);}
        Path flag=file(s).resolveSibling("f8q-native-only-v17.done");
        if(!Files.exists(flag))try{
            Path backup=file(s).resolveSibling("f8q-personal-exploration-before-v17.dat");
            if(Files.exists(file(s))&&!Files.exists(backup))Files.copy(file(s),backup);
            tiles.clear();dirty=true;save(s);
            diskJob.thenRunAsync(()->{try{Files.writeString(flag,"JourneyMap raster removed; personal coverage retained.\n");}catch(IOException e){F8qParty.LOG.warn("Could not finish map rollback marker",e);}},IO);
        }catch(IOException e){F8qParty.LOG.warn("Could not back up old raster; not marking rollback complete",e);}

    }
    static void observe(ServerPlayer p,ChunkPos pos){load(p.getServer());var set=watched.computeIfAbsent(p.getUUID(),k->new HashSet<>());if(set.size()<131072&&set.add(new Key(p.level().dimension().location().toString(),pos.x,pos.z)))dirty=true;}
    static boolean knows(UUID id,String dim,double x,double z){return watched.getOrDefault(id,Set.of()).contains(new Key(dim,((int)Math.floor(x))>>4,((int)Math.floor(z))>>4));}
    static void upload(ServerPlayer p,Net.TilesUp data){
        load(p.getServer());var map=tiles.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>());
        for(var tile:data.tiles()){
            Key k=new Key(tile.dimension(),tile.x(),tile.z());
            // A client may upload its personal saved map; it never grants discovery to other players.
            if(Math.abs((long)k.x)>1875000||Math.abs((long)k.z)>1875000||tile.pixels().length!=256)continue;
            var ownCoverage=watched.computeIfAbsent(p.getUUID(),id->new HashSet<>());if(ownCoverage.size()<131072&&ownCoverage.add(k))dirty=true;
            Net.Tile prior=map.get(k);if(prior==null||!Arrays.equals(prior.pixels(),tile.pixels())){map.put(k,tile);dirty=true;}
            while(map.size()>131072)map.remove(map.keySet().iterator().next());
        }
    }
    static void reconnect(UUID id){sent.remove(id);membership.remove(id);}
    static void tick(MinecraftServer s){
        load(s);if(s.getTickCount()%10!=0)return;
        for(ServerPlayer p:s.getPlayerList().getPlayers()){
            var party=F8qParty.partyOf(p.getUUID());String signature=party==null?"":party.stream().map(UUID::toString).sorted().reduce("",(a,b)->a+b);
            if(!signature.equals(membership.put(p.getUUID(),signature)))sent.remove(p.getUUID());
            if(party==null)continue;var history=sent.computeIfAbsent(p.getUUID(),k->new HashMap<>());
            for(UUID owner:party){if(owner.equals(p.getUUID()))continue;var source=tiles.get(owner);if(source==null)continue;
                List<Net.Tile> changes=new ArrayList<>();
                for(var entry:source.entrySet()){
                    String key=owner+":"+entry.getKey();int hash=tileHashes.computeIfAbsent(entry.getValue(),t->Arrays.hashCode(t.pixels()));
                    if(!Objects.equals(history.get(key),hash)){changes.add(entry.getValue());history.put(key,hash);if(changes.size()==64)break;}
                }
                if(!changes.isEmpty()){
                    var learned=tiles.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>());var coverage=watched.computeIfAbsent(p.getUUID(),k->new HashSet<>());
                    for(var tile:changes){var k=new Key(tile.dimension(),tile.x(),tile.z());learned.put(k,tile);if(coverage.size()<131072)coverage.add(k);}
                    while(learned.size()>131072)learned.remove(learned.keySet().iterator().next());dirty=true;
                    for(int offset=0;offset<changes.size();offset+=8)PacketDistributor.sendToPlayer(p,new Net.TilesDown(owner,List.copyOf(changes.subList(offset,Math.min(changes.size(),offset+8)))));
                }
            }
        }
        if(s.getTickCount()%1200==0)save(s);
    }
    static void save(MinecraftServer s){
        if(!dirty||!diskJob.isDone())return;
        var ids=new HashSet<UUID>(watched.keySet());ids.addAll(tiles.keySet());var coverage=new HashMap<UUID,Set<Key>>();var raster=new HashMap<UUID,List<Net.Tile>>();
        for(UUID id:ids){coverage.put(id,Set.copyOf(watched.getOrDefault(id,Set.of())));raster.put(id,List.copyOf(tiles.getOrDefault(id,new LinkedHashMap<>()).values()));}
        Path target=file(s);dirty=false;
        diskJob=java.util.concurrent.CompletableFuture.runAsync(()->{
            try{Path tmp=target.resolveSibling("f8q-personal-exploration-v1.tmp");
                try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))){
                    out.writeInt(0x46385131);out.writeInt(ids.size());
                    for(UUID id:ids){out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());var seen=coverage.get(id);out.writeInt(seen.size());for(Key k:seen){out.writeUTF(k.dim);out.writeInt(k.x);out.writeInt(k.z);}var map=raster.get(id);out.writeInt(map.size());for(var tile:map){out.writeUTF(tile.dimension());out.writeInt(tile.x());out.writeInt(tile.z());for(int pixel:tile.pixels())out.writeInt(pixel);}}
                }try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
            }catch(IOException e){dirty=true;throw new java.util.concurrent.CompletionException(e);}
        },IO);
    }
    static void flush(MinecraftServer s){try{diskJob.join();save(s);diskJob.join();}catch(RuntimeException e){F8qParty.LOG.warn("Could not flush personal exploration",e);}}
    static void clear(){tileHashes.clear();tiles.clear();watched.clear();sent.clear();membership.clear();loaded=dirty=false;}
}
