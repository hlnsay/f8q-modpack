package com.velorise.simplemap.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import com.velorise.simplemap.client.waypoint.PinNavigation;
import net.minecraft.client.Minecraft;

/** World/account-scoped journal; personal edits and routes never enter party payloads. */
public final class LocalPlaces {
    public record Style(String name,int color) {}
    public static final String[] COLOR_IDS={"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
    public static final int[] COLORS={0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
    public static final class State {
        public int version=1;
        public Map<String,LinkedHashMap<String,TeamMarks.Location>> places=new LinkedHashMap<>();
        public Map<String,Style> styles=new HashMap<>();
        public String routeId="",routeDimension="";
        public TeamMarks.Location routePoint;
        public String routeIcon="";
        public int routeKind=4;
    }
    public static final class Journal {
        private static final Gson JSON=new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(TeamMarks.Location.class,(com.google.gson.JsonDeserializer<TeamMarks.Location>)(json,type,context)->{
                var o=json.getAsJsonObject();return new TeamMarks.Location(o.get("id").getAsString(),o.get("structure").getAsString(),o.get("x").getAsDouble(),o.get("y").getAsDouble(),o.get("z").getAsDouble(),o.get("kind").getAsInt(),o.get("open").getAsBoolean(),o.has("label")?o.get("label").getAsString():"");
            })
            .registerTypeAdapter(Style.class,(com.google.gson.JsonDeserializer<Style>)(json,type,context)->{
                var o=json.getAsJsonObject();return new Style(o.get("name").getAsString(),o.get("color").getAsInt());
            }).create();
        public State state=new State();
        private final Path file;
        private boolean dirty;
        public Journal(Path file){this.file=file;if(file!=null&&Files.exists(file))try{
            State loaded=JSON.fromJson(Files.readString(file),State.class);
            if(loaded!=null&&loaded.places!=null&&loaded.styles!=null)state=loaded;
        }catch(Exception e){System.err.println("F8Q: could not read place journal: "+e.getMessage());}}
        public void receive(String dimension,List<TeamMarks.Location> changes){
            var points=state.places.computeIfAbsent(dimension,k->new LinkedHashMap<>());
            for(var p:changes){if(p.kind()<0)points.remove(p.id());else points.put(p.id(),p);}dirty=true;
        }
        public void style(String id,String name,int color){state.styles.put(id,new Style(name.strip(),color&0xffffff));dirty=true;}
        public void changed(){dirty=true;}
        public void flush(){if(!dirty||file==null)return;try{
            Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(tmp,JSON.toJson(state));
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;
        }catch(Exception e){System.err.println("F8Q: could not save place journal: "+e.getMessage());}}
    }
    private static Journal journal=new Journal(null);
    private static Path bound,preferences;
    private static boolean resumed;
    private static int ticks;
    public static boolean showPois=true;
    public static float markerScale=1.5f,routeScale=1.5f;
    public static boolean isBound(){return bound!=null;}
    public static boolean bind(Path file){if(Objects.equals(bound,file))return false;journal.flush();bound=file;journal=new Journal(file);resumed=false;return true;}
    public static Journal journal(){return journal;}
    public static void close(){journal.flush();bound=null;journal=new Journal(null);resumed=false;}
    public static String name(String id,String fallback){Style s=journal.state.styles.get(id);return s==null||s.name()==null||s.name().isBlank()?fallback:s.name();}
    public static int color(String id){Style s=journal.state.styles.get(id);return s==null?0xffffff:s.color();}
    public static void edit(String id,String name,int color){journal.style(id,name,color);journal.flush();TeamMarks.refreshStyles();}
    public static Integer parseColor(String input){
        if(input==null)return null;String s=input.strip().toLowerCase(Locale.ROOT).replace("minecraft:","");
        for(int i=0;i<COLOR_IDS.length;i++)if(COLOR_IDS[i].equals(s))return COLORS[i];
        try{if(s.startsWith("#")&&s.length()==7)return Integer.parseInt(s.substring(1),16);if(s.startsWith("§")||s.startsWith("&"))s=s.substring(1);
            int n=s.length()==1&&"abcdef".contains(s)?Integer.parseInt(s,16):Integer.parseInt(s);return n>=0&&n<16?COLORS[n]:null;
        }catch(NumberFormatException e){return null;}
    }
    public static boolean routing(){return journal.state.routeId!=null&&!journal.state.routeId.isEmpty();}
    public static TeamMarks.Location routeLocation(){var points=journal.state.places.get(journal.state.routeDimension);return points!=null&&points.containsKey(journal.state.routeId)?points.get(journal.state.routeId):journal.state.routePoint;}
    public static String routeDimension(){return journal.state.routeDimension;}
    public static boolean following(String id){return routing()&&journal.state.routeId.equals(id);}
    public static void follow(TeamMarks.Location place,String dimension){follow(place,dimension,place.id());}
    public static void follow(TeamMarks.Location place,String dimension,String routeKey){follow(place,dimension,routeKey,"poi:"+place.kind(),place.kind());}
    public static void follow(TeamMarks.Location place,String dimension,String routeKey,String icon,int kind){
        journal.state.routeIcon=icon;journal.state.routeKind=kind;
        journal.state.routePoint=place;journal.state.routeId=routeKey;journal.state.routeDimension=dimension;resumed=true;
        PinNavigation.activate(place.x(),place.z());MapManager.getInstance().savePin();journal.changed();journal.flush();
    }
    public static void clearRoute(boolean clearPin){journal.state.routeId="";journal.state.routeDimension="";journal.state.routePoint=null;journal.changed();journal.flush();if(clearPin){PinNavigation.clear();MapManager.getInstance().savePin();}}
    public static void tick(){
        if(++ticks%20==0)journal.flush();
        if(!resumed&&routing()&&Minecraft.getInstance().player!=null&&routeDimension().equals(MapManager.getInstance().getLiveDimensionResourceId())){
            var place=routeLocation();if(place!=null){PinNavigation.activate(place.x(),place.z());MapManager.getInstance().savePin();}resumed=true;
        }
    }
    public static void preferences(Path path){
        if(Objects.equals(preferences,path))return;preferences=path;Properties p=new Properties();
        if(Files.exists(path))try(var in=Files.newBufferedReader(path)){p.load(in);}catch(Exception ignored){}
        showPois=!"false".equals(p.getProperty("showPois"));
        try{markerScale=clamp(Float.parseFloat(p.getProperty("markerScale","1.5")));routeScale=clamp(Float.parseFloat(p.getProperty("routeScale","1.5")));}catch(NumberFormatException ignored){}
    }
    public static float clamp(float scale){return Float.isFinite(scale)?Math.max(.75f,Math.min(3,scale)):1.5f;}
    public static void savePreferences(){if(preferences==null)return;Properties p=new Properties();p.setProperty("showPois",Boolean.toString(showPois));p.setProperty("markerScale",Float.toString(markerScale));p.setProperty("routeScale",Float.toString(routeScale));try{Files.createDirectories(preferences.getParent());try(var out=Files.newBufferedWriter(preferences)){p.store(out,"F8Q map interface");}}catch(Exception ignored){}}
}
