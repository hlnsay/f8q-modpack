package com.f8q.knock;

/** Local-viewer policy: no server glowing flag or visibility leak to strangers. */
public final class PartyOutlinePolicy {
    public static int color(boolean viewerDowned,boolean targetDowned,boolean mate,boolean alive){
        if(!mate||!alive)return -1;
        if(targetDowned)return 0xF04444;
        return viewerDowned?0xFFFFFF:-1;
    }
}
