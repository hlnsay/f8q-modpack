package com.velorise.simplemap.client;
public final class WorldClock {
    public static String format(long ticks){long minutes=Math.floorMod(ticks+6000,24000)*1440/24000;return String.format(java.util.Locale.ROOT,"%02d:%02d",minutes/60,minutes%60);}
}
