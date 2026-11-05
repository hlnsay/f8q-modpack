package com.velorise.simplemap.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;

/** Read-only JourneyMap surface migration. A multiplayer cache needs connection-log proof. */
public final class LegacyMapImport {
    private static final Set<Path> started=Collections.synchronizedSet(new HashSet<>());
    public static void start(Path target){
        var mc=Minecraft.getInstance();if(mc==null||mc.level==null||!started.add(target))return;
        Path marker=target.resolveSibling(target.getFileName()+".journeymap-imported");if(Files.exists(marker))return;
        Path game=mc.gameDirectory.toPath();String address=mc.getCurrentServer()==null?null:mc.getCurrentServer().ip;
        Thread worker=new Thread(()->{
            try{
                Path source=source(game,target,address);if(source==null)return;
                var tiles=readSurface(source);
                if(tiles.isEmpty())return;
                mc.execute(()->SharedExploration.importLegacy(target,tiles,marker,source));
            }catch(Exception e){System.err.println("F8Q: JourneyMap import skipped: "+e.getMessage());}
        },"F8Q-JourneyMap-import");worker.setDaemon(true);worker.start();
    }
    private static String address(String host){String s=host.trim().toLowerCase(Locale.ROOT);return s.contains(":")?s:s+":25565";}
    public static Path source(Path game,Path target,String server) throws IOException {
        Path data=game.resolve("journeymap/data");
        if(server==null){
            Path world=target.getParent().getParent();if(world==null)return null;Path root=data.resolve("sp").resolve(world.getFileName());return Files.isDirectory(root)?root:null;
        }
        Path journal=game.resolve("journeymap/journeymap.log");if(!Files.isRegularFile(journal)||Files.size(journal)>8_000_000)return null;
        String log=Files.readString(journal);String folder=null,when=null;
        Pattern mapping=Pattern.compile("\\[(\\d{2}:\\d{2}:\\d{2})\\].*Mapping started in (.+?)\\. Memory:");
        for(String line:log.split("\\R")){var m=mapping.matcher(line);if(m.find()){
            String path=m.group(2).replace('\\','/');String prefix="/journeymap/data/mp/";int at=path.indexOf(prefix);
            if(at>=0){String suffix=path.substring(at+prefix.length());int slash=suffix.indexOf('/');if(slash>0){folder=suffix.substring(0,slash);when=m.group(1);}}
        }}
        if(folder==null||folder.equals(".")||folder.equals(".."))return null;
        // Associate the last JourneyMap mapping time with the nearest preceding connection.
        int mapped=LocalTime.parse(when).toSecondOfDay(),best=121;String connected=null;
        String date=Files.getLastModifiedTime(journal).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
        Pattern connect=Pattern.compile("(\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?.*Connecting to ([^, ]+), (\\d+)");
        Path logs=game.resolve("logs");if(!Files.isDirectory(logs))return null;
        try(var stream=Files.list(logs)){
            for(Path file:stream.filter(p->p.getFileName().toString().startsWith(date)&&p.toString().endsWith(".gz")).sorted().limit(64).toList()){
                if(Files.size(file)>32_000_000)continue;
                try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)),java.nio.charset.StandardCharsets.UTF_8))){
                    String line;while((line=in.readLine())!=null){var m=connect.matcher(line);if(!m.find())continue;int delta=mapped-LocalTime.parse(m.group(1)).toSecondOfDay();if(delta>=0&&delta<best){best=delta;connected=address(m.group(2)+":"+m.group(3));}}
                }
            }
        }
        if(connected==null||!connected.equals(address(server)))return null;
        Path root=data.resolve("mp").resolve(folder).normalize();return root.startsWith(data.resolve("mp"))&&Files.isDirectory(root)?root:null;
    }
    public static List<SharedExploration.Tile> readSurface(Path root) throws IOException {
        var result=new ArrayList<SharedExploration.Tile>();
        for(String dimension:List.of("overworld","the_nether","the_end")){
            Path day=root.resolve(dimension).resolve("day");if(!Files.isDirectory(day))continue;
            String dim="minecraft:"+dimension;
            try(var files=Files.list(day)){
                for(Path file:files.filter(p->p.getFileName().toString().matches("-?\\d+,-?\\d+\\.png")).sorted().limit(2048).toList()){
                    BufferedImage image=ImageIO.read(file.toFile());if(image==null||image.getWidth()!=512||image.getHeight()!=512)continue;
                    String[] xy=file.getFileName().toString().replace(".png","").split(",");int rx=Integer.parseInt(xy[0]),rz=Integer.parseInt(xy[1]);
                    if(Math.abs((long)rx)>58000||Math.abs((long)rz)>58000)continue;
                    int[] region=image.getRGB(0,0,512,512,null,0,512);
                    for(int cz=0;cz<32;cz++)for(int cx=0;cx<32;cx++){
                        int[] pixels=new int[256];boolean known=false;
                        for(int z=0;z<16;z++)for(int x=0;x<16;x++){int argb=region[(cz*16+z)*512+cx*16+x];pixels[z*16+x]=(argb&0xff00ff00)|((argb&255)<<16)|((argb>>>16)&255);known|=(argb>>>24)!=0;}
                        if(known){result.add(new SharedExploration.Tile(dim,rx*32+cx,rz*32+cz,pixels));if(result.size()>131072)throw new IOException("Legacy map exceeds 131,072 known chunks; source remains untouched");}
                    }
                }
            }
        }return result;
    }
}
