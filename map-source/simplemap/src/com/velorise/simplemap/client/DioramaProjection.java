package com.velorise.simplemap.client;

/** Orthographic camera shared by terrain, upright markers and mouse picking. */
public final class DioramaProjection {
    public static final double SIN = Math.sin(Math.toRadians(50));
    public static final double COS = Math.cos(Math.toRadians(50));
    private DioramaProjection() {}
    public static double y(double z, double height, double centerZ, double reference) {
        return centerZ + (z-centerZ)*SIN - (height-reference)*COS;
    }
    public static double unprojectZ(double projectedZ, double height, double centerZ, double reference) {
        return centerZ + (projectedZ-centerZ+(height-reference)*COS)/SIN;
    }
}
