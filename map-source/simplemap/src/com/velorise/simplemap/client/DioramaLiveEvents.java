package com.velorise.simplemap.client;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
@EventBusSubscriber(modid="simplemap",value=Dist.CLIENT)
public final class DioramaLiveEvents {
 @SubscribeEvent public static void loaded(ChunkEvent.Load event){if(event.getLevel() instanceof ClientLevel&&event.getChunk() instanceof LevelChunk chunk)DioramaView.changed(chunk);}
 @SubscribeEvent public static void left(ClientPlayerNetworkEvent.LoggingOut event){DioramaView.get().release();}
}
