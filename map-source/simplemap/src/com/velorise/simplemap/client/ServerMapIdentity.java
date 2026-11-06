package com.velorise.simplemap.client;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import com.google.gson.*;

/** Explicit alias for this pack's verified endpoint. Never searches for or imports another server's map. */
public final class ServerMapIdentity {
    public static String canonicalAddress(String raw,int connectedPort){
        String value=raw==null?"unknown":raw.trim().toLowerCase(Locale.ROOT);
        if(value.equals("f8quit.exaroton.me")&&connectedPort==30096)return "f8quit.exaroton.me:30096";
        return value;
    }
    public static boolean recover(Path savesRoot,String sourceName,String targetName)throws IOException {
        Path root=savesRoot.toAbsolutePath().normalize(),source=child(root,sourceName),target=child(root,targetName);
        if(source.equals(target)||!Files.isDirectory(source))return false;
        Path marker=target.resolve(".native-address-alias-v22");if(Files.isRegularFile(marker))return false;
        if(Files.isSymbolicLink(source)||Files.isSymbolicLink(target))throw new IOException("Map aliases may not be symbolic links");
        Files.createDirectories(target);
        Path backup=target.resolve(".address-recovery-v22");Files.createDirectories(backup);
        boolean changed=false;
        try(var dirs=Files.list(source)){
            for(Path dimension:dirs.filter(Files::isDirectory).toList()){
                if(Files.isSymbolicLink(dimension)||!Files.isRegularFile(dimension.resolve(".dimension_id")))continue;
                String name=dimension.getFileName().toString();if(name.startsWith(".")||name.equals("caves")||name.equals("light_cache"))continue;
                Path out=child(target,name);if(Files.isSymbolicLink(out))throw new IOException("Symbolic dimension directory");Files.createDirectories(out);
                String dimensionId=Files.readString(dimension.resolve(".dimension_id")).strip();
                if(Files.exists(out.resolve(".dimension_id"))&&!Files.readString(out.resolve(".dimension_id")).strip().equals(dimensionId))throw new IOException("Different dimension identity");
                Path before=backup.resolve(name);Files.createDirectories(before);boolean dimensionChanged=false;
                try(var regions=Files.list(dimension)){
                    for(Path file:regions.filter(p->p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.smdat")).toList()){
                        if(Files.isSymbolicLink(file))throw new IOException("Symbolic native region");
                        Path dest=out.resolve(file.getFileName());
                        if(Files.isSymbolicLink(dest))throw new IOException("Symbolic target region");
                        // Decode with the native format, merge its block/biome palettes and observed coverage.
                        // No JourneyMap images, terrain.dat overlays, guessed chunks or foreign LOD tiles are read.
                        var incoming=RegionDataStore.read(file.toFile());
                        if(Files.exists(dest)){
                            var existing=RegionDataStore.read(dest.toFile());
                            Path saved=before.resolve(file.getFileName());if(!Files.exists(saved))Files.copy(dest,saved);
                            RegionDataStore.writeAtomic(dest.toFile(),RegionDataStore.merge(existing,incoming));
                        }else RegionDataStore.writeAtomic(dest.toFile(),incoming);
                        dimensionChanged=true;changed=true;
                    }
                }
                if(dimensionChanged){
                    for(String metadata:List.of(".dimension_id",".dimension_profile"))if(!Files.exists(out.resolve(metadata))&&Files.exists(dimension.resolve(metadata)))Files.copy(dimension.resolve(metadata),out.resolve(metadata));
                    // Regenerate derived images/containers from the merged native surface. Keep originals as backups.
                    for(String derived:List.of(".lod_v2","containers-v2")){
                        Path path=out.resolve(derived),saved=before.resolve(derived);
                        if(Files.exists(path)&&!Files.exists(saved))Files.move(path,saved);
                    }
                }
            }
        }
        try(var journals=Files.list(source)){
            for(Path file:journals.filter(p->p.getFileName().toString().matches("f8q-places-[0-9a-fA-F-]{36}\\.json")).toList()){
                Path dest=target.resolve(file.getFileName());
                JsonObject incoming=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(Files.exists(dest)){
                    JsonObject existing=JsonParser.parseString(Files.readString(dest)).getAsJsonObject();
                    Path saved=backup.resolve(file.getFileName());if(!Files.exists(saved))Files.copy(dest,saved);
                    // Keep the newest authoritative place snapshot (including deletions); retain personal styling.
                    JsonObject newest=Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(dest))>=0?incoming:existing;
                    JsonObject styles=new JsonObject();
                    JsonObject older=newest==incoming?existing:incoming;
                    if(older.has("styles"))older.getAsJsonObject("styles").entrySet().forEach(e->styles.add(e.getKey(),e.getValue()));
                    if(newest.has("styles"))newest.getAsJsonObject("styles").entrySet().forEach(e->styles.add(e.getKey(),e.getValue()));
                    newest.add("styles",styles);incoming=newest;
                }
                atomic(dest,new GsonBuilder().setPrettyPrinting().create().toJson(incoming));
            }
        }
        // Native manual waypoints are separate from the POI journal; preserve the newest complete snapshot.
        Path waypoints=source.resolve("waypoints.json"),dest=target.resolve("waypoints.json");
        if(Files.isRegularFile(waypoints)&&(!Files.exists(dest)||Files.getLastModifiedTime(waypoints).compareTo(Files.getLastModifiedTime(dest))>0)){
            if(Files.exists(dest)&&!Files.exists(backup.resolve("waypoints.json")))Files.copy(dest,backup.resolve("waypoints.json"));
            atomic(dest,Files.readString(waypoints));
        }
        atomic(marker,"Native alias recovered from "+sourceName+"\n");return changed;
    }
    private static Path child(Path root,String name)throws IOException {
        Path path=root.resolve(name).normalize();if(!path.getParent().equals(root)||name.equals(".")||name.equals(".."))throw new IOException("Unsafe map path");return path;
    }
    private static void atomic(Path dest,String text)throws IOException {
        Path temp=Files.createTempFile(dest.getParent(),"address-recovery-",".tmp");
        Files.writeString(temp,text);Files.move(temp,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
