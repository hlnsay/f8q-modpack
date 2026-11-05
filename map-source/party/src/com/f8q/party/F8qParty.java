package com.f8q.party;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.server.PlayerReviveServer;

/** Parties of up to three: who is in them, and what their members may see of each other. */
@Mod("f8qparty")
public final class F8qParty {
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("f8qparty");
    private static final net.minecraft.resources.ResourceLocation WOUNDED = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("f8qparty", "wounded");
    static final int LIMIT = 3;
    private static final int INVITE_TICKS = 600;
    private static final int RECONNECT_TICKS = 1200;
    /** Each party is its members in order; a member's place picks the colour. */
    private static final List<List<UUID>> parties = new ArrayList<>();
    private static final Map<UUID, Invite> invites = new HashMap<>();
    /** Members who left the game, and the tick their place in the party runs out. */
    private static final Map<UUID, Integer> gone = new HashMap<>();
    private static final Map<UUID, String> names = new HashMap<>();
    private static Map<UUID, UUID> lastHelping = Map.of();
    private static int clock;

    private record Invite(UUID from, int expires) {
    }

    public F8qParty(IEventBus bus) {
        bus.addListener(F8qParty::payloads);
        NeoForge.EVENT_BUS.addListener(F8qParty::tick);
        NeoForge.EVENT_BUS.addListener(F8qParty::started);
        NeoForge.EVENT_BUS.addListener(Unknowns::sent);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> Unknowns.stopping(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent stop) -> Unknowns.stopped());
        NeoForge.EVENT_BUS.addListener(F8qParty::joined);
        NeoForge.EVENT_BUS.addListener(F8qParty::quit);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, F8qParty::interact);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Net.Action.TYPE, Net.Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                action(player, payload);
            }
        }));
        registrar.versioned("3").playToServer(Net.MarksUp.TYPE, Net.MarksUp.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                relay(player, payload);
            }
        }));
        registrar.versioned("2").playToClient(Net.Unknowns.TYPE, Net.Unknowns.CODEC, (payload, context) -> context.enqueueWork(() -> PartyClientBridge.unknowns(payload)));
        registrar.versioned("3").playToClient(Net.MarksDown.TYPE, Net.MarksDown.CODEC, (payload, context) -> context.enqueueWork(() -> PartyClientBridge.marks(payload)));
        registrar.playToClient(Net.Offer.TYPE, Net.Offer.CODEC, (payload, context) -> context.enqueueWork(() -> PartyClientBridge.offer(payload)));
        registrar.playToClient(Net.Helpers.TYPE, Net.Helpers.CODEC, (payload, context) -> context.enqueueWork(() -> PartyClientBridge.helpers(payload)));
        registrar.playToClient(Net.State.TYPE, Net.State.CODEC, (payload, context) -> context.enqueueWork(() -> PartyClientBridge.state(payload)));
    }

    private static void relay(ServerPlayer from, Net.MarksUp marks) {
        List<UUID> party = partyOf(from.getUUID());
        if (party == null) {
            return;
        }
        Net.MarksDown down = new Net.MarksDown(from.getUUID(), marks.spots().stream().limit(48).toList());
        for (UUID id : party) {
            ServerPlayer mate = from.getServer().getPlayerList().getPlayer(id);
            if (mate != null && mate != from) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(mate, down);
            }
        }
    }

    private static List<UUID> partyOf(UUID player) {
        for (List<UUID> party : parties) {
            if (party.contains(player)) {
                return party;
            }
        }
        return null;
    }

    /** Whether these two share a party. */
    public static boolean together(UUID a, UUID b) {
        List<UUID> party = partyOf(a);
        return party != null && party.contains(b);
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }

    private static void action(ServerPlayer player, Net.Action action) {
        MinecraftServer server = player.getServer();
        ServerPlayer target = server.getPlayerList().getPlayer(action.target());
        switch (action.kind()) {
            case Net.INVITE -> invite(player, target);
            case Net.ACCEPT -> accept(player, server);
            case Net.DECLINE -> {
                Invite invite = invites.remove(player.getUUID());
                ServerPlayer from = invite == null ? null : server.getPlayerList().getPlayer(invite.from());
                if (from != null) {
                    say(from, player.getGameProfile().getName() + " отказался от пати");
                }
            }
            case Net.LEAVE -> leave(player.getUUID(), server);
            default -> {
            }
        }
    }

    private static void invite(ServerPlayer from, ServerPlayer to) {
        if (to == null || to == from) {
            return;
        }
        List<UUID> mine = partyOf(from.getUUID());
        if (mine != null && mine.size() >= LIMIT) {
            say(from, "В пати уже " + LIMIT + " игрока");
        } else if (partyOf(to.getUUID()) != null) {
            say(from, to.getGameProfile().getName() + " уже в пати");
        } else if (invites.containsKey(to.getUUID())) {
            say(from, to.getGameProfile().getName() + " ещё решает");
        } else {
            invites.put(to.getUUID(), new Invite(from.getUUID(), clock + INVITE_TICKS));
            PacketDistributor.sendToPlayer(to, new Net.Offer(from.getGameProfile().getName(), INVITE_TICKS));
            say(from, "Приглашение отправлено: " + to.getGameProfile().getName());
        }
    }

    private static void accept(ServerPlayer player, MinecraftServer server) {
        Invite invite = invites.remove(player.getUUID());
        if (invite == null || invite.expires() < clock || partyOf(player.getUUID()) != null) {
            return;
        }
        List<UUID> party = partyOf(invite.from());
        if (party == null) {
            party = new ArrayList<>(List.of(invite.from()));
            parties.add(party);
        }
        if (party.size() >= LIMIT) {
            say(player, "Пати уже заполнена");
            return;
        }
        party.add(player.getUUID());
        save(server);
    }

    private static void leave(UUID player, MinecraftServer server) {
        List<UUID> party = partyOf(player);
        if (party == null) {
            return;
        }
        party.remove(player);
        if (party.size() < 2) {
            parties.remove(party);
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            PacketDistributor.sendToPlayer(online, new Net.State(List.of()));
        }
        for (UUID other : party) {
            ServerPlayer mate = server.getPlayerList().getPlayer(other);
            if (mate != null && party.size() < 2) {
                PacketDistributor.sendToPlayer(mate, new Net.State(List.of()));
            }
        }
        save(server);
    }

    /** Only someone from your own party can pick you up; the rest are told why nothing happens. */
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer player
                && PlayerReviveServer.getBleeding(target).isBleeding() && !together(player.getUUID(), target.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            say(player, "Поднять можно только игрока из пати");
        }
    }

    private static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            gone.remove(player.getUUID());
            names.put(player.getUUID(), player.getGameProfile().getName());
            if (partyOf(player.getUUID()) == null) {
                PacketDistributor.sendToPlayer(player, new Net.State(List.of()));
            }
        }
    }

    /** A dropped connection keeps its place for a minute. */
    private static void quit(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (partyOf(id) != null) {
            gone.put(id, clock + RECONNECT_TICKS);
        }
    }

    /** The minute runs only while someone is there to wait: a sleeping server ends no parties. */
    private static void expire(MinecraftServer server) {
        for (List<UUID> party : new ArrayList<>(parties)) {
            boolean anyone = party.stream().anyMatch(id -> server.getPlayerList().getPlayer(id) != null);
            for (UUID id : new ArrayList<>(party)) {
                if (!anyone || server.getPlayerList().getPlayer(id) != null) {
                    continue;
                }
                if (gone.computeIfAbsent(id, key -> clock + RECONNECT_TICKS) <= clock) {
                    gone.remove(id);
                    leave(id, server);
                }
            }
        }
    }

    /** A badly hurt player walks a little slower; healing gives the pace straight back. */
    private static void wounded(net.minecraft.server.level.ServerPlayer player) {
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean hurt = player.isAlive() && !player.isCreative() && !player.isSpectator() && player.getHealth() / Math.max(1, player.getMaxHealth()) <= 0.3F;
        if (hurt && !speed.hasModifier(WOUNDED)) {
            speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(WOUNDED, -0.1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!hurt && speed.hasModifier(WOUNDED)) {
            speed.removeModifier(WOUNDED);
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        Unknowns.tick(event.getServer());
        if (event.getServer().getTickCount() % 20 == 0) {
            event.getServer().getPlayerList().getPlayers().forEach(F8qParty::wounded);
        }
        clock++;
        invites.values().removeIf(invite -> invite.expires() < clock);
        if (clock % 5 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        expire(server);
        Map<UUID, UUID> helping = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IBleeding bleeding = PlayerReviveServer.getBleeding(player);
            if (bleeding.isBleeding()) {
                bleeding.revivingPlayers().forEach(helper -> helping.put(helper.getUUID(), player.getUUID()));
            }
        }
        if (!helping.isEmpty() || !lastHelping.isEmpty()) {
            PacketDistributor.sendToAllPlayers(new Net.Helpers(helping));
        }
        lastHelping = helping;
        for (List<UUID> party : parties) {
            List<Net.Mate> mates = new ArrayList<>();
            for (int i = 0; i < party.size(); i++) {
                mates.add(mate(party.get(i), i, server));
            }
            for (UUID member : party) {
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new Net.State(mates));
                }
            }
        }
    }

    private static Net.Mate mate(UUID id, int color, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            int left = Math.max(0, gone.getOrDefault(id, clock) - clock) / 20;
            return new Net.Mate(id, names.getOrDefault(id, ""), color, 0, 20, 0, 0, -1, 0, 0, 0, "", false, left, 0F);
        }
        IBleeding bleeding = PlayerReviveServer.getBleeding(player);
        int effect = player.hasEffect(MobEffects.WITHER) ? 2 : player.hasEffect(MobEffects.POISON) ? 1 : 0;
        return new Net.Mate(id, player.getGameProfile().getName(), color, player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(), effect,
            bleeding.isBleeding() ? bleeding.downedTime() : -1, player.getX(), player.getY(), player.getZ(), player.level().dimension().location().toString(), true, 0, player.getYRot());
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("f8qparty.json");
    }

    private static void save(MinecraftServer server) {
        try {
            List<List<String>> out = new ArrayList<>();
            for (List<UUID> party : parties) {
                out.add(party.stream().map(UUID::toString).toList());
            }
            Files.writeString(file(server), new Gson().toJson(out));
        } catch (Exception ignored) {
            // The parties just won't outlive a restart.
        }
    }

    private static void started(ServerStartedEvent event) {
        parties.clear();
        invites.clear();
        Path path = file(event.getServer());
        try {
            if (Files.exists(path)) {
                JsonArray all = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
                all.forEach(party -> {
                    List<UUID> ids = new ArrayList<>();
                    party.getAsJsonArray().forEach(id -> ids.add(UUID.fromString(id.getAsString())));
                    if (ids.size() > 1) {
                        parties.add(ids);
                    }
                });
            }
        } catch (Exception ignored) {
            parties.clear();
        }
    }
}
