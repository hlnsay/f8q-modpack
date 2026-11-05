package com.f8q.party;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
/** Personal server-observed coverage only. No party raster or exploration transfer. */
final class Exploration {
    record Key(String dim,int x,int z) {}
    private static final Map<UUID,Set<Key>> watched=new HashMap<>();
    private static boolean loaded;
    private static volatile boolean dirty;
    private static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"F8Q-personal-coverage-save");t.setDaemon(true);return t;});
    private static java.util.concurrent.CompletableFuture<Void> diskJob=java.util.concurrent.CompletableFuture.completedFuture(null);
    private static Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("f8q-personal-exploration-v1.dat");}
    private static void load(MinecraftServer s){
        if(loaded)return;loaded=true;if(!Files.exists(file(s)))return;
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file(s))),65536))){
            if(in.readInt()!=0x46385131)throw new IOException("exploration version");
            int players=in.readInt();if(players<0||players>4096)throw new IOException("player count");
            for(int i=0;i<players;i++){
                UUID id=new UUID(in.readLong(),in.readLong());int n=in.readInt();if(n<0||n>131072)throw new IOException("coverage count");
                var coverage=new HashSet<Key>();for(int j=0;j<n;j++)coverage.add(new Key(in.readUTF(),in.readInt(),in.readInt()));watched.put(id,coverage);
                n=in.readInt();if(n<0||n>131072)throw new IOException("tile count");
                // Discard obsolete imagery without allocating pixel arrays.
                for(int j=0;j<n;j++){in.readUTF();in.readInt();in.readInt();in.skipNBytes(1024);}if(n>0)dirty=true;
            }
        }catch(IOException e){F8qParty.LOG.warn("Could not load personal exploration",e);}
        if(dirty)save(s);
    }
    static void observe(ServerPlayer p,ChunkPos pos){load(p.getServer());var set=watched.computeIfAbsent(p.getUUID(),k->new HashSet<>());if(set.size()<131072&&set.add(new Key(p.level().dimension().location().toString(),pos.x,pos.z)))dirty=true;}
    static boolean knows(UUID id,String dim,double x,double z){return watched.getOrDefault(id,Set.of()).contains(new Key(dim,((int)Math.floor(x))>>4,((int)Math.floor(z))>>4));}
    static void upload(ServerPlayer p,Net.TilesUp data){ /* Old clients cannot restore imported imagery. */ }
    static void reconnect(UUID id) {}
    static void tick(MinecraftServer s){load(s);if(s.getTickCount()%1200==0)save(s);}
    static List<Key> snapshot(Set<Key> source){return new ArrayList<>(source);}
    static void save(MinecraftServer s){
        if(!dirty||!diskJob.isDone())return;
        // Dense x/z record hashes make Set.copyOf quadratic. This owned list needs no hashing.
        var coverage=new HashMap<UUID,List<Key>>();watched.forEach((id,keys)->coverage.put(id,snapshot(keys)));
        Path target=file(s);dirty=false;
        diskJob=java.util.concurrent.CompletableFuture.runAsync(()->{
            try{Path tmp=target.resolveSibling("f8q-personal-exploration-v1.tmp");
                try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)),65536))){
                    out.writeInt(0x46385131);out.writeInt(coverage.size());
                    for(var entry:coverage.entrySet()){UUID id=entry.getKey();out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());out.writeInt(entry.getValue().size());for(Key k:entry.getValue()){out.writeUTF(k.dim);out.writeInt(k.x);out.writeInt(k.z);}out.writeInt(0);}
                }
                try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
            }catch(IOException e){dirty=true;throw new java.util.concurrent.CompletionException(e);}
        },IO);
    }
    static void flush(MinecraftServer s){try{diskJob.join();save(s);diskJob.join();}catch(RuntimeException e){F8qParty.LOG.warn("Could not flush personal exploration",e);}}
    static void clear(){watched.clear();loaded=dirty=false;}
}
