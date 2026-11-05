package com.f8q.party;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual generated places only; no seed predictions, locate calls or forced chunk generation. */
final class Unknowns {
    private record Job(String dimension,long chunk) {}
    private record Origin(String dimension,int x,int z) {}
    private record Place(String key,String dimension,String structure,int kind,double x,double y,double z,
                         int minX,int maxX,int minZ,int maxZ,boolean block) {}
    private static final class Store {
        Map<String,Place> places=new HashMap<>();
        Map<String,Set<String>> known=new HashMap<>();
        Map<String,Set<String>> seen=new HashMap<>();
    }
    private static final Gson GSON=new com.google.gson.GsonBuilder()
        .registerTypeAdapter(Place.class,(com.google.gson.JsonDeserializer<Place>)(json,type,context)->{
            var o=json.getAsJsonObject();return new Place(o.get("key").getAsString(),o.get("dimension").getAsString(),o.get("structure").getAsString(),o.get("kind").getAsInt(),o.get("x").getAsDouble(),o.get("y").getAsDouble(),o.get("z").getAsDouble(),o.get("minX").getAsInt(),o.get("maxX").getAsInt(),o.get("minZ").getAsInt(),o.get("maxZ").getAsInt(),o.get("block").getAsBoolean());
        }).create();
    private static final Queue<Job> queue=new ConcurrentLinkedQueue<>();
    private static final Set<Job> pending=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Job,Long> scanned=new LinkedHashMap<>();
    private static final Map<UUID,Origin> origins=new HashMap<>();
    private static final Map<UUID,Map<String,Net.Place>> lastSent=new HashMap<>();
    private static Store store=new Store();
    private static boolean loaded,dirty;
    private static int saveClock;
    private static Path file(MinecraftServer s) {return s.getWorldPath(LevelResource.ROOT).resolve("f8q-surface-places-v3.json");}
    private static boolean hidden(MinecraftServer server,Place p) {
        if(PlaceKinds.hidden(p.structure))return true;
        for(ServerLevel level:server.getAllLevels())if(level.dimension().location().toString().equals(p.dimension)) {
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(net.minecraft.resources.ResourceLocation.parse(p.structure));
            return structure!=null&&PlaceKinds.undergroundStep(structure.step().name());
        }
        return false;
    }
    private static void load(MinecraftServer s) {
        if(loaded)return;loaded=true;
        try {
            if(!Files.exists(file(s))) {
                Path legacy=s.getWorldPath(LevelResource.ROOT).resolve("f8q-generated-places-v2.json");
                if(Files.exists(legacy)) {
                    Store old=GSON.fromJson(Files.readString(legacy),Store.class);
                    if(old!=null&&old.known!=null)store.known=old.known;
                }
                dirty=true;
            }
            if(Files.exists(file(s))) {
                Store read=GSON.fromJson(Files.readString(file(s)),Store.class);
                if(read!=null&&read.places!=null&&read.known!=null)store=read;
            }
        }catch(Exception e){F8qParty.LOG.warn("Could not read confirmed map places",e);}
        if(store.seen==null)store.seen=new HashMap<>();
        // Migration removes already saved underground markers, not just future discoveries.
        if(store.places.values().removeIf(p->hidden(s,p))) {
            for(var known:store.known.values())known.retainAll(store.places.keySet());
            dirty=true;
        }
    }
    private static void save(MinecraftServer s) {
        if(!dirty)return;
        try {
            Path tmp=file(s).resolveSibling("f8q-generated-places-v2.tmp");
            Files.writeString(tmp,GSON.toJson(store));Files.move(tmp,file(s),StandardCopyOption.REPLACE_EXISTING);dirty=false;
        }catch(Exception e){F8qParty.LOG.warn("Could not save confirmed map places",e);}
    }
    static void reconnect(UUID player){lastSent.remove(player);origins.remove(player);}
    static void stopped(){queue.clear();pending.clear();scanned.clear();origins.clear();lastSent.clear();store=new Store();loaded=dirty=false;saveClock=0;}
    static void stopping(MinecraftServer s){save(s);}
    /** FULL chunk was already sent to a player. Only queue a coordinate here. */
    static void sent(ChunkWatchEvent.Sent event){
        LevelChunk c=event.getChunk();
        if(c.getLevel() instanceof ServerLevel level)offer(level.dimension().location().toString(),c.getPos());
    }
    private static void offer(String dim,ChunkPos pos){Job j=new Job(dim,pos.toLong());if(pending.size()<8192&&pending.add(j))queue.offer(j);}
    private static void plan(ServerPlayer p){
        String dim=p.level().dimension().location().toString();ChunkPos at=p.chunkPosition();Origin old=origins.get(p.getUUID());
        if(old!=null&&old.dimension.equals(dim)&&Math.abs(old.x-at.x)<4&&Math.abs(old.z-at.z)<4)return;
        if(old==null||!old.dimension.equals(dim))lastSent.remove(p.getUUID());
        origins.put(p.getUUID(),new Origin(dim,at.x,at.z));
        int radius=Math.min(12,Math.max(2,p.getServer().getPlayerList().getViewDistance()));
        for(int r=0;r<=radius;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)
            if(Math.max(Math.abs(x),Math.abs(z))==r)offer(dim,new ChunkPos(at.x+x,at.z+z));
    }
    private static void put(Place p){if(!p.equals(store.places.put(p.key,p)))dirty=true;}
    private static void scan(MinecraftServer server,Job job){
        ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(job.dimension)));
        if(level==null)return;ChunkPos pos=new ChunkPos(job.chunk);
        // Non-loading API: a missing chunk is skipped, never requested/generated.
        LevelChunk chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);if(chunk==null)return;
        long now=level.getGameTime();Long prior=scanned.get(job);if(prior!=null&&now-prior<600)return;
        scanned.put(job,now);if(scanned.size()>16384)scanned.remove(scanned.keySet().iterator().next());
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<net.minecraft.world.level.levelgen.structure.Structure,net.minecraft.world.level.levelgen.structure.StructureStart> starts=new HashMap<>(chunk.getAllStarts());
        // Multiple starts of the same structure must not overwrite each other.
        List<net.minecraft.world.level.levelgen.structure.StructureStart> actual=new ArrayList<>(starts.values());
        Set<ChunkPos> references=new HashSet<>();
        for(var refs:chunk.getAllReferences().values())for(long ref:refs)references.add(new ChunkPos(ref));
        int checked=0;for(ChunkPos ref:references){
            if(++checked>64)break;
            LevelChunk loadedStart=level.getChunkSource().getChunkNow(ref.x,ref.z);
            if(loadedStart!=null)for(var start:loadedStart.getAllStarts().values())if(!actual.contains(start))actual.add(start);
        }
        actual.forEach(start->{
            if(start==null||!start.isValid())return;
            var structure=start.getStructure();var rid=registry.getKey(structure);if(rid==null)return;
            String id=rid.toString(),hints=id;
            if(PlaceKinds.hidden(id)||PlaceKinds.undergroundStep(structure.step().name()))return;
            if(id.contains("ribbit")||id.contains("frog")){
                int n=0;for(var piece:start.getPieces()){if(++n>128)break;if(piece instanceof PoolElementStructurePiece pool)hints+=" "+pool.getElement();}
            }
            if(PlaceKinds.classify(hints)<0)return;
            for(var piece:start.getPieces())if(piece instanceof net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece
                    &&piece instanceof com.f8q.party.mixin.PortalPlacementAccessor portal
                    &&PlaceKinds.portalUnderground(portal.f8q$getPlacement().name()))return;
            var b=start.getBoundingBox();double x=PlaceKinds.center(b.minX(),b.maxX()),z=PlaceKinds.center(b.minZ(),b.maxZ());
            // Check every category against real generated terrain, including portals and ruins.
            // Missing centre chunk means wait; never load or generate it to obtain a marker.
            LevelChunk centerChunk=level.getChunkSource().getChunkNow(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4);
            if(centerChunk==null)return;
            if(level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                int ground=centerChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,((int)Math.floor(x))&15,((int)Math.floor(z))&15);
                if(PlaceKinds.buried(b.maxY(),ground))return;
            }
            String key=job.dimension+"|"+id+"|"+b.minX()+","+b.minY()+","+b.minZ();
            put(new Place(key,job.dimension,id,PlaceKinds.classify(hints),x,PlaceKinds.center(b.minY(),b.maxY()),z,b.minX(),b.maxX(),b.minZ(),b.maxZ(),false));
        });
        Set<String> blocks=new HashSet<>();
        for(var entry:chunk.getBlockEntities().entrySet()){
            String id=BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlockState().getBlock()).toString();
            if(!id.contains("waystone")||id.contains("warp_plate")||id.contains("sharestone"))continue;
            BlockPos b=entry.getKey();
            if(level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                int ground=chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,b.getX()&15,b.getZ()&15);
                if(PlaceKinds.buried(b.getY()+2,ground))continue;
            }
            String key=job.dimension+"|block|"+b.asLong();blocks.add(key);
            put(new Place(key,job.dimension,id,0,b.getX()+.5,b.getY(),b.getZ()+.5,b.getX(),b.getX(),b.getZ(),b.getZ(),true));
        }
        if(store.places.values().removeIf(p->p.block&&p.dimension.equals(job.dimension)&&((int)Math.floor(p.x)>>4)==pos.x&&((int)Math.floor(p.z)>>4)==pos.z&&!blocks.contains(p.key)))dirty=true;
    }
    private static void send(ServerPlayer player){
        String dim=player.level().dimension().location().toString();Set<String> known=store.known.computeIfAbsent(player.getUUID().toString(),k->new HashSet<>());
        Set<String> seen=store.seen.computeIfAbsent(player.getUUID().toString(),k->new HashSet<>());
        List<Place> near=new ArrayList<>();
        for(Place p:store.places.values())if(p.dimension.equals(dim)&&((Math.abs(p.x-player.getX())<=4096&&Math.abs(p.z-player.getZ())<=4096)||known.contains(p.key)||seen.contains(p.key))){
            if(PlaceKinds.distance(player.getX(),player.getZ(),p.minX,p.maxX,p.minZ,p.maxZ)<=48*48&&known.add(p.key))dirty=true;
            if(seen.add(p.key))dirty=true;
            near.add(p);
        }
        near.sort(Comparator.comparingDouble(p->(p.x-player.getX())*(p.x-player.getX())+(p.z-player.getZ())*(p.z-player.getZ())));
        List<Net.Place> result=new ArrayList<>();
        for(Place p:near){
            boolean open=known.contains(p.key);
            result.add(new Net.Place(p.key,p.structure,p.x,p.y,p.z,open?p.kind:4,open));
        }
        BlockPos home=player.getRespawnPosition();
        if(home!=null&&player.getRespawnDimension().location().toString().equals(dim))result.add(new Net.Place("home:"+player.getUUID(),"minecraft:respawn",home.getX()+.5,home.getY(),home.getZ()+.5,11,true));
        Map<String,Net.Place> sent=lastSent.computeIfAbsent(player.getUUID(),k->new HashMap<>());
        List<Net.Place> changes=new ArrayList<>();
        for(var prior:new ArrayList<>(sent.values())) {
            boolean removed=prior.id().startsWith("home:") ? result.stream().noneMatch(p->p.id().equals(prior.id())) : !store.places.containsKey(prior.id());
            if(removed&&changes.size()<128){changes.add(new Net.Place(prior.id(),"",prior.x(),prior.y(),prior.z(),-1,false));sent.remove(prior.id());}
        }
        // Send bounded deltas: dense areas are paged across ticks, not silently truncated to 127 places.
        for(Net.Place place:result)if(changes.size()<128&&!place.equals(sent.get(place.id()))){changes.add(place);sent.put(place.id(),place);}
        if(!changes.isEmpty())PacketDistributor.sendToPlayer(player,new Net.Unknowns(dim,changes));
    }
    static void tick(MinecraftServer server){
        load(server);long deadline=System.nanoTime()+2_000_000L;
        for(int n=0;n<4&&!queue.isEmpty()&&System.nanoTime()<deadline;n++){Job j=queue.poll();if(j!=null){pending.remove(j);scan(server,j);}}
        if(server.getTickCount()%40==0){Set<UUID> active=new HashSet<>();for(ServerPlayer p:server.getPlayerList().getPlayers()){active.add(p.getUUID());plan(p);if(server.getTickCount()%600==0)offer(p.level().dimension().location().toString(),p.chunkPosition());send(p);}lastSent.keySet().retainAll(active);origins.keySet().retainAll(active);}
        if(++saveClock>=1200){saveClock=0;save(server);}
    }
}
