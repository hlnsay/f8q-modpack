package com.f8q.party;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import team.creative.playerrevive.server.PlayerReviveServer;

/** All bottom layers move together; the 3D hand renderer is separate. */
final class HudLayers {
    private static final Set<ResourceLocation> BOTTOM=Set.of(VanillaGuiLayers.HOTBAR,VanillaGuiLayers.PLAYER_HEALTH,
        VanillaGuiLayers.ARMOR_LEVEL,VanillaGuiLayers.FOOD_LEVEL,VanillaGuiLayers.AIR_LEVEL,VanillaGuiLayers.VEHICLE_HEALTH,
        VanillaGuiLayers.EXPERIENCE_BAR,VanillaGuiLayers.EXPERIENCE_LEVEL,VanillaGuiLayers.JUMP_METER,VanillaGuiLayers.SELECTED_ITEM_NAME);
    private static final Set<ResourceLocation> translated=new HashSet<>();
    static boolean downed(){var p=Minecraft.getInstance().player;return p!=null&&p.isAlive()&&PlayerReviveServer.getBleeding(p).isBleeding();}
    static void before(RenderGuiLayerEvent.Pre event){
        if((!BOTTOM.contains(event.getName())&&!event.getName().getNamespace().equals("appleskin"))||event.isCanceled())return;
        if(downed()){event.setCanceled(true);return;}
        // Vanilla places armor ten GUI pixels above health; XP-like HUD uses an eight-pixel row pitch.
        int offset=event.getName().equals(VanillaGuiLayers.ARMOR_LEVEL)?-4:-6;
        event.getGuiGraphics().pose().pushPose();event.getGuiGraphics().pose().translate(0,offset,0);translated.add(event.getName());
    }
    static void after(RenderGuiLayerEvent.Post event){if(translated.remove(event.getName()))event.getGuiGraphics().pose().popPose();}
}
