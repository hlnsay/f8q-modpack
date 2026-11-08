package com.f8q.packdefaults;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;
/** Seed only new mod bindings. Never replace a saved player's binding. */
@Mod(value="f8qpackdefaults",dist=Dist.CLIENT)
public final class PackDefaults {
 private final Set<String> existing=new HashSet<>();private boolean applied;
 private static final Map<String,Integer> DEFAULTS=Map.of("key.voice_chat",299,"key.push_to_talk",280,"key.mute_microphone",-1,"key.disable_voice_chat",-1,"key.hide_icons",-1,"key.voice_chat_group",-1,"key.veil.editor",-1);
 public PackDefaults(){
  try{for(String line:Files.readAllLines(FMLPaths.GAMEDIR.get().resolve("options.txt"))){int colon=line.indexOf(':');if(colon>0)existing.add(line.substring(0,colon));}}catch(java.io.IOException ignored){}
  NeoForge.EVENT_BUS.addListener(this::tick);
 }
 private void tick(ClientTickEvent.Post event){
  if(applied)return;var mc=Minecraft.getInstance();if(mc.options==null||mc.screen==null)return;applied=true;boolean changed=false;
  for(KeyMapping key:mc.options.keyMappings){Integer code=DEFAULTS.get(key.getName());if(code!=null&&!existing.contains("key_"+key.getName())){key.setKey(code<0?InputConstants.UNKNOWN:InputConstants.Type.KEYSYM.getOrCreate(code));changed=true;}}
  if(changed){KeyMapping.resetMapping();mc.options.save();System.out.println("F8Q: initialized non-conflicting new voice/editor key defaults; saved player bindings preserved");}
 }
}
