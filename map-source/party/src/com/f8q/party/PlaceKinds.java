package com.f8q.party;
import java.util.Locale;
/** Classification only: coordinates must come from a generated object. */
final class PlaceKinds {
    static int classify(String value) {
        String id = value.toLowerCase(Locale.ROOT);
        if (id.contains("waystone")) return 0;
        if (id.contains("ruined_portal") || id.contains("broken_portal")) return 10;
        if (id.contains("ogre")) return 5;
        if (id.startsWith("croaks:")) return 7;
        if (id.contains("ribbit") || id.contains("frog_village") || id.contains("frog_camp")) {
            for (String word : new String[]{"evil", "hostile", "gremlin", "cursed", "bandit", "raider", "corrupt"})
                if (id.contains(word)) return 7;
            return 6;
        }
        if (id.equals("trek:overworld/medium/desert_outpost")||id.equals("trek:overworld/medium/jungle_outpost")) return 9;
        if (id.equals("trek:overworld/medium/camp_1")||id.equals("trek:overworld/medium/camp_3")
                ||id.equals("trek:overworld/medium/camp_4")||id.equals("trek:overworld/medium/spruce_camp")) return 3;
        if (id.contains("pillager") || (id.contains("illager")&&!id.contains("villager"))) return 9;
        if (id.contains("ruin") || id.contains("remnant") || id.contains("abandoned")) return 8;
        if (id.contains("witch") || id.contains("swamp_hut")) return 2;
        if (id.contains("village") || id.contains("hamlet") || id.contains("town") || id.contains("tavern") || id.contains("inn_")) return 3;
        for (String word : new String[]{"dungeon", "crypt", "catacomb", "tomb", "fort", "citadel", "keep", "donjon", "lair", "castle", "temple", "pyramid", "bastion", "mansion", "monument", "sanctum", "manor", "ancient_city", "chamber", "stronghold", "mineshaft"})
            if (id.contains(word)) return 1;
        return -1;
    }
    static boolean hidden(String value) {
        String id=value.toLowerCase(Locale.ROOT);
        return id.equals("minecraft:spawner") || id.contains("mineshaft") || id.contains("ancient_city")
            || id.contains("stronghold") || id.contains("trial_chamber")
            || id.startsWith("betterdungeons:") || id.contains("monster_room") || id.contains("underground")
            || id.contains("catacomb") || id.contains("small_dungeon");
    }
    static boolean portalUnderground(String placement) {
        return placement.equals("UNDERGROUND") || placement.equals("IN_MOUNTAIN");
    }
    static boolean buried(int top,int ground) {return top<ground-2;}
    static boolean undergroundStep(String step) {
        return step.equals("UNDERGROUND_STRUCTURES") || step.equals("UNDERGROUND_DECORATION");
    }
    static double center(int min, int max) { return min + (max - (double) min) / 2.0 + .5; }
    static double distance(double x, double z, int minX, int maxX, int minZ, int maxZ) {
        double dx = Math.max(minX-x, Math.max(0,x-maxX)), dz = Math.max(minZ-z, Math.max(0,z-maxZ));
        return dx*dx+dz*dz;
    }
}
