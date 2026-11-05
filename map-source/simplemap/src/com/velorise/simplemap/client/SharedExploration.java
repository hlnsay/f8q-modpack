package com.velorise.simplemap.client;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
/** Compatibility boundary: maps are personal. No raster loading, export, upload or rendering. */
public final class SharedExploration {
    public record Tile(String dimension,int x,int z,int[] pixels) {}
    public static void bind(Path path) {}
    public static void clear() {}
    public static boolean learned(String dimension,int x,int z){return false;}
    public static void retainParty(Set<UUID> members) {}
    public static void receive(UUID owner,List<Tile> tiles) {}
    public static void tick(Consumer<List<Tile>> send) {}
    public static void render(GuiGraphics g,double minX,double maxX,double minZ,double maxZ) {}
}
