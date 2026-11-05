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
    private static boolean loaded,dirty;
    private static Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("f8q-personal-exploration-v1.dat");}
    private static void load(MinecraftServer s){
        if(loaded)return;loaded=true;
        if(!Files.exists(file(s)))return;
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file(s))))){
            if(in.readInt()!=0x46385131)throw new IOException("exploration version");
            int players=in.readInt();if(players<0||players>4096)throw new IOException("player count");
            for(int i=0;i<players;i++){
                UUID id=new UUID(in.readLong(),in.readLong());int n=in.readInt();if(n<0||n>65536)throw new IOException("coverage count");
                var coverage=new HashSet<Key>();for(int j=0;j<n;j++)coverage.add(new Key(in.readUTF(),in.readInt(),in.readInt()));watched.put(id,coverage);
                n=in.readInt();if(n<0||n>16384)throw new IOException("tile count");var map=new LinkedHashMap<Key,Net.Tile>();
                for(int j=0;j<n;j++){Key k=new Key(in.readUTF(),in.readInt(),in.readInt());int[] p=new int[256];for(int a=0;a<256;a++)p[a]=in.readInt();map.put(k,new Net.Tile(k.dim,k.x,k.z,p));}tiles.put(id,map);
            }
        }catch(IOException e){F8qParty.LOG.warn("Could not load personal exploration",e);}
    }
    static void observe(ServerPlayer p,ChunkPos pos){load(p.getServer());var set=watched.computeIfAbsent(p.getUUID(),k->new HashSet<>());if(set.size()<65536&&set.add(new Key(p.level().dimension().location().toString(),pos.x,pos.z)))dirty=true;}
    static boolean knows(UUID id,String dim,double x,double z){return watched.getOrDefault(id,Set.of()).contains(new Key(dim,((int)Math.floor(x))>>4,((int)Math.floor(z))>>4));}
    static void upload(ServerPlayer p,Net.TilesUp data){
        load(p.getServer());var map=tiles.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>());
        for(var tile:data.tiles()){
            Key k=new Key(tile.dimension(),tile.x(),tile.z());
            // A client may upload its personal saved map; it never grants discovery to other players.
            if(Math.abs((long)k.x)>1875000||Math.abs((long)k.z)>1875000||tile.pixels().length!=256)continue;
            var ownCoverage=watched.computeIfAbsent(p.getUUID(),id->new HashSet<>());if(ownCoverage.size()<65536&&ownCoverage.add(k))dirty=true;
            Net.Tile prior=map.get(k);if(prior==null||!Arrays.equals(prior.pixels(),tile.pixels())){map.put(k,tile);dirty=true;}
            while(map.size()>16384)map.remove(map.keySet().iterator().next());
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
                    if(!Objects.equals(history.get(key),hash)){changes.add(entry.getValue());history.put(key,hash);if(changes.size()==8)break;}
                }
                if(!changes.isEmpty()){
                    var learned=tiles.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>());var coverage=watched.computeIfAbsent(p.getUUID(),k->new HashSet<>());
                    for(var tile:changes){var k=new Key(tile.dimension(),tile.x(),tile.z());learned.put(k,tile);coverage.add(k);}
                    while(learned.size()>16384)learned.remove(learned.keySet().iterator().next());dirty=true;
                    PacketDistributor.sendToPlayer(p,new Net.TilesDown(owner,changes));
                }
            }
        }
        if(s.getTickCount()%1200==0)save(s);
    }
    static void save(MinecraftServer s){
        if(!dirty)return;
        try{Path tmp=file(s).resolveSibling("f8q-personal-exploration-v1.tmp");
            try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))){
                out.writeInt(0x46385131);Set<UUID> ids=new HashSet<>(watched.keySet());ids.addAll(tiles.keySet());out.writeInt(ids.size());
                for(UUID id:ids){out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());var coverage=watched.getOrDefault(id,Set.of());out.writeInt(coverage.size());for(Key k:coverage){out.writeUTF(k.dim);out.writeInt(k.x);out.writeInt(k.z);}var map=tiles.getOrDefault(id,new LinkedHashMap<>());out.writeInt(map.size());for(var e:map.entrySet()){Key k=e.getKey();out.writeUTF(k.dim);out.writeInt(k.x);out.writeInt(k.z);for(int c:e.getValue().pixels())out.writeInt(c);}}
            }Files.move(tmp,file(s),StandardCopyOption.REPLACE_EXISTING);dirty=false;
        }catch(IOException e){F8qParty.LOG.warn("Could not save personal exploration",e);}
    }
    static void clear(){tileHashes.clear();tiles.clear();watched.clear();sent.clear();membership.clear();loaded=dirty=false;}
}
