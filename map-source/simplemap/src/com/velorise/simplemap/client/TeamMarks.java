package com.velorise.simplemap.client;

import com.velorise.simplemap.client.MapManager.MapConfig;
import com.velorise.simplemap.client.waypoint.PinNavigation.WaypointManager;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * F8Q: what the party shares with the map. The party mod fills this in; the map only draws it.
 * Marks arrive already coloured and named, and three switches say what the player wants to see.
 */
public final class TeamMarks {
    public record Mark(String owner, int color, String label, double x, double y, double z, String dimension, boolean pin, String iconItem, int kind) {
        public Mark(String owner,int color,String label,double x,double y,double z,String dimension,boolean pin){this(owner,color,label,x,y,z,dimension,pin,"",13);}
        public String title() {
            return owner.isEmpty() ? label : "[" + owner + "] " + label;
        }
    }

    public record Mate(String name, net.minecraft.resources.ResourceLocation arrow, double x, double z, float yaw, String dimension) {
    }

    private static volatile List<Mate> mates = List.of();
    public static boolean showPins = true;
    public static boolean showWaypoints = true;
    public static boolean showInWorld = true;
    private static volatile List<Mark> marks = List.of();
    private static boolean loaded;
    private static final net.minecraft.resources.ResourceLocation RED_X = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simplemap", "textures/gui/red_x.png");
    public static final net.minecraft.resources.ResourceLocation SKULL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simplemap", "textures/gui/skull.png");
    public static final net.minecraft.resources.ResourceLocation UNKNOWN = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simplemap", "textures/gui/unknown.png");
    private static volatile List<double[]> unknown = List.of();
    private static volatile String unknownDimension = "";

    private TeamMarks() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("simplemap/f8q-team.properties");
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (Files.exists(path)) {
            Properties values = new Properties();
            try (Reader reader = Files.newBufferedReader(path)) {
                values.load(reader);
            } catch (IOException e) {
                return;
            }
            showPins = !"false".equals(values.getProperty("pins"));
            showWaypoints = !"false".equals(values.getProperty("waypoints"));
            showInWorld = !"false".equals(values.getProperty("world"));
        }
    }

    public static void save() {
        Properties values = new Properties();
        values.setProperty("pins", Boolean.toString(showPins));
        values.setProperty("waypoints", Boolean.toString(showWaypoints));
        values.setProperty("world", Boolean.toString(showInWorld));
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                values.store(writer, null);
            }
        } catch (IOException ignored) {
            // A switch that isn't remembered is no reason to stop the game.
        }
    }

    public static void setMates(List<Mate> fresh) {
        mates = List.copyOf(fresh);
    }

    public static List<Mate> mates(String dimension) {
        List<Mate> out = new ArrayList<>();
        for (Mate mate : mates) {
            if (mate.dimension().equals(dimension)) {
                out.add(mate);
            }
        }
        return out;
    }

    public static void arrow(GuiGraphics g, net.minecraft.resources.ResourceLocation texture, float x, float y, float size, float degrees) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        g.pose().scale(size / 17F, size / 17F, 1F);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(texture, -7, -8, 14, 17, 0F, 0F, 14, 17, 14, 17);
        g.pose().popPose();
    }

    public static void set(List<Mark> fresh) {
        marks = List.copyOf(fresh);
    }

    /** Teammates' marks in this dimension that the player wants to see. */
    public static List<Mark> visible(String dimension) {
        load();
        List<Mark> out = new ArrayList<>();
        for (Mark mark : marks) {
            if (mark.dimension().equals(dimension) && (mark.pin() ? showPins : showWaypoints)) {
                out.add(mark);
            }
        }
        return out;
    }

    /** The player's own pin and waypoints here, for the party and for the view of the world. */
    public static List<Mark> own(String dimension) {
        List<Mark> out = new ArrayList<>();
        if (MapConfig.pinActive && !LocalPlaces.routing()) {
            out.add(new Mark("", 0xE0B84C, "Метка", MapConfig.pinWorldX, Double.NaN, MapConfig.pinWorldZ, dimension, true));
        }
        var waypoints = WaypointManager.getInstance().getWaypointsForDimension(dimension);
        if (waypoints != null) {
            for (WaypointManager.Waypoint wp : waypoints) {
                if (wp != null && !wp.deathPoint) {
                    out.add(new Mark("", 0xE0B84C, wp.name, wp.x, wp.hasY ? wp.y : Double.NaN, wp.z, dimension, false, WaypointManager.resolveIconItemId(wp),13));
                }
            }
        }
        return out;
    }

    private static net.minecraft.resources.ResourceLocation crossOf(int color) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        String name = blue >= red && blue >= green ? "mark_blue" : red >= green ? "mark_red" : "mark_green";
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simplemap", "textures/gui/" + name + ".png");
    }

    public static void icon(GuiGraphics g, net.minecraft.resources.ResourceLocation texture, float x, float y, int half) {icon(g,texture,x,y,half,0xffffff);}
    public static void icon(GuiGraphics g, net.minecraft.resources.ResourceLocation texture, float x, float y, int half,int color) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(texture, -half, -half, half * 2, half * 2, 0F, 0F, 32, 32, 32, 32);
        g.pose().popPose();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
    }

    public record Location(String id, String structure, double x, double y, double z, int kind, boolean open,String label) {
        public Location(String id,String structure,double x,double y,double z,int kind,boolean open){this(id,structure,x,y,z,kind,open,"");}
        public String title() { return LocalPlaces.name(id,id.startsWith("waypoint:") ? id.substring(9) : open ? (kind==0?obeliskTitle(label):structureTitle(structure,kind)) : "Неизвестное место"); }
        public String description() { return kind==12 ? "Место вашей гибели. Здесь стоит поискать оставленные вещи." : kind==13 ? "Ваша сохранённая метка. Ориентир для будущего путешествия." : open ? placeDescription(kind) : "Здесь обнаружена настоящая постройка. Подойдите ближе, чтобы узнать, что это за место."; }
    }
    private static final java.util.Map<String,String> STRUCTURE_TITLES=java.util.Map.ofEntries(
        java.util.Map.entry("ancient_city","Древний город"),java.util.Map.entry("stronghold","Крепость"),
        java.util.Map.entry("mineshaft","Заброшенная шахта"),java.util.Map.entry("mineshaft_mesa","Золотые шахты"),
        java.util.Map.entry("trial_chambers","Камеры испытаний"),java.util.Map.entry("pillager_outpost","Аванпост разбойников"),
        java.util.Map.entry("mansion","Лесной особняк"),java.util.Map.entry("monument","Подводная крепость"),
        java.util.Map.entry("desert_pyramid","Пустынный храм"),java.util.Map.entry("jungle_pyramid","Храм в джунглях"),
        java.util.Map.entry("spawner","Комната со спавнером"),java.util.Map.entry("trail_ruins","Руины троп"),
        java.util.Map.entry("shipwreck","Затонувший корабль"),java.util.Map.entry("shipwreck_beached","Корабль на берегу"),
        java.util.Map.entry("buried_treasure","Зарытое сокровище"),java.util.Map.entry("fortress","Крепость Незера"),
        java.util.Map.entry("bastion_remnant","Развалины бастиона"),java.util.Map.entry("end_city","Город Края"),
        java.util.Map.entry("igloo","Иглу"),java.util.Map.entry("swamp_hut","Хижина ведьмы"),
        java.util.Map.entry("ogre_kings_fort","Форт короля огров"),java.util.Map.entry("ogre_outpost_plains","Равнинный лагерь огров"),
        java.util.Map.entry("ogre_outpost_swamp","Болотный лагерь огров"),java.util.Map.entry("ribbit_village","Поселение Ribbits"),
        java.util.Map.entry("wild_ruin","Заброшенные руины"),java.util.Map.entry("mayan_ruins","Руины майя"),java.util.Map.entry("roman_ruined_temple","Разрушенный римский храм"),
        java.util.Map.entry("ruined_german_village","Заброшенная деревня"),java.util.Map.entry("illager_camp","Лагерь разбойников"),
        java.util.Map.entry("illager_hideout","Убежище разбойников"),java.util.Map.entry("illager_manor","Поместье разбойников"),
        java.util.Map.entry("jungle_ruins","Руины в джунглях"),java.util.Map.entry("desert_ruins","Руины в пустыне"),
        java.util.Map.entry("skeleton_dungeon","Подземелье скелетов"),java.util.Map.entry("spider_dungeon","Паучье подземелье"),
        java.util.Map.entry("zombie_dungeon","Подземелье зомби"));
    public static String structureTitle(String structure,int kind){
        if(kind==1||kind==4)return "Данж";
        if(kind==0||kind==11||structure.isEmpty())return placeName(kind);
        String path=structure.substring(structure.indexOf(':')+1);
        if(structure.startsWith("croaks:"))return path.equals("outpost")?"Лагерь злых лягушек":path.equals("shed")?"Хижина злых лягушек":"Грибное убежище Croaks";
        if(path.startsWith("tavern_"))return "Поселение · таверна";
        String title=STRUCTURE_TITLES.get(path);if(title!=null)return title;
        if(path.startsWith("ruined_portal"))return "Разрушенный портал";
        if(path.startsWith("ocean_ruin"))return "Подводные руины";
        if(path.startsWith("village_"))return "Деревня жителей";
        if(kind==5||kind==6||kind==7||kind==9)return placeName(kind);
        // Do not expose raw English registry names as player-facing map labels.
        return placeName(kind);
    }
    private static volatile List<Location> locations = List.of();
    public static String obeliskTitle(String label){
        String name=label==null?"":label.strip();
        if(name.isEmpty()||name.equalsIgnoreCase("waystone")||name.equalsIgnoreCase("обелиск")||name.equalsIgnoreCase("камень телепорта"))return "Обелиск";
        return "Обелиск ["+name+"]";
    }
    public static String routeIcon(Location place,String saved){
        if(!place.open())return "poi:unknown";
        return saved==null||saved.isEmpty()||saved.startsWith("poi:")?"poi:"+place.kind():saved;
    }
    private static final String[] PLACE_FILES = { "poi_waystone", "poi_dungeon", "poi_witch", "poi_village", "poi_dungeon", "poi_ogre", "poi_frog", "poi_frog_enemy", "poi_ruins", "poi_pillager", "poi_portal", "poi_home" };
    private static final String[] PLACE_NAMES = { "Обелиск", "Данж", "Ведьмины земли", "Поселение", "Данж", "Лагерь огров", "Поселение Ribbits", "Лагерь злых лягушек", "Руины", "Лагерь разбойников", "Разрушенный портал", "Ваш дом" };
    private static final String[] PLACE_DESCRIPTIONS = {
        "Древний путевой камень. Активируйте его, чтобы открыть новую точку для путешествий.",
        "Запутанное сооружение, которое может скрывать опасности и ценные находки. Прихватите факелы и припасы.",
        "Тихое на вид убежище среди болот. Будьте внимательны: незваным гостям здесь не всегда рады.",
        "Поселение жителей: дома, ремёсла и торговля. Подходящее место для передышки в пути.",
        "Необычная постройка, замеченная во время исследования. Осмотритесь — возможно, здесь есть своя история.",
        "Укрепление огров. Здесь стоит ожидать сильных противников и готовиться к встрече заранее.",
        "Мирное поселение Ribbits. Прислушайтесь к звукам болот и познакомьтесь с его необычными обитателями.",
        "Недружелюбное поселение лягушек. Подходите осторожно и держите оружие наготове.",
        "Остатки старого сооружения. Среди обломков могут сохраниться следы прошлого.",
        "Убежище разбойников. Их дозорные могут заметить путника ещё на подходе.",
        "Обломки древнего прохода в Незер. Осмотрите раму: возможно, портал ещё удастся восстановить.",
        "Ваша последняя установленная точка возрождения — место, куда хочется вернуться после путешествия."
    };
    private static final net.minecraft.resources.ResourceLocation[] PLACE_TEXTURES = java.util.Arrays.stream(PLACE_FILES)
        .map(f -> net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simplemap", "textures/gui/" + f + ".png"))
        .toArray(net.minecraft.resources.ResourceLocation[]::new);
    private static int safeKind(int kind) { return kind >= 0 && kind < PLACE_FILES.length ? kind : 4; }
    public static String placeName(int kind) { return PLACE_NAMES[safeKind(kind)]; }
    public static String placeDescription(int kind) { return PLACE_DESCRIPTIONS[safeKind(kind)]; }
    public static net.minecraft.resources.ResourceLocation placeIcon(int kind) { return kind==12 ? SKULL : PLACE_TEXTURES[safeKind(kind)]; }
    private static boolean hiddenUnderground(Location p) {
        String id=p.structure().isEmpty()?p.id():p.structure();
        return id.contains("minecraft:spawner")||id.contains("mineshaft")||id.contains("ancient_city")
            ||id.contains("stronghold")||id.contains("trial_chamber")||id.contains("betterdungeons:")||id.contains("monster_room")||id.contains("underground")
            ||id.contains("catacomb")||id.contains("small_dungeon");
    }
    private record Snapshot(List<Location> locations,List<double[]> points) {}
    private static final java.util.Map<String,java.util.LinkedHashMap<String,Location>> placeCache=new java.util.HashMap<>();
    private static final java.util.Map<String,java.util.LinkedHashMap<String,Location>> pendingChanges=new java.util.HashMap<>();
    private static volatile java.util.Map<String,Snapshot> placeSnapshots=java.util.Map.of();
    public static List<Location> locations(String dimension) {
        var result=new java.util.LinkedHashMap<String,Location>();
        sharedPlaces.values().forEach(dims->dims.getOrDefault(dimension,java.util.Map.of()).forEach(result::put));
        Snapshot snapshot=placeSnapshots.get(dimension);if(snapshot!=null)for(var p:snapshot.locations()){
            var shared=result.get(p.id());result.put(p.id(),shared!=null&&shared.open()&&!p.open()?shared:p);
        }result.keySet().removeIf(id->LocalPlaces.hidden(dimension,id));return List.copyOf(result.values());
    }
    private static final java.util.Map<java.util.UUID,java.util.Map<String,java.util.Map<String,Location>>> sharedPlaces=new java.util.HashMap<>();
    public static void retainParty(java.util.Set<java.util.UUID> owners){sharedPlaces.keySet().retainAll(owners);SharedExploration.retainParty(owners);}
    public static void setSharedLocations(java.util.UUID owner,String dim,boolean reset,List<Location> points){ /* Personal discovery policy. */ }
    public static void setLocations(String dimension,List<Location> changes) {
        if(dimension.isEmpty()){closeJournal();return;}
        if(!LocalPlaces.isBound()){var queued=pendingChanges.computeIfAbsent(dimension,k->new java.util.LinkedHashMap<>());for(var p:changes)queued.put(p.id(),p);}
        var cache=placeCache.computeIfAbsent(dimension,d->new java.util.LinkedHashMap<>());
        for(var p:changes)if(p.kind()<0){sharedPlaces.values().forEach(dims->dims.values().forEach(points->points.remove(p.id())));if(LocalPlaces.following(p.id()))LocalPlaces.clearRoute(true);}
        for(Location p:changes){if(p.kind()<0||(p.open()&&p.kind()==4)||hiddenUnderground(p))cache.remove(p.id());else { Location previous=cache.get(p.id()); cache.put(p.id(),previous!=null&&previous.open()&&!p.open()?previous:p); }}
        cache.values().removeIf(TeamMarks::hiddenUnderground);
        while(cache.size()>16384)cache.remove(cache.keySet().iterator().next());
        var list=List.copyOf(cache.values());
        var next=new java.util.HashMap<>(placeSnapshots);
        next.put(dimension,new Snapshot(list,list.stream().map(p->new double[]{p.x(),p.z(),p.kind(),p.open()?1:0,LocalPlaces.color(p.id())}).toList()));
        placeSnapshots=java.util.Map.copyOf(next);
        LocalPlaces.journal().state.places.put(dimension,new java.util.LinkedHashMap<>(cache));
        LocalPlaces.journal().changed();
        LocalPlaces.journal().flush();
    }
    public static void setUnknown(String dimension,List<double[]> places) {
        // Compatibility with older callers; authoritative locations use setLocations.
        unknownDimension=dimension;unknown=List.copyOf(places);
    }
    public static List<double[]> unknown(String dimension) {
        return locations(dimension).stream().filter(p->mapped(p,dimension)).map(p->new double[]{p.x(),p.z(),p.kind(),p.open()?1:0,LocalPlaces.color(p.id())}).toList();
    }

    public static void openJournal(Path path) {
        var pending=LocalPlaces.isBound()?java.util.Map.<String,Snapshot>of():placeSnapshots;
        var queued=new java.util.HashMap<>(pendingChanges);
        if(!LocalPlaces.bind(path))return;
        pendingChanges.clear();
        SharedExploration.bind(path.resolveSibling(path.getFileName()+".terrain.dat"));
        placeCache.clear();placeSnapshots=java.util.Map.of();
        var saved=new java.util.LinkedHashMap<>(LocalPlaces.journal().state.places);
        saved.forEach((dim,points)->setLocations(dim,List.copyOf(points.values())));
        pending.forEach((dim,snapshot)->setLocations(dim,snapshot.locations()));
        queued.forEach((dim,changes)->setLocations(dim,List.copyOf(changes.values())));
    }
    public static void refreshStyles(){
        var next=new java.util.HashMap<String,Snapshot>();
        placeSnapshots.forEach((dim,snapshot)->next.put(dim,new Snapshot(snapshot.locations(),snapshot.locations().stream().map(p->new double[]{p.x(),p.z(),p.kind(),p.open()?1:0,LocalPlaces.color(p.id())}).toList())));
        placeSnapshots=java.util.Map.copyOf(next);
    }
    public static void closeJournal(){pendingChanges.clear();sharedPlaces.clear();SharedExploration.clear();LocalPlaces.close();placeCache.clear();placeSnapshots=java.util.Map.of();}
    public static java.util.Map<String,List<Location>> allLocations(){
        var out=new java.util.LinkedHashMap<String,List<Location>>();var dims=new java.util.HashSet<>(placeSnapshots.keySet());sharedPlaces.values().forEach(m->dims.addAll(m.keySet()));dims.forEach(dim->out.put(dim,locations(dim)));return out;
    }
    public static List<Mark> allVisible(){return marks.stream().filter(m->m.pin()?showPins:showWaypoints).toList();}
    public static Mark localRoute(String dim){
        var place=LocalPlaces.routeLocation();if(place==null||!dim.equals(LocalPlaces.routeDimension()))return null;
        return new Mark("",LocalPlaces.color(place.id()),place.title(),place.x(),place.y(),place.z(),dim,false,routeIcon(place,LocalPlaces.journal().state.routeIcon),place.kind());
    }
    public static void markIcon(GuiGraphics g,Mark mark,float x,float y,int half){
        if(mark.iconItem()!=null&&mark.iconItem().startsWith("poi:")){icon(g,"poi:unknown".equals(mark.iconItem())?UNKNOWN:placeIcon(mark.kind()),x,y,half,mark.color());return;}
        if(mark.iconItem()!=null&&!mark.iconItem().isEmpty()){PlaceEntry.itemIcon(g,mark.iconItem(),x,y,half);return;}
        cross(g,x,y,half,mark.color(),mark.owner().isEmpty());
    }

    /** The map's cross: the plain one for the player, the party colour for a teammate. */
    public static void cross(GuiGraphics g, float x, float y, int half, int color, boolean own) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(own ? RED_X : crossOf(color), -half, -half, half * 2, half * 2, 0F, 0F, 32, 32, 32, 32);
        g.pose().popPose();
    }

    public static boolean mapped(Location p,String dimension){
        if(p.open())return true;
        if(SharedExploration.learned(dimension,(int)Math.floor(p.x()),(int)Math.floor(p.z())))return true;
        return !com.velorise.simplemap.client.color.MapBlockData.isEmpty(MapManager.getInstance().getPackedBlockData((int)Math.floor(p.x()),(int)Math.floor(p.z())));
    }
    public static boolean autoPlacesVisible() {
        return LocalPlaces.showPois;
    }
    public static int placeHalf(int kind,boolean open){return !open?6:kind==8?11:8;}

    /** Called by the full map inside its world-to-screen transform. */
    public static void drawOnMap(GuiGraphics g, Font font, double minX, double maxX, double minZ, double maxZ, float scale, float overlay, double hoverX, double hoverZ) {
        MapMarkerLayout layout=new MapMarkerLayout();
        var screen=Minecraft.getInstance().screen instanceof com.velorise.simplemap.client.gui.MapScreen map?map:null;
        for (Location location : autoPlacesVisible()?locations(MapManager.getInstance().getCurrentDimensionResourceId()):List.<Location>of()) {
            if(!mapped(location,MapManager.getInstance().getCurrentDimensionResourceId()))continue;
            if(DioramaView.markerX(location.x(),location.z())<minX||DioramaView.markerX(location.x(),location.z())>maxX||DioramaView.markerZ(location.x(),location.z())<minZ||DioramaView.markerZ(location.x(),location.z())>maxZ)continue;
            int half=placeHalf(location.kind(),location.open());
            boolean selected=screen!=null&&screen.isSelectedLocation(location.id());
            boolean hover=Math.hypot((DioramaView.markerX(location.x(),location.z())-DioramaView.markerX(hoverX,hoverZ))*scale,(DioramaView.markerZ(location.x(),location.z())-DioramaView.markerZ(hoverX,hoverZ))*scale)<=(half+3)*overlay;
            boolean focused=selected||hover;
            int sx=(int)Math.round((DioramaView.markerX(location.x(),location.z())-minX)*scale/overlay),sy=(int)Math.round((DioramaView.markerZ(location.x(),location.z())-minZ)*scale/overlay);
            if(!layout.icon(sx,sy,half,focused))continue;
            float size=overlay/scale;
            g.pose().pushPose();DioramaView.point(g.pose(),location.x(),location.z(),5);g.pose().scale(size,size,1);
            g.pose().pushPose();
            float selectedScale=screen==null?1f:screen.selectionScale(location.id());
            g.pose().scale(selectedScale,selectedScale,1);
            icon(g,location.open()?placeIcon(location.kind()):UNKNOWN,0,0,half,LocalPlaces.color(location.id()));
            g.pose().popPose();
            // Questions are symbols, not a wall of repeated labels. Hover or selection reveals their caption.
            if(location.open()||focused) {
                String title=location.title();int width=font.width(title),labelY=half+3;
                if(layout.label(sx-width/2-2,sy+labelY-1,width+4,12,focused)) {
                    g.fill(-width/2-2,labelY-1,width/2+2,labelY+11,0xB0000000);
                    g.drawString(font,title,-width/2,labelY,0xFFD9CBA0,false);
                }
            }
            g.pose().popPose();
        }
        var privateRoute=localRoute(MapManager.getInstance().getCurrentDimensionResourceId());
        if(privateRoute!=null&&(!autoPlacesVisible()||LocalPlaces.routeLocation().id().startsWith("waypoint:"))
                &&DioramaView.markerX(privateRoute.x(),privateRoute.z())>=minX&&DioramaView.markerX(privateRoute.x(),privateRoute.z())<=maxX&&DioramaView.markerZ(privateRoute.x(),privateRoute.z())>=minZ&&DioramaView.markerZ(privateRoute.x(),privateRoute.z())<=maxZ){
            g.pose().pushPose();DioramaView.point(g.pose(),privateRoute.x(),privateRoute.z(),8);g.pose().scale(overlay/scale,overlay/scale,1);markIcon(g,privateRoute,0,0,10);g.pose().popPose();
        }
        for (Mate mate : mates(MapManager.getInstance().getCurrentDimensionResourceId())) {
            if (DioramaView.markerX(mate.x(),mate.z()) < minX || DioramaView.markerX(mate.x(),mate.z()) > maxX || DioramaView.markerZ(mate.x(),mate.z()) < minZ || DioramaView.markerZ(mate.x(),mate.z()) > maxZ) {
                continue;
            }
            float factor = 2F / scale * overlay * 0.5F;
            g.pose().pushPose();
            DioramaView.point(g.pose(),mate.x(), mate.z(), 7);
            g.pose().scale(factor, factor, 1F);
            arrow(g, mate.arrow(), 0, 0, 13, mate.yaw() + 180F- (DioramaView.overlaysActive()?dev.diorama.Projection.facing().turns*90:0));
            int width = font.width(mate.name());
            g.fill(-width / 2 - 2, 8, width / 2 + 2, 19, 0xB0000000);
            g.drawString(font, mate.name(), -width / 2, 10, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
        for (Mark mark : visible(MapManager.getInstance().getCurrentDimensionResourceId())) {
            if (DioramaView.markerX(mark.x(),mark.z()) < minX || DioramaView.markerX(mark.x(),mark.z()) > maxX || DioramaView.markerZ(mark.x(),mark.z()) < minZ || DioramaView.markerZ(mark.x(),mark.z()) > maxZ) {
                continue;
            }
            float factor = 2F / scale * overlay * 0.5F;
            g.pose().pushPose();
            DioramaView.point(g.pose(),mark.x(), mark.z(), 6);
            g.pose().scale(factor, factor, 1F);
            markIcon(g,mark,0,0,6);
            String title = mark.title();
            int width = font.width(title);
            g.fill(-width / 2 - 2, 7, width / 2 + 2, 18, 0xB0000000);
            g.drawString(font, title, -width / 2, 9, 0xFF000000 | mark.color(), false);
            g.pose().popPose();
        }
    }
}
