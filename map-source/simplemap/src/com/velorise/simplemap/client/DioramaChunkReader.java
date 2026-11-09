package com.velorise.simplemap.client;
import com.mojang.serialization.Codec;
import dev.diorama.data.SavedChunk;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
/** Reads actual Anvil voxel sections. Never synthesizes block columns from the 2D cache. */
final class DioramaChunkReader {
 final ClientLevel level;final Path root;
 final java.util.Map<Long,SavedChunk> cache=new java.util.LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<Long,SavedChunk> e){return size()>96;}};
 final Codec<PalettedContainer<BlockState>> blocks;
 final Codec<PalettedContainerRO<Holder<Biome>>> biomes;
 DioramaChunkReader(ClientLevel level,Path root){this.level=level;this.root=root;
 blocks=PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY,BlockState.CODEC,PalettedContainer.Strategy.SECTION_STATES,Blocks.AIR.defaultBlockState());
 var registry=level.registryAccess().registryOrThrow(Registries.BIOME);
 biomes=PalettedContainer.codecRO(registry.asHolderIdMap(),registry.holderByNameCodec(),PalettedContainer.Strategy.SECTION_BIOMES,registry.getHolderOrThrow(Biomes.PLAINS));
 }
 CompoundTag readTag(int cx,int cz)throws IOException{
  Path single=root.resolve(cx+"."+cz+".nbt");
  if(Files.exists(single))return NbtIo.readCompressed(single,NbtAccounter.create(8*1024*1024));
  Path region=root.resolve("r."+(cx>>5)+"."+(cz>>5)+".mca");if(!Files.isRegularFile(region))return null;
  try(var f=new RandomAccessFile(region.toFile(),"r")){
   f.seek(((cx&31)+(cz&31)*32)*4L);int location=f.readInt();if(location==0)return null;
   long offset=(long)(location>>>8)*4096;if(offset+5>f.length())return null;f.seek(offset);
   int len=f.readInt();int codec=f.readUnsignedByte();if(len<1||len>8*1024*1024||offset+len+4>f.length())return null;
   byte[] data=new byte[len-1];f.readFully(data);InputStream in=new ByteArrayInputStream(data);
   if(codec==2)in=new InflaterInputStream(in);else if(codec==1)in=new GZIPInputStream(in);else if(codec!=3)return null;
   try(var stream=new DataInputStream(in)){return NbtIo.read(stream,NbtAccounter.create(8*1024*1024));}
  }
 }
 @SuppressWarnings("unchecked") SavedChunk read(int cx,int cz)throws IOException{
  long key=dev.diorama.data.ChunkSnapshot.key(cx,cz);SavedChunk existing=cache.get(key);if(existing!=null)return existing;
  CompoundTag tag=readTag(cx,cz);if(tag==null||!tag.getString("Status").endsWith("full"))return null;
  int count=level.getSectionsCount(),min=level.getMinSection();
  PalettedContainer<BlockState>[] states=new PalettedContainer[count];
  PalettedContainerRO<Holder<Biome>>[] bs=new PalettedContainerRO[count];
  DataLayer[] light=new DataLayer[count],sky=new DataLayer[count];
  var sections=tag.getList("sections",10);
  for(int s=0;s<sections.size();s++){var section=sections.getCompound(s);int at=section.getByte("Y")-min;if(at<0||at>=count)continue;
   states[at]=blocks.parse(NbtOps.INSTANCE,section.getCompound("block_states")).result().orElse(null);
   bs[at]=biomes.parse(NbtOps.INSTANCE,section.getCompound("biomes")).result().orElse(null);
   byte[] b=section.getByteArray("BlockLight"),k=section.getByteArray("SkyLight");if(b.length==2048)light[at]=new DataLayer(b);if(k.length==2048)sky[at]=new DataLayer(k);
  }
  var heights=tag.getCompound("Heightmaps");int[] surface=heights(heights.getLongArray("WORLD_SURFACE")),solid=heights(heights.getLongArray("MOTION_BLOCKING"));
  if(surface==null||solid==null)return null;
  SavedChunk result=new SavedChunk(cx,cz,true,states,bs,light,sky,surface,solid);cache.put(key,result);return result;
 }
 int[] heights(long[] packed){try{var storage=new SimpleBitStorage(Mth.ceillog2(level.getHeight()+1),256,packed);int[] out=new int[256];for(int i=0;i<256;i++)out[i]=storage.get(i)+level.getMinBuildHeight()-1;return out;}catch(RuntimeException e){return null;}}
}
