package com.velorise.simplemap.client;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.velorise.simplemap.client.color.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

/** Learned raster layer. Imported/shared pixels remain separate from live block records. */
public final class SharedExploration {
    public record Tile(String dimension,int x,int z,int[] pixels) {}
    private record Key(String dim,int x,int z) {}
    private static final Map<UUID,Map<Key,Tile>> owners=new HashMap<>();
    private static final Map<Key,Atlas> atlases=new LinkedHashMap<>();
    private static final Set<Key> atlasKeys=new HashSet<>();
    private static final ArrayDeque<Tile> uploadHistory=new ArrayDeque<>();
    private static final Map<Key,Integer> uploaded=new HashMap<>();
    private static Set<UUID> allowed=Set.of();
    private static int clock,regionCursor,chunkCursor;
    private static List<String> regions=List.of();
    private static String session="";
    private static final Set<Key> changedAtlases=new HashSet<>();
    private static boolean dirty;
    private static volatile boolean persistDirty;
    private static volatile Path file;
    private static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"F8Q-map-journal-save");t.setDaemon(true);return t;});
    private static java.util.concurrent.CompletableFuture<Void> diskJob=java.util.concurrent.CompletableFuture.completedFuture(null);

    public static void bind(Path path){
        if(Objects.equals(file,path))return;clear();file=path;
        if(Files.exists(path))try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))){
            if(in.readInt()!=0x46385131)throw new IOException("map raster version");int count=in.readInt();if(count<0||count>131072)throw new IOException("map raster count");
            var map=owners.computeIfAbsent(new UUID(0,0),k->new HashMap<>());
            for(int i=0;i<count;i++){String d=in.readUTF();int x=in.readInt(),z=in.readInt();int[] pixels=new int[256];for(int n=0;n<256;n++)pixels[n]=in.readInt();Tile tile=new Tile(d,x,z,pixels);map.put(new Key(d,x,z),tile);atlasKeys.add(new Key(d,x>>3,z>>3));uploadHistory.add(tile);}dirty=true;
        }catch(IOException e){System.err.println("F8Q: could not restore shared exploration: "+e.getMessage());}
        LegacyMapImport.start(path);
    }
    public static void importLegacy(Path target,List<Tile> tiles,Path marker,Path source){
        if(!Objects.equals(file,target))return;var map=owners.computeIfAbsent(new UUID(0,0),k->new HashMap<>());
        var union=new HashSet<>(map.keySet());for(var tile:tiles)union.add(new Key(tile.dimension(),tile.x(),tile.z()));
        if(union.size()>131072){System.err.println("F8Q: legacy merge too large; original cache kept intact");return;}
        for(Tile tile:tiles){Key key=new Key(tile.dimension(),tile.x(),tile.z());Tile old=map.get(key);int[] pixels=tile.pixels();
            if(old!=null){pixels=old.pixels().clone();for(int i=0;i<256;i++)if((pixels[i]>>>24)==0)pixels[i]=tile.pixels()[i];}
            Tile merged=new Tile(tile.dimension(),tile.x(),tile.z(),pixels);map.put(key,merged);uploadHistory.add(merged);Key atlas=new Key(key.dim,key.x>>3,key.z>>3);atlasKeys.add(atlas);changedAtlases.add(atlas);
        }
        dirty=persistDirty=true;invalidate();
        diskJob.whenComplete((v,error)->Minecraft.getInstance().execute(()->{
            if(!Objects.equals(file,target))return;save();
            diskJob.thenRunAsync(()->{try{Files.writeString(marker,"Imported "+tiles.size()+" surface chunks from "+source+"\n");}catch(IOException e){System.err.println("F8Q: import marker: "+e.getMessage());}},IO);
        }));
        System.out.println("F8Q: imported "+tiles.size()+" JourneyMap surface chunks from "+source);
    }
    private static void save(){
        if(!persistDirty||file==null||!diskJob.isDone())return;
        Path target=file;var combined=new LinkedHashMap<Key,Tile>();owners.values().forEach(combined::putAll);persistDirty=false;
        diskJob=java.util.concurrent.CompletableFuture.runAsync(()->{
            try{Files.createDirectories(target.getParent());Path tmp=target.resolveSibling(target.getFileName()+".tmp");
                try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))){out.writeInt(0x46385131);out.writeInt(combined.size());for(var t:combined.values()){out.writeUTF(t.dimension());out.writeInt(t.x());out.writeInt(t.z());for(int pixel:t.pixels())out.writeInt(pixel);}}
                try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
            }catch(IOException e){throw new java.util.concurrent.CompletionException(e);}
        },IO);
        diskJob.whenComplete((v,error)->{if(error!=null&&Objects.equals(file,target))persistDirty=true;});
    }

    private static final class Atlas {
        final NativeImage image=new NativeImage(128,128,true);
        final ResourceLocation id;
        final DynamicTexture texture;
        long maskVersion=-1;
        Atlas(Key key){image.fillRect(0,0,128,128,0);texture=new DynamicTexture(new NativeImage(128,128,true));id=Minecraft.getInstance().getTextureManager().register("f8q_party_map",texture);}
    }
    public static boolean learned(String dimension,int x,int z){Key key=new Key(dimension,x>>4,z>>4);return owners.values().stream().anyMatch(map->map.containsKey(key));}
    public static void retainParty(Set<UUID> members){
        allowed=Set.copyOf(members);
    }
    public static void receive(UUID owner,List<Tile> tiles){
        if(!allowed.contains(owner))return;var map=owners.computeIfAbsent(new UUID(0,0),k->new HashMap<>());
        for(var tile:tiles)if(tile.pixels().length==256){map.put(new Key(tile.dimension(),tile.x(),tile.z()),tile);changedAtlases.add(new Key(tile.dimension(),tile.x()>>3,tile.z()>>3));atlasKeys.add(new Key(tile.dimension(),tile.x()>>3,tile.z()>>3));if(map.size()>131072)map.remove(map.keySet().iterator().next());}
        dirty=true;persistDirty=true;invalidate();
    }
    private static void invalidate(){
        com.velorise.simplemap.client.renderer.FullscreenMapFramebufferRenderer.getInstance().invalidate();
        com.velorise.simplemap.client.renderer.MinimapFramebufferRenderer.getInstance().invalidate();
    }
    public static void clear(){try{diskJob.join();save();diskJob.join();}catch(RuntimeException e){System.err.println("F8Q: map save failed: "+e.getMessage());}atlasKeys.clear();uploadHistory.clear();changedAtlases.clear();file=null;persistDirty=false;owners.clear();allowed=Set.of();uploaded.clear();regions=List.of();session="";regionCursor=chunkCursor=0;for(var a:atlases.values()){Minecraft.getInstance().getTextureManager().release(a.id);a.image.close();}atlases.clear();dirty=false;}
    public static void tick(Consumer<List<Tile>> send){
        Minecraft mc=Minecraft.getInstance();var manager=MapManager.getInstance();if(mc.level==null||mc.player==null||!manager.isViewingLiveDimension())return;
        if(++clock%200==0)save();
        if(clock%10!=0)return;
        for(int batch=0;batch<8&&!uploadHistory.isEmpty();batch++){var history=new ArrayList<Tile>();while(history.size()<8&&!uploadHistory.isEmpty())history.add(uploadHistory.remove());send.accept(history);}

        String current=String.valueOf(manager.getCurrentDimensionDir())+manager.getLiveDimensionResourceId();
        if(!current.equals(session)){session=current;regions=List.of();regionCursor=chunkCursor=0;}
        if(regions.isEmpty()||clock%200==0)regions=manager.getKnownRegionNamesForBook(2048);
        if(regions.isEmpty())return;
        var out=new ArrayList<Tile>();long deadline=System.nanoTime()+2_000_000L;
        for(int probes=0;probes<64&&out.size()<8&&System.nanoTime()<deadline;probes++){
            String name=regions.get(Math.floorMod(regionCursor,regions.size()));String[] parts=name.split("\\.");if(parts.length<4){regionCursor++;continue;}
            int rx,rz;try{rx=Integer.parseInt(parts[1]);rz=Integer.parseInt(parts[2]);}catch(NumberFormatException e){regionCursor++;continue;}
            var region=manager.getRegion(rx,rz,true);int cursor=chunkCursor++;
            if(chunkCursor>=1024){chunkCursor=0;regionCursor++;}
            if(region==null||!region.isLoaded())continue;
            int lx=cursor&31,lz=cursor>>5;if(!region.isChunkSurfaceComplete(lx,lz))continue;
            int cx=rx*32+lx,cz=rz*32+lz;Key key=new Key(manager.getLiveDimensionResourceId(),cx,cz);
            int revision=Objects.hash(region.chunkRevision(lx,lz),MapManager.MapConfig.mapColorProfile,MapManager.MapConfig.blockColourMode);
            if(Objects.equals(uploaded.get(key),revision))continue;
            var snapshot=region.snapshotChunk(lx,lz,null);if(snapshot==null)continue;
            List<String> blocks=region.snapshotBlockPalette(),biomes=region.snapshotBiomePalette();
            var colors=new HashMap<String,Integer>();var policies=new HashMap<String,SurfaceColorizer.BlockTintPolicy>();var textures=MapTextureManager.getInstance();
            for(String block:blocks){colors.put(block,textures.resolveBlockColor(block));policies.put(block,textures.resolveTintPolicy(block));}
            var registry=mc.level.registryAccess().registryOrThrow(Registries.BIOME);
            int[] pixels=SurfaceColorizer.colorizeSampleWindow(snapshot.packedPixels(),snapshot.tints(),null,16,0,16,cx*16,cz*16,1,biomes,blocks,
                index->{if(index<0||index>=biomes.size())return null;return registry.get(ResourceLocation.tryParse(biomes.get(index)));},colors,policies,Set.of(),MapManager.MapConfig.blockColourMode,MapManager.MapConfig.displayFlowers,MapManager.MapConfig.terrainSlopes,MapManager.MapConfig.mapColorProfile,()->true);
            uploaded.put(key,revision);out.add(new Tile(key.dim,cx,cz,pixels));
        }
        if(!out.isEmpty())send.accept(out);
    }
    public static void render(GuiGraphics g,double minX,double maxX,double minZ,double maxZ){
        if(owners.isEmpty()&&!dirty)return;var manager=MapManager.getInstance();String dim=manager.getCurrentDimensionResourceId();
        if(com.velorise.simplemap.client.cave.CaveMode.isActive(Minecraft.getInstance()))return;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        int drawn=0;
        for(Key k:atlasKeys){
            int x=k.x*128,z=k.z*128;if(!k.dim.equals(dim)||x+128<minX||x>maxX||z+128<minZ||z>maxZ)continue;
            Atlas a=atlases.get(k);boolean fresh=a==null;if(fresh){a=new Atlas(k);atlases.put(k,a);}
            if(fresh||changedAtlases.remove(k)){
                for(int cz=0;cz<8;cz++)for(int cx=0;cx<8;cx++){
                    Key tileKey=new Key(k.dim,k.x*8+cx,k.z*8+cz);Tile tile=null;
                    for(var map:owners.values())if(map.containsKey(tileKey)){tile=map.get(tileKey);break;}
                    if(tile!=null)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++)a.image.setPixelRGBA(cx*16+dx,cz*16+dz,tile.pixels()[dz*16+dx]);
                }a.maskVersion=-1;
            }
            var region=manager.getRegion(x>>9,z>>9,false);long revision=region==null?0:region.sourceRevision();
            if(a.maskVersion!=revision){
                // Keep the source image intact. The upload copy hides personally known columns.
                NativeImage masked=new NativeImage(128,128,true);
                for(int dz=0;dz<128;dz++)for(int dx=0;dx<128;dx++)masked.setPixelRGBA(dx,dz,MapBlockData.isEmpty(manager.getPackedBlockData(x+dx,z+dz))?a.image.getPixelRGBA(dx,dz):0);
                a.texture.setPixels(masked);a.texture.upload();a.maskVersion=revision;
            }
            g.pose().pushPose();g.pose().translate(0,0,2);g.blit(a.id,x,z,128,128,0f,0f,128,128,128,128);g.pose().popPose();
            if(++drawn>=2048)break;
        }
    }
}
