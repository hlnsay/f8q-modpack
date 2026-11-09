package com.velorise.simplemap.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
/** Optional, bounded read-only recovery of voxel geometry for a player's ALREADY explored chunks. */
@EventBusSubscriber(modid="simplemap",bus=EventBusSubscriber.Bus.MOD)
public final class DioramaGeometry {
 public static final int BATCH=8,MAX_BYTES=512*1024;
 private static final Map<UUID,Long> LAST=new ConcurrentHashMap<>();
 private static final ExecutorService IO=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"F8Q-map-geometry-io");t.setDaemon(true);t.setPriority(2);return t;},new ThreadPoolExecutor.AbortPolicy());
 public record Request(long session,String dimension,long[] chunks) implements CustomPacketPayload {
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("simplemap","geometry_request_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,v)->{b.writeLong(v.session);b.writeUtf(v.dimension,128);b.writeVarInt(v.chunks.length);for(long k:v.chunks)b.writeLong(k);},b->{long session=b.readLong();String d=b.readUtf(128);int n=b.readVarInt();if(n<0||n>BATCH)throw new IllegalArgumentException("geometry request size");long[] chunks=new long[n];for(int i=0;i<n;i++)chunks[i]=b.readLong();return new Request(session,d,chunks);});
  public Type<Request> type(){return TYPE;}
 }
 public record Data(long session,String dimension,byte[] bytes) implements CustomPacketPayload {
  public static final Type<Data> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("simplemap","geometry_data_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Data> CODEC=StreamCodec.of((b,v)->{b.writeLong(v.session);b.writeUtf(v.dimension,128);b.writeByteArray(v.bytes);},b->new Data(b.readLong(),b.readUtf(128),b.readByteArray(MAX_BYTES)));
  public Type<Data> type(){return TYPE;}
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
  var r=event.registrar("f8q-geometry-1").optional();
  r.playToServer(Request.TYPE,Request.CODEC,DioramaGeometry::request);
  r.playToClient(Data.TYPE,Data.CODEC,(data,context)->context.enqueueWork(()->com.velorise.simplemap.client.DioramaView.get().acceptGeometry(data)));
 }
 private static void request(Request data,IPayloadContext context){context.enqueueWork(()->{
  if(!(context.player() instanceof ServerPlayer player))return;var level=player.serverLevel();if(!level.dimension().location().toString().equals(data.dimension))return;
  long now=System.currentTimeMillis();Long previous=LAST.put(player.getUUID(),now);if(previous!=null&&now-previous<200)return;if(LAST.size()>128)LAST.entrySet().removeIf(e->now-e.getValue()>60_000);
  var chunks=level.getChunkSource().chunkMap;List<CompletableFuture<Optional<CompoundTag>>> reads=new ArrayList<>();
  for(long key:data.chunks){int x=(int)(key>>32),z=(int)key;if(Math.abs((long)x)>1875000||Math.abs((long)z)>1875000)continue;reads.add(chunks.read(new ChunkPos(x,z)));}
  CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).whenComplete((unused,error)->{
   if(error!=null)return;try{IO.submit(()->{try{
    CompoundTag root=new CompoundTag();ListTag list=new ListTag();int estimated=0;
    for(var read:reads){var tag=read.join().orElse(null);if(tag==null||!tag.getString("Status").endsWith("full"))continue;
     // Deliberately omit block entities, entities, inventories, player data, ticks and structure metadata.
     CompoundTag copy=new CompoundTag();for(String k:List.of("DataVersion","xPos","zPos","yPos","Status","sections","Heightmaps")){Tag value=tag.get(k);if(value!=null)copy.put(k,value.copy());}
     for(Tag section:copy.getList("sections",10))if(section instanceof CompoundTag sec)sec.remove("block_entities");
     list.add(copy);
    }
    root.put("chunks",list);var out=new ByteArrayOutputStream();NbtIo.writeCompressed(root,out);
    byte[] bytes=out.toByteArray();if(bytes.length<=MAX_BYTES&&player.connection!=null)PacketDistributor.sendToPlayer(player,new Data(data.session,data.dimension,bytes));
   }catch(Exception e){System.err.println("F8Q geometry read failed: "+e);}});}catch(RejectedExecutionException ignored){}
  });
 });}
}
