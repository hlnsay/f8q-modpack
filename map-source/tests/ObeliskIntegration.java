package com.f8q.maptest;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Test-only mod: never copied to the real client, server, or release artifacts. */
@Mod("f8q_map_test")
public final class ObeliskIntegration {
    private int phase,wait;
    private final BlockPos pos=new BlockPos(8,200,8);
    public ObeliskIntegration(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private boolean exists()throws Exception{
        var field=Class.forName("com.f8q.party.Unknowns").getDeclaredField("store");field.setAccessible(true);Object store=field.get(null);
        var places=store.getClass().getDeclaredField("places");places.setAccessible(true);
        return ((java.util.Map<?,?>)places.get(store)).containsKey("minecraft:overworld|block|"+pos.asLong());
    }
    private void tick(ServerTickEvent.Post event){
        var level=event.getServer().overworld();if(level.getChunkSource().getChunkNow(0,0)==null)return;
        if(++wait<12||phase>=4)return;wait=0;
        try{
            var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            if(phase==1||phase==3){if(!exists())throw new AssertionError("Placed obelisk missing under leaf canopy");}
            if(phase==2&&exists())throw new AssertionError("Broken obelisk marker survived");
            if(phase==3){System.out.println("F8Q_TEST_PASS: obelisk under canopy: place, break, replace at same position");phase++;return;}
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("waystones:waystone"));
            if(block==Blocks.AIR)throw new AssertionError("Waystones block registry missing");
            if(phase==1){
                var state=level.getBlockState(pos);level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pos,state,fake));
            }else{
                level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos.above(10),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true));
                var state=block.defaultBlockState();
                level.setBlockAndUpdate(pos,state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER));
                level.setBlockAndUpdate(pos.above(),state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,pos),Blocks.STONE.defaultBlockState(),fake));
            }
            phase++;
        }catch(Throwable error){phase=4;System.out.println("F8Q_TEST_FAIL: "+error);error.printStackTrace();}
    }
}
