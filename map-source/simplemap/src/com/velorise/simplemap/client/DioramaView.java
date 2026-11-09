package com.velorise.simplemap.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import dev.diorama.*;
import dev.diorama.bake.BlockLooks;
import dev.diorama.data.*;
import dev.diorama.data.ChunkSnapshot;
import dev.diorama.render.MapLevel;
import dev.diorama.render.MapViewport;
import dev.diorama.render.gpu.GpuTileBaker;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Original Diorama voxel renderer embedded in SimpleMap. SimpleMap remains the owner of exploration and UI. */
public final class DioramaView {
 private static final DioramaView INSTANCE=new DioramaView();
 private static boolean overlays;private boolean presentedFullscreen;
 private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"F8Q-diorama-read");t.setDaemon(true);t.setPriority(2);return t;});
 private final Set<Long> changed=ConcurrentHashMap.newKeySet();
 private long changedAt,liveAt;
 private final Set<Long> known=ConcurrentHashMap.newKeySet(),scheduled=ConcurrentHashMap.newKeySet();
 private final ArrayBlockingQueue<ChunkSnapshot> ready=new ArrayBlockingQueue<>(24);
 private final AtomicBoolean reading=new AtomicBoolean();
 private MapLevel map;private MapViewport viewport;private ClientLevel level;private Path dimension;
 private DioramaChunkReader reader;private volatile long generation;
 private final Queue<Long> missing=new ConcurrentLinkedQueue<>();
 private final Map<Long,Long> fetches=new ConcurrentHashMap<>();
 private long fetchAt;
 private double centerX,centerZ;
 private Facing fullFacing=Facing.NORTH;private boolean miniMode;private MapViewport miniViewport=new MapViewport();
 private int miniX,miniY,miniSize;private double miniCX,miniCZ,miniRef,miniZoom,miniAngle;private boolean miniValid;private long requestAt;private volatile boolean knownLoaded;
 public long terrainCompositions,meshBuilds;
 public static DioramaView get(){return INSTANCE;}
 public static boolean active(){return INSTANCE.presentedFullscreen;}
 public static boolean composing(){return false;}
 public static void beginOverlays(){overlays=active();}
 public static void endOverlays(){overlays=false;}
 public static boolean overlaysActive(){return overlays;}
 public static double markerZ(double x,double z){return overlays?INSTANCE.projectZ(x,z):z;}
 public static double markerX(double x,double z){return overlays?INSTANCE.projectX(x,z):x;}
 public static void point(PoseStack pose,double x,double z,double layer){pose.translate(markerX(x,z),markerZ(x,z),layer);}
 public void view(double x,double z){centerX=x;centerZ=z;}
 public double height(double x,double z){
  var m=MapManager.getInstance();long packed=m.getPackedBlockData((int)Math.floor(x),(int)Math.floor(z));
  if(!com.velorise.simplemap.client.color.MapBlockData.isEmpty(packed))return com.velorise.simplemap.client.color.MapBlockData.topY(packed)+1;
  if(map!=null){var s=map.worldChunk((int)Math.floor(x)>>4,(int)Math.floor(z)>>4);if(s!=null)return s.surface((int)Math.floor(x)&15,(int)Math.floor(z)&15)+1;}
  return 64;
 }
 public double projectX(double x,double z){return centerX+Projection.u(x,z)-Projection.u(centerX,centerZ);}
 public double projectZ(double x,double z){return centerZ+Projection.v(x,height(x,z),z)-Projection.v(centerX,64,centerZ);}
 public record WorldPoint(double x,double z){}
 public WorldPoint pickWorld(double px,double pz){double u=Projection.u(centerX,centerZ)+px-centerX,v=Projection.v(centerX,64,centerZ)+pz-centerZ;Facing f=Projection.facing();double rotatedZ=Projection.zAt(v,64),x=0,z=0;
  for(int i=0;i<8;i++){x=f.worldX(u,rotatedZ);z=f.worldZ(u,rotatedZ);double next=Projection.zAt(v,height(x,z));if(Math.abs(next-rotatedZ)<.01)break;rotatedZ=next;}return new WorldPoint(x,z);}
 public static WorldPoint worldDelta(double u,double v){Facing f=Projection.facing();return new WorldPoint(f.worldX(u,v/Projection.sin()),f.worldZ(u,v/Projection.sin()));}
 private void orient(Facing facing){if(Projection.facing()!=facing){if(Boolean.getBoolean("f8q.engineProbe"))System.out.println("F8Q_ORIENT "+Projection.facing()+" -> "+facing+" mini="+miniMode+" screen="+Minecraft.getInstance().screen);Projection.setFacing(facing);if(map!=null)map.colorsChanged();if(viewport!=null)viewport.close();viewport=new MapViewport();if(miniViewport!=null)miniViewport.close();miniViewport=new MapViewport();}}
 public void turn(int steps){fullFacing=fullFacing.next(steps);orient(fullFacing);}
 public String direction(){return fullFacing.id();}
 public record Pick(double z,double height){}
 public Pick pick(double x,double projected){var p=pickWorld(x,projected);return new Pick(p.z(),height(p.x(),p.z()));}
 public double pickZ(double x,double projected){return pick(x,projected).z();}
 private static Path nativeFolder()throws Exception{var f=MapManager.class.getDeclaredField("currentDimensionDir");f.setAccessible(true);File file=(File)f.get(MapManager.getInstance());return file==null?null:file.toPath();}
 private void attach(ClientLevel current,Path folder)throws Exception{
  presentedFullscreen=false;if(map!=null)map.close();if(viewport!=null)viewport.close();generation++;known.clear();changed.clear();scheduled.clear();ready.clear();missing.clear();fetches.clear();knownLoaded=false;
  level=current;dimension=folder;long stamp=generation;MapStyle.set(MapStyle.DEFAULT);Settings.get().mapSavedChunks=false;
  map=new MapLevel(current.getMinBuildHeight(),current.getMaxBuildHeight(),current.dimensionType().hasCeiling()?current.getMinBuildHeight()+current.dimensionType().logicalHeight():Integer.MIN_VALUE,folder.resolve("diorama-engine-v1/tiles"));
  viewport=new MapViewport();Path rawFolder=folder.resolve("diorama-engine-v1/region");
  var integrated=Minecraft.getInstance().getSingleplayerServer();if(integrated!=null)rawFolder=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(current.dimension(),integrated.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("region");
  reader=new DioramaChunkReader(current,rawFolder);
  if(BlockLooks.get()==null)BlockLooks.build();
  io.submit(()->{try(var files=Files.list(folder)){
   var list=files.filter(f->f.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.smdat")).toList();
   for(Path f:list){if(stamp!=generation)return;String[] n=f.getFileName().toString().split("\\.");int rx=Integer.parseInt(n[1]),rz=Integer.parseInt(n[2]);var data=RegionDataStore.read(f.toFile());long[] pixels=data.pixels();
    for(int cz=0;cz<32;cz++)for(int cx=0;cx<32;cx++){boolean present=false;for(int z=0;z<16&&!present;z++)for(int x=0;x<16;x++)if(!com.velorise.simplemap.client.color.MapBlockData.isEmpty(pixels[(cz*16+z)*512+cx*16+x])){present=true;break;}if(present&&stamp==generation)known.add(ChunkSnapshot.key(rx*32+cx,rz*32+cz));}
   }
  }catch(Exception e){System.err.println("F8Q native exploration read: "+e);}finally{if(stamp==generation)knownLoaded=true;}});
 }
 private void request(double cx,double cz,int w,int h,float scale){
  long now=System.nanoTime();if(now-requestAt<250_000_000L||reading.get()||!knownLoaded)return;requestAt=now;
  double halfX=w/(2*scale)+48,halfZ=h/(2*scale)/Projection.sin()+256;
  if((Projection.facing().turns&1)!=0){double swap=halfX;halfX=halfZ;halfZ=swap;}
  List<Long> want=new ArrayList<>();
  for(long key:known){int x=(int)(key>>32),z=(int)key;if(Math.abs(x*16+8-cx)>halfX||Math.abs(z*16+8-cz)>halfZ||map.worldChunk(x,z)!=null||scheduled.contains(key))continue;want.add(key);}
  want.sort(Comparator.comparingDouble(k->Math.hypot(((int)(k>>32))*16+8-cx,((int)(long)k)*16+8-cz)));
  if(want.isEmpty())return;reading.set(true);long stamp=generation;var source=reader;var world=level;
  io.submit(()->{try{int count=0;for(long key:want){if(stamp!=generation||count>=32||ready.remainingCapacity()==0)break;int x=(int)(key>>32),z=(int)key;
    var saved=source.read(x,z);if(saved==null){long last=fetches.getOrDefault(key,0L);if(System.currentTimeMillis()-last>20_000){fetches.put(key,System.currentTimeMillis());missing.add(key);}continue;}var around=new SavedChunk[9];for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)around[(dx+1)*3+dz+1]=dx==0&&dz==0?saved:source.read(x+dx,z+dz);
    var snapshot=ChunkSnapshot.fromSave(saved,around,world);if(stamp==generation&&ready.offer(snapshot)){scheduled.add(key);count++;}
   }}catch(Exception e){System.err.println("F8Q diorama geometry read: "+e);}finally{reading.set(false);}});
 }
 public boolean render(GuiGraphics graphics,int x,int y,int w,int h,double cx,double cz,float stable,float zoom,float partial){
  var mc=Minecraft.getInstance();if(mc.level==null||!MapManager.getInstance().isViewingLiveDimension()){if(!miniMode)presentedFullscreen=false;return false;}
  if(!miniMode&&mc.getSingleplayerServer()==null&&(mc.getConnection()==null||!((net.neoforged.neoforge.common.extensions.ICommonPacketListener)mc.getConnection()).hasChannel(com.velorise.simplemap.network.DioramaGeometry.Request.TYPE))){presentedFullscreen=false;return false;}
  try{Path folder=nativeFolder();if(folder==null)return false;if(level!=mc.level||!folder.equals(dimension))attach(mc.level,folder);view(cx,cz);orient(miniMode?Facing.NORTH:fullFacing);
   if(BlockLooks.isStale()&&mc.getOverlay()==null){BlockLooks.build();map.colorsChanged();}
   int applied=0;long now=System.nanoTime();
   if(now-changedAt>250_000_000L&&!changed.isEmpty()){changedAt=now;var it=changed.iterator();long key=it.next();it.remove();int a=(int)(key>>32),b=(int)key;var c=mc.level.getChunkSource().getChunk(a,b,ChunkStatus.FULL,false);if(c!=null&&map.worldChunk(a,b)!=null){map.put(ChunkSnapshot.take(c,mc.level),null,mc.player.chunkPosition().x,mc.player.chunkPosition().z);applied++;}}
   ChunkSnapshot s;while(applied<3&&(s=ready.poll())!=null){scheduled.remove(s.key());map.put(s,null,((int)Math.floor(cx))>>4,((int)Math.floor(cz))>>4);applied++;meshBuilds++;}
   // The live view only reads chunks already resident on the client; it never requests world generation.
   int pcx=mc.player.chunkPosition().x,pcz=mc.player.chunkPosition().z;
   if(knownLoaded&&now-liveAt>50_000_000L){
    liveAt=now;boolean taken=false;
    for(int radius=0;radius<=8&&!taken;radius++)for(int dx=-radius;dx<=radius&&!taken;dx++)for(int dz=-radius;dz<=radius&&!taken;dz++){
     if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
     int a=pcx+dx,b=pcz+dz;long key=ChunkSnapshot.key(a,b);if(map.worldChunk(a,b)!=null)continue;
     var chunk=mc.level.getChunkSource().getChunk(a,b,ChunkStatus.FULL,false);if(chunk==null)continue;
     known.add(key);map.put(ChunkSnapshot.take(chunk,mc.level),null,((int)Math.floor(cx))>>4,((int)Math.floor(cz))>>4);taken=true;
    }
   }

   request(cx,cz,w,h,zoom);fetchMissing();GpuTileBaker.frame();map.update();
   float night=Math.max(0,Math.min(1,(1-MapRenderer.getInstance().minimapBrightnessBucket(mc,partial,false)/24f)/.72f));viewport.night(night);
   var view=new MapViewport.View(x,y,w,h,Projection.u(cx,cz),Projection.v(cx,miniMode?height(cx,cz):64,cz),zoom,Integer.MIN_VALUE,miniMode&&MapManager.MapConfig.minimapCircle,false);
   if(miniMode){
    miniX=x;miniY=y;miniSize=w;miniCX=cx;miniCZ=cz;miniRef=height(cx,cz);miniZoom=zoom;miniValid=true;
    miniViewport.night(night);graphics.pose().pushPose();graphics.pose().translate(x+w*.5,y+h*.5,0);graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)miniAngle));graphics.pose().translate(-x-w*.5,-y-h*.5,0);
    try{miniViewport.draw(graphics,map,view);}finally{graphics.pose().popPose();}
   }else viewport.draw(graphics,map,view);terrainCompositions++;
   if(!miniMode&&(!knownLoaded||map.bakingCount()>0||reading.get()||!ready.isEmpty()))graphics.drawString(mc.font,"Подготовка объёмной карты…",x+50,y+h-18,0xFFF0E8D0,true);
   if(!miniMode)presentedFullscreen=true;return true;
  }catch(Throwable e){if(!miniMode)presentedFullscreen=false;e.printStackTrace();return false;}
 }
 public static boolean background(GuiGraphics g,int w,int h,double cx,double cz,float scale,double mx,double mz,float partial){
  boolean ok=get().render(g,0,0,w,h,cx,cz,scale,scale,partial);if(ok){beginOverlays();try{MapRenderer.getInstance().drawFullscreenOverlays(g,0,0,w,h,cx,cz,scale,MapManager.getInstance().isViewingLiveDimension(),mx,mz,partial);}finally{endOverlays();}}return ok;
 }
 public boolean renderMini(GuiGraphics g,int x,int y,int size,double cx,double cz,float zoom,boolean rotate,float partial){
  var mc=Minecraft.getInstance();
  // A HUD layer can be dispatched underneath an opaque map screen. Do not let that hidden
  // minimap switch the shared original projection back to north between fullscreen frames.
  if(mc.screen!=null&&(mc.screen instanceof com.velorise.simplemap.client.gui.MapScreen||mc.screen.getClass().getName().startsWith("com.velorise.simplemap.client.gui.MapScreen$")||mc.screen instanceof com.velorise.simplemap.client.gui.PlaceEditorScreen)){miniValid=false;return true;}
  miniMode=true;miniValid=false;miniAngle=rotate&&mc.player!=null?Math.toRadians(-net.minecraft.util.Mth.rotLerp(partial,mc.player.yRotO,mc.player.getYRot())-180):0;
  try{return render(g,x,y,size,size,cx,cz,zoom,zoom,partial);}finally{miniMode=false;}
 }
 public com.velorise.simplemap.client.renderer.MinimapFramebufferRenderer.HudPoint miniPoint(double x,double z){if(!miniValid)return null;
  double dx=(x-miniCX)*miniZoom,dz=(Projection.v(x,height(x,z),z)-Projection.v(miniCX,miniRef,miniCZ))*miniZoom;
  double c=Math.cos(miniAngle),s=Math.sin(miniAngle);return new com.velorise.simplemap.client.renderer.MinimapFramebufferRenderer.HudPoint((float)(miniX+miniSize*.5+dx*c-dz*s),(float)(miniY+miniSize*.5+dx*s+dz*c));
 }
 private void fetchMissing(){
  var mc=Minecraft.getInstance();if(mc.getConnection()==null||!((net.neoforged.neoforge.common.extensions.ICommonPacketListener)mc.getConnection()).hasChannel(com.velorise.simplemap.network.DioramaGeometry.Request.TYPE))return;
  long now=System.nanoTime();if(now-fetchAt<250_000_000L||missing.isEmpty())return;fetchAt=now;
  long[] batch=new long[com.velorise.simplemap.network.DioramaGeometry.BATCH];int n=0;Long key;
  while(n<batch.length&&(key=missing.poll())!=null)if(known.contains(key))batch[n++]=key;
  if(n>0)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.velorise.simplemap.network.DioramaGeometry.Request(generation,level.dimension().location().toString(),java.util.Arrays.copyOf(batch,n)));
 }
 public void acceptGeometry(com.velorise.simplemap.network.DioramaGeometry.Data data){
  if(level==null||data.session()!=generation||!level.dimension().location().toString().equals(data.dimension()))return;
  long stamp=generation;var root=reader.root;
  io.submit(()->{try{
   var tag=net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(data.bytes()),net.minecraft.nbt.NbtAccounter.create(32*1024*1024));
   var list=tag.getList("chunks",10);if(list.size()>com.velorise.simplemap.network.DioramaGeometry.BATCH)return;
   for(int i=0;i<list.size();i++){var chunk=list.getCompound(i);int x=chunk.getInt("xPos"),z=chunk.getInt("zPos");long key=ChunkSnapshot.key(x,z);if(stamp!=generation||!known.contains(key))continue;
    Files.createDirectories(root);Path target=root.resolve(x+"."+z+".nbt"),temp=root.resolve(x+"."+z+".nbt.tmp");
    net.minecraft.nbt.NbtIo.writeCompressed(chunk,temp);if(stamp==generation){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);fetches.remove(key);}else Files.deleteIfExists(temp);
   }
  }catch(Exception e){System.err.println("F8Q geometry recovery: "+e);}});
 }
 public static void changed(net.minecraft.world.level.chunk.LevelChunk chunk){if(INSTANCE.map!=null&&chunk.getLevel()==INSTANCE.level)INSTANCE.changed.add(ChunkSnapshot.key(chunk.getPos().x,chunk.getPos().z));}
 public void release(){generation++;presentedFullscreen=false;miniValid=false;if(map!=null)map.close();if(viewport!=null)viewport.close();if(miniViewport!=null)miniViewport.close();map=null;viewport=null;level=null;reader=null;dimension=null;known.clear();ready.clear();scheduled.clear();missing.clear();fetches.clear();changed.clear();}
}

