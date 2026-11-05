package com.velorise.simplemap.client;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.velorise.simplemap.client.color.MapBlockData;
import com.velorise.simplemap.client.color.SurfaceColorizer.SurfaceTintData;

/** Captures real block-colour providers on the client thread, never from a fake biome world. */
public final class LiveMapColors {
    private static Object level;
    public static void tick(Minecraft mc){if(mc.level!=null&&level!=mc.level){level=mc.level;ChunkScanner.getInstance().requestImmediateSurfaceRefresh(mc);}}
    public static void capture(int cx,int cz,int start,long[] pixels,int[] tints,boolean[] valid,int offset,int count){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.isSameThread()||!mc.level.hasChunk(cx,cz))return;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int i=0;i<count;i++){
            int source=offset+i,column=start+i;if(!valid[source]||MapBlockData.isEmpty(pixels[source]))continue;
            pos.set(cx*16+(column&15),MapBlockData.topY(pixels[source]),cz*16+(column>>4));
            var state=mc.level.getBlockState(pos);int tint=mc.getBlockColors().getColor(state,mc.level,pos,0);
            if(tint!=-1)tints[source]=SurfaceTintData.fromProviderResult(tint);
        }
    }
}
