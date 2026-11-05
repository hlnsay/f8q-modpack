package com.velorise.simplemap.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Logical screen coordinates; keeps marker and label density bounded at every zoom. */
public final class MapMarkerLayout {
    private static final int CELL=32;
    private record Rect(int left,int top,int right,int bottom) {
        boolean intersects(Rect b){return left<b.right&&right>b.left&&top<b.bottom&&bottom>b.top;}
    }
    private final Map<Long,List<Rect>> icons=new HashMap<>(),labels=new HashMap<>();
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private boolean reserve(Map<Long,List<Rect>> bins,Rect r,boolean force){
        int minX=Math.floorDiv(r.left,CELL),maxX=Math.floorDiv(r.right,CELL);
        int minY=Math.floorDiv(r.top,CELL),maxY=Math.floorDiv(r.bottom,CELL);
        if(!force)for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)
            for(Rect other:bins.getOrDefault(key(x,y),List.of()))if(r.intersects(other))return false;
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)bins.computeIfAbsent(key(x,y),k->new ArrayList<>()).add(r);
        return true;
    }
    public boolean icon(int x,int y,int half,boolean focused){return reserve(icons,new Rect(x-half-2,y-half-2,x+half+2,y+half+2),focused);}
    public boolean label(int x,int y,int width,int height,boolean focused){return reserve(labels,new Rect(x,y,x+width,y+height),focused);}
    public static float pulseScale(double seconds){return 1.28f+0.08f*(float)Math.sin(seconds*2*Math.PI/1.3);}
}
