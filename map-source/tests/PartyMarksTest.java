package com.f8q.party;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import com.velorise.simplemap.client.TeamMarks;

public final class PartyMarksTest {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  UUID owner=UUID.randomUUID(),mate=UUID.randomUUID(),stranger=UUID.randomUUID();
  var field=F8qParty.class.getDeclaredField("parties");field.setAccessible(true);
  var parties=(List<List<UUID>>)field.get(null);parties.clear();parties.add(new ArrayList<>(List.of(owner,mate)));
  check(F8qParty.markRecipients(owner).equals(List.of(mate)),"only other party members receive marks");
  check(F8qParty.markRecipients(stranger).isEmpty(),"outsider cannot relay marks into a party");
  var spot=new Net.Spot(false,"Домик",12.5,71,-44.5,"minecraft:overworld","minecraft:diamond",13);
  var payload=new Net.MarksDown(owner,List.of(spot));var buf=new FriendlyByteBuf(Unpooled.buffer());
  Net.MarksDown.CODEC.encode(buf,payload);check(Net.MarksDown.CODEC.decode(buf).equals(payload),"label coordinates dimension and custom icon survive relay");buf.release();
  var members=PartyClient.class.getDeclaredField("mates");members.setAccessible(true);
  members.set(null,List.of(new Net.Mate(owner,"Mate",1,20,20,0,0,-1,0,70,0,"minecraft:overworld",true,0,0)));
  TeamMarks.set(List.of());PartyClient.marks(new Net.MarksDown(stranger,List.of(spot)));
  check(TeamMarks.allVisible().isEmpty(),"client rejects unrelated player's marks");
  PartyClient.marks(payload);var visible=TeamMarks.allVisible();
  check(visible.size()==1&&visible.get(0).label().equals("Домик")&&visible.get(0).iconItem().equals("minecraft:diamond"),"manual mark published for map minimap list and world HUD");
  PartyClient.marks(new Net.MarksDown(owner,List.of()));check(TeamMarks.allVisible().isEmpty(),"empty owner snapshot removes deleted marks");
  PartyClient.marks(payload);members.set(null,List.of());var publish=PartyClient.class.getDeclaredMethod("publish");publish.setAccessible(true);publish.invoke(null);
  check(TeamMarks.allVisible().isEmpty(),"leaving a party removes its markers");
  parties.clear();members.set(null,List.of());TeamMarks.set(List.of());
  System.out.println("PASS: manual party marker relay, icon codec, stranger rejection, deletion and leaving party");
 }
}
