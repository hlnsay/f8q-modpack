import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.velorise.simplemap.client.LegacyMapImport;
import com.velorise.simplemap.client.SharedExploration;
public final class LegacyImportCli {
 public static void main(String[] args)throws Exception{
  Path game=Path.of(args[0]),target=Path.of(args[1]);Path source=LegacyMapImport.source(game,target,args[2]);if(source==null)throw new IllegalStateException("No verified matching JourneyMap cache");
  var records=new LinkedHashMap<String,SharedExploration.Tile>();
  if(Files.exists(target))try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(target))))){if(in.readInt()!=0x46385131)throw new IOException("format");int count=in.readInt();for(int i=0;i<count;i++){String d=in.readUTF();int x=in.readInt(),z=in.readInt();int[] pixels=new int[256];for(int n=0;n<256;n++)pixels[n]=in.readInt();records.put(d+":"+x+":"+z,new SharedExploration.Tile(d,x,z,pixels));}}
  var imported=LegacyMapImport.readSurface(source);for(var tile:imported){String key=tile.dimension()+":"+tile.x()+":"+tile.z();var prior=records.get(key);if(prior==null)records.put(key,tile);else{int[] pixels=prior.pixels().clone();for(int n=0;n<256;n++)if((pixels[n]>>>24)==0)pixels[n]=tile.pixels()[n];records.put(key,new SharedExploration.Tile(tile.dimension(),tile.x(),tile.z(),pixels));}}
  if(records.size()>131072)throw new IOException("capacity");Files.createDirectories(target.getParent());if(Files.exists(target))Files.copy(target,target.resolveSibling(target.getFileName()+".before-journeymap"),StandardCopyOption.REPLACE_EXISTING);
  Path tmp=target.resolveSibling(target.getFileName()+".import.tmp");try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))){out.writeInt(0x46385131);out.writeInt(records.size());for(var tile:records.values()){out.writeUTF(tile.dimension());out.writeInt(tile.x());out.writeInt(tile.z());for(int color:tile.pixels())out.writeInt(color);}}
  Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(target))))){if(in.readInt()!=0x46385131||in.readInt()!=records.size())throw new IOException("readback header");for(var tile:records.values()){if(!in.readUTF().equals(tile.dimension())||in.readInt()!=tile.x()||in.readInt()!=tile.z())throw new IOException("coordinate mismatch");for(int color:tile.pixels())if(in.readInt()!=color)throw new IOException("pixel mismatch");}if(in.read()!=-1)throw new IOException("trailing bytes");}
  Files.writeString(target.resolveSibling(target.getFileName()+".journeymap-imported"),"Imported "+imported.size()+" chunks from "+source+"\n");
  System.out.println("Verified JourneyMap source: "+source);System.out.println("Imported surface chunks: "+imported.size());System.out.println("Written and pixel-verified chunks: "+records.size()+"; compressed bytes: "+Files.size(target));
 }
}