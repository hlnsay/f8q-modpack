package com.f8q.party;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import com.velorise.simplemap.client.MapManager;
import com.velorise.simplemap.client.TeamMarks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import team.creative.playerrevive.server.PlayerReviveServer;

/** The party as a player sees it: the menu on a friend, the invite, the top-left list, the diamonds. */
@Mod(value = "f8qparty", dist = Dist.CLIENT)
public final class PartyClient {
    private static final int[] COLORS = {0x3F8CFF, 0xE5383B, 0x4FD06A};
    private static final int NEAR = 12;
    private static final int BAR = 62;
    private static final String[] SKULL = {
        ".XXXXX.",
        "XXXXXXX",
        "X..X..X",
        "X..X..X",
        "XXX.XXX",
        ".XXXXX.",
        ".X.X.X."};
    private static final KeyMapping ACTION = new KeyMapping("Пати: пригласить и принять", InputConstants.KEY_GRAVE, "F8Q");
    private static final KeyMapping DECLINE = new KeyMapping("Пати: отклонить", InputConstants.KEY_BACKSPACE, "F8Q");
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Map<UUID, ResourceLocation> GRAY = new HashMap<>();
    private static final UUID NOBODY = new UUID(0, 0);
    private static final ResourceLocation[] ARROWS = {
        ResourceLocation.fromNamespaceAndPath("f8qparty", "textures/gui/arrow_blue.png"),
        ResourceLocation.fromNamespaceAndPath("f8qparty", "textures/gui/arrow_red.png"),
        ResourceLocation.fromNamespaceAndPath("f8qparty", "textures/gui/arrow_green.png")};
    private static final ResourceLocation KEY_YO = ResourceLocation.fromNamespaceAndPath("f8qparty", "textures/gui/key_yo.png");
    private static final ResourceLocation KEY_TILDE = ResourceLocation.fromNamespaceAndPath("f8qparty", "textures/gui/key_tilde.png");
    private static final Map<UUID, List<Net.Spot>> theirs = new HashMap<>();
    private static final Map<UUID, Integer> seen = new HashMap<>();
    private static boolean viewKnown;
    private static List<Net.Mate> mates = new ArrayList<>();
    private static Player hovered;
    private static String inviter;
    private static int inviteLeft;
    private static int inviteTotal = 1;
    private static int ticks;

    public PartyClient(IEventBus bus) {
        bus.addListener(PartyClient::keys);
        NeoForge.EVENT_BUS.addListener(PartyClient::tick);
        NeoForge.EVENT_BUS.addListener(PartyClient::level);
        NeoForge.EVENT_BUS.addListener(PartyClient::hud);
        NeoForge.EVENT_BUS.addListener(PartyClient::press);
        NeoForge.EVENT_BUS.addListener(PartyClient::left);
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(ACTION);
        event.register(DECLINE);
    }

    static void offer(Net.Offer offer) {
        inviter = offer.from();
        inviteLeft = offer.ticks();
        inviteTotal = Math.max(1, offer.ticks());
    }

    static void helpers(Net.Helpers helpers) {
        Kneel.helpers(helpers.pairs());
    }

    static void state(Net.State state) {
        mates = state.mates();
    }

    private static void left(ClientPlayerNetworkEvent.LoggingOut event) {
        TeamMarks.setLocations("", List.of());
        TeamMarks.setMates(List.of());
        TeamMarks.set(List.of());
        mates = new ArrayList<>();
        inviter = null;
    }

    /** Whether this player is in the viewer's party: only they may be lifted. */
    public static boolean isMate(UUID id) {
        return mates.stream().anyMatch(mate -> mate.id().equals(id));
    }

    public static Player hovered() {
        return hovered;
    }

    private static boolean downed(Entity entity) {
        return entity instanceof Player player && PlayerReviveServer.getBleeding(player).isBleeding();
    }

    private static void send(int kind, UUID target) {
        PacketDistributor.sendToServer(new Net.Action(kind, target));
    }

    private static void tick(ClientTickEvent.Post event) {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (inviteLeft > 0 && --inviteLeft == 0) {
            inviter = null;
        }
        if (me != null && mc.level != null) {
            shareMarks(mc);
            placeMates(mc, me);
        }
        if (me == null || mc.level == null) {
            hovered = null;
            return;
        }
        Kneel.tick(mc);
        hovered = mc.screen == null ? pick(mc, me) : null;
        while (ACTION.consumeClick()) {
            if (inviter != null) {
                send(Net.ACCEPT, NOBODY);
                inviter = null;
            } else if (hovered != null) {
                if (isMate(hovered.getUUID())) {
                    send(Net.LEAVE, NOBODY);
                } else if (mates.size() < F8qParty.LIMIT) {
                    send(Net.INVITE, hovered.getUUID());
                }
            }
        }
        while (DECLINE.consumeClick()) {
            if (inviter != null) {
                send(Net.DECLINE, NOBODY);
                inviter = null;
            }
        }
    }

    /** The player under the crosshair, further than arm's reach but not through a wall. */
    private static Player pick(Minecraft mc, LocalPlayer me) {
        Vec3 eye = me.getEyePosition();
        Vec3 end = eye.add(me.getViewVector(1F).scale(NEAR));
        AABB box = me.getBoundingBox().expandTowards(me.getViewVector(1F).scale(NEAR)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(me, eye, end, box, entity -> entity instanceof Player && entity != me && !entity.isSpectator(), NEAR * NEAR);
        if (hit == null) {
            return null;
        }
        HitResult block = me.pick(NEAR, 1F, false);
        if (block instanceof BlockHitResult && block.getType() != HitResult.Type.MISS
                && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) {
            return null;
        }
        return (Player) hit.getEntity();
    }

    /** A left click on a downed friend means help: no hitting them by accident. */
    private static void press(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.hitResult instanceof EntityHitResult hit && downed(hit.getEntity()) && isMate(hit.getEntity().getUUID())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            mc.gameMode.interact(mc.player, hit.getEntity(), InteractionHand.MAIN_HAND);
        }
    }

    private static void level(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            VIEW.set(event.getModelViewMatrix());
            PROJECTION.set(event.getProjectionMatrix());
            viewKnown = true;
        }
    }

    private static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Font font = mc.font;
        worldMarks(mc, g, font, me);
        int y = 6;
        for (Net.Mate mate : mates) {
            if (!mate.id().equals(me.getUUID())) {
                row(mc, g, font, me, mate, 6, y);
                y += 26;
                diamond(mc, g, me, mate, partial);
            }
        }
        if (hovered != null && !downed(hovered)) {
            menu(mc, g, font, me, partial);
        }
        if (inviter != null) {
            invite(g, font);
        }
    }

    static void unknowns(Net.Unknowns unknowns) {
        List<TeamMarks.Location> places = new ArrayList<>();
        for (Net.Place p : unknowns.places()) {
            if (Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()))
                places.add(new TeamMarks.Location(p.id(), p.structure(), p.x(), p.y(), p.z(), p.kind(), p.open()));
        }
        TeamMarks.setLocations(unknowns.dimension(), places);
    }

    static void marks(Net.MarksDown down) {
        theirs.put(down.owner(), down.spots());
        seen.put(down.owner(), ticks);
        publish();
    }

    /** Teammates' marks in their colours; whoever left the party or fell silent drops out. */
    private static void publish() {
        List<TeamMarks.Mark> all = new ArrayList<>();
        for (Net.Mate mate : mates) {
            List<Net.Spot> spots = theirs.get(mate.id());
            Integer when = seen.get(mate.id());
            if (spots != null && when != null && ticks - when < 300) {
                for (Net.Spot spot : spots) {
                    all.add(new TeamMarks.Mark(mate.name(), COLORS[mate.color() % 3], spot.label(), spot.x(), spot.y(), spot.z(), spot.dimension(), spot.pin()));
                }
            }
        }
        TeamMarks.set(all);
    }

    /** Where teammates stand, for both maps. */
    private static void placeMates(Minecraft mc, LocalPlayer me) {
        List<TeamMarks.Mate> out = new ArrayList<>();
        for (Net.Mate mate : mates) {
            if (mate.online() && !mate.id().equals(me.getUUID())) {
                Player seen = mc.level.getPlayerByUUID(mate.id());
                out.add(new TeamMarks.Mate(mate.name(), ARROWS[mate.color() % 3], seen != null ? seen.getX() : mate.x(), seen != null ? seen.getZ() : mate.z(),
                    seen != null ? seen.getYRot() : mate.yaw(), mate.dimension()));
            }
        }
        TeamMarks.setMates(out);
    }

    private static void shareMarks(Minecraft mc) {
        if (ticks % 100 != 0) {
            return;
        }
        publish();
        if (mates.size() < 2 || mc.getConnection() == null) {
            return;
        }
        String dimension = MapManager.getInstance().getLiveDimensionResourceId();
        List<Net.Spot> spots = new ArrayList<>();
        for (TeamMarks.Mark mark : TeamMarks.own(dimension)) {
            if (spots.size() < 48) {
                spots.add(new Net.Spot(mark.pin(), mark.label(), mark.x(), mark.y(), mark.z(), dimension));
            }
        }
        PacketDistributor.sendToServer(new Net.MarksUp(spots));
    }

    /** A mark drawn over the world: where it lies, what it is and how far away. */
    private static void worldMark(Minecraft mc, GuiGraphics g, Font font, LocalPlayer me, TeamMarks.Mark mark) {
        double y = mark.y();
        if (Double.isNaN(y)) {
            int bx = (int) Math.floor(mark.x());
            int bz = (int) Math.floor(mark.z());
            y = mc.level.hasChunk(bx >> 4, bz >> 4) ? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) : me.getY();
        }
        double dx = mark.x() - me.getX();
        double dz = mark.z() - me.getZ();
        int metres = (int) Math.sqrt(dx * dx + dz * dz);
        if (metres < 3) {
            return;
        }
        int[] at = project(mc, g, new Vec3(mark.x(), y + 1.5, mark.z()));
        if (at == null || at[0] < 0 || at[1] < 0 || at[0] > g.guiWidth() || at[1] > g.guiHeight()) {
            return;
        }
        boolean own = mark.owner().isEmpty();
        TeamMarks.cross(g, at[0], at[1], 4, mark.color(), own);
        int y2 = at[1] + 6;
        if (!(own && mark.pin())) {
            box(g, font, mark.title(), at[0], y2, own ? 0xFFFFFFFF : 0xFF000000 | mark.color());
            y2 += 11;
        }
        box(g, font, metres + " метров", at[0], y2, 0xFFFFFFFF);
    }

    private static void box(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int half = font.width(text) / 2;
        g.fill(x - half - 2, y, x + half + 2, y + 10, 0xB0000000);
        g.drawString(font, text, x - half, y + 1, color, false);
    }

    private static void worldMarks(Minecraft mc, GuiGraphics g, Font font, LocalPlayer me) {
        TeamMarks.load();
        if (!TeamMarks.showInWorld) {
            return;
        }
        String dimension = MapManager.getInstance().getLiveDimensionResourceId();
        TeamMarks.own(dimension).forEach(mark -> worldMark(mc, g, font, me, mark));
        TeamMarks.visible(dimension).forEach(mark -> worldMark(mc, g, font, me, mark));
    }

    private static int[] project(Minecraft mc, GuiGraphics g, Vec3 point) {
        if (!viewKnown) {
            return null;
        }
        Vec3 rel = point.subtract(mc.gameRenderer.getMainCamera().getPosition());
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1);
        VIEW.transform(clip);
        PROJECTION.transform(clip);
        if (clip.w <= 0.05F) {
            return null;
        }
        return new int[] {Math.round((clip.x / clip.w + 1) / 2 * g.guiWidth()), Math.round((1 - clip.y / clip.w) / 2 * g.guiHeight())};
    }

    private static Vec3 place(Minecraft mc, Net.Mate mate, float partial) {
        Player seen = mc.level.getPlayerByUUID(mate.id());
        return seen != null ? seen.getPosition(partial) : new Vec3(mate.x(), mate.y(), mate.z());
    }

    private static boolean sameWorld(Minecraft mc, Net.Mate mate) {
        return mate.online() && mate.dimension().equals(mc.level.dimension().location().toString());
    }

    private static void diamond(Minecraft mc, GuiGraphics g, LocalPlayer me, Net.Mate mate, float partial) {
        if (!sameWorld(mc, mate)) {
            return;
        }
        Player seen = mc.level.getPlayerByUUID(mate.id());
        double height = seen != null ? seen.getBbHeight() + 0.95 : 2.75;
        int[] at = project(mc, g, place(mc, mate, partial).add(0, height, 0));
        if (at == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(at[0], at[1], 0);
        g.pose().scale(0.75F, -0.75F, 1F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(ARROWS[mate.color() % 3], -7, -8, 14, 17, 0F, 0F, 14, 17, 14, 17);
        g.pose().popPose();
    }

    private static int mix(int a, int b, float t) {
        int r = Mth.lerpInt(t, a >> 16 & 255, b >> 16 & 255);
        int g = Mth.lerpInt(t, a >> 8 & 255, b >> 8 & 255);
        int c = Mth.lerpInt(t, a & 255, b & 255);
        return r << 16 | g << 8 | c;
    }

    private static void row(Minecraft mc, GuiGraphics g, Font font, LocalPlayer me, Net.Mate mate, int x, int y) {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mate.id());
        boolean out = mate.downed() >= 0;
        if (info != null) {
            if (out || !mate.online()) {
                ResourceLocation gray = gray(mc, mate.id(), info.getSkin().texture());
                if (gray != null) {
                    g.blit(gray, x, y, 16, 16, 0F, 0F, 8, 8, 8, 8);
                }
            } else {
                PlayerFaceRenderer.draw(g, info.getSkin(), x, y, 16);
            }
        } else {
            g.fill(x, y, x + 16, y + 16, 0xFF2A2A2A);
        }
        g.fill(x - 1, y - 1, x + 17, y, 0xC0000000);
        g.fill(x - 1, y + 16, x + 17, y + 17, 0xC0000000);
        g.fill(x - 1, y, x, y + 16, 0xC0000000);
        g.fill(x + 16, y, x + 17, y + 16, 0xC0000000);
        if (out && ticks % 20 < 12) {
            skull(g, x + 4, y - 3);
        }
        int color = COLORS[mate.color() % 3];
        int bx = x + 21;
        g.fill(bx, y + 3, bx + 3, y + 8, 0xFF000000 | color);
        String name = mate.name().isEmpty() ? "Игрок" : mate.name();
        g.pose().pushPose();
        g.pose().translate(bx + 6, y + 1, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, name, 0, 0, out ? 0xFFFF7A66 : mate.online() ? 0xFFF2EFE9 : 0xFF8C8578, true);
        g.pose().popPose();
        bar(g, mate, bx, y + 9);
        String line = distance(mc, me, mate);
        if (out) {
            int seconds = mate.downed() / 20;
            line += "  " + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        }
        g.pose().pushPose();
        g.pose().translate(bx, y + 15, 0);
        g.pose().scale(0.6F, 0.6F, 1F);
        g.drawString(font, line, 0, 0, out ? 0xFFFF7A66 : 0xFFC9C2B8, true);
        g.pose().popPose();
    }

    private static String distance(Minecraft mc, LocalPlayer me, Net.Mate mate) {
        if (!mate.online()) {
            return "не в сети  " + mate.back() / 60 + ":" + (mate.back() % 60 < 10 ? "0" : "") + mate.back() % 60;
        }
        if (!sameWorld(mc, mate)) {
            return "другой мир";
        }
        double d = me.position().distanceTo(place(mc, mate, 1F));
        return String.format(Locale.ROOT, d < 100 ? "%.1fm" : "%.0fm", d).replace('.', ',');
    }

    /** Thin and notched like the experience bar, but red, tinted by what's hurting them, gold on top. */
    private static void bar(GuiGraphics g, Net.Mate mate, int x, int y) {
        g.fill(x - 1, y - 1, x + BAR + 1, y + 4, 0xFF0C0505);
        g.fill(x, y, x + BAR, y + 3, 0xFF3A1214);
        float max = Math.max(1, mate.max());
        int fill = Math.round(BAR * Mth.clamp(mate.health() / max, 0, 1));
        int color = mate.effect() == 2 ? 0xFF4A4A4A : mate.effect() == 1 ? 0xFF4FC23A : 0xFFE0262F;
        int top = mate.effect() == 2 ? 0xFF7A7A7A : mate.effect() == 1 ? 0xFF9AE887 : 0xFFFF6A5E;
        g.fill(x, y, x + fill, y + 3, color);
        g.fill(x, y, x + fill, y + 1, top);
        if (mate.extra() > 0) {
            int gold = Math.round(BAR * Mth.clamp(mate.extra() / max, 0, 1));
            g.fill(x, y, x + gold, y + 3, 0xFFF0C030);
            g.fill(x, y, x + gold, y + 1, 0xFFFFE9A0);
        }
        for (int notch = 1; notch < 10; notch++) {
            int at = x + BAR * notch / 10;
            g.fill(at, y, at + 1, y + 3, 0x70000000);
        }
    }

    private static void skull(GuiGraphics g, int x, int y) {
        for (int row = 0; row < SKULL.length; row++) {
            for (int col = 0; col < SKULL[row].length(); col++) {
                if (SKULL[row].charAt(col) == 'X') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, 0xFFF2EFE9);
                }
            }
        }
    }

    /** A black-and-white copy of the face, read from the skin once. */
    private static ResourceLocation gray(Minecraft mc, UUID id, ResourceLocation skin) {
        ResourceLocation known = GRAY.get(id);
        if (known != null) {
            return known;
        }
        AbstractTexture texture = mc.getTextureManager().getTexture(skin);
        RenderSystem.bindTexture(texture.getId());
        try (NativeImage full = new NativeImage(64, 64, false)) {
            full.downloadTexture(0, false);
            NativeImage face = new NativeImage(8, 8, true);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int hat = full.getPixelRGBA(40 + x, 8 + y);
                    int pixel = (hat >>> 24) > 128 ? hat : full.getPixelRGBA(8 + x, 8 + y);
                    int tone = ((pixel & 255) * 3 + (pixel >> 8 & 255) * 6 + (pixel >> 16 & 255)) / 10;
                    face.setPixelRGBA(x, y, 0xFF000000 | tone << 16 | tone << 8 | tone);
                }
            }
            ResourceLocation made = ResourceLocation.fromNamespaceAndPath("f8qparty", "gray/" + id);
            mc.getTextureManager().register(made, new DynamicTexture(face));
            GRAY.put(id, made);
            return made;
        }
    }

    /** The party key: its picture when it sits on the Ё / ~ key, a plain cap otherwise. */
    private static int actionKey(GuiGraphics g, Font font, int x, int y) {
        if (!"key.keyboard.grave.accent".equals(ACTION.saveString())) {
            return keyCap(g, font, ACTION.getTranslatedKeyMessage(), x, y);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean russian = ACTION.getTranslatedKeyMessage().getString().toLowerCase(java.util.Locale.ROOT).contains("ё");
        g.blit(russian ? KEY_YO : KEY_TILDE, x, y - 1, 12, 12, 0F, 0F, 32, 32, 32, 32);
        return x + 12;
    }

    private static int keyCap(GuiGraphics g, Font font, Component text, int x, int y) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 11, 0xFF6E6358);
        g.fill(x, y, x + w, y + 10, 0xFFEFE6D8);
        g.drawString(font, text, x + 3, y + 2, 0xFF2A1B14, false);
        return x + w;
    }

    private static void menu(Minecraft mc, GuiGraphics g, Font font, LocalPlayer me, float partial) {
        boolean mate = isMate(hovered.getUUID());
        if (mates.size() >= F8qParty.LIMIT && !mate) {
            return;
        }
        int[] at = project(mc, g, hovered.getPosition(partial).add(0, hovered.getBbHeight() * 0.7, 0));
        if (at == null) {
            return;
        }
        String title = hovered.getGameProfile().getName();
        String act = mate ? "Выйти из пати" : "Пригласить в пати";
        Component key = ACTION.getTranslatedKeyMessage();
        int w = Math.max(font.width(title), font.width(act) + font.width(key) + 10) + 14;
        int x = Mth.clamp(at[0] + 18, 4, g.guiWidth() - w - 4);
        int y = Mth.clamp(at[1] - 14, 4, g.guiHeight() - 40);
        g.fill(x, y, x + w, y + 30, 0xE6090909);
        g.fill(x, y, x + w, y + 1, 0xFFF2EFE9);
        g.fill(x, y + 29, x + w, y + 30, 0xFF3A3A3A);
        g.fill(x, y + 1, x + 1, y + 29, 0xFF3A3A3A);
        g.fill(x + w - 1, y + 1, x + w, y + 29, 0xFF3A3A3A);
        g.drawString(font, title, x + 7, y + 4, 0xFFFFFFFF, true);
        int next = actionKey(g, font, x + 7, y + 16);
        g.drawString(font, act, next + 4, y + 18, 0xFFD9CBA0, true);
    }

    private static void invite(GuiGraphics g, Font font) {
        String text = inviter + " зовёт в пати";
        int w = Math.max(font.width(text) + 20, font.width("ПринятьОтклонить") + font.width(ACTION.getTranslatedKeyMessage()) + font.width(DECLINE.getTranslatedKeyMessage()) + 56);
        int x = (g.guiWidth() - w) / 2;
        int y = 22;
        g.fill(x, y, x + w, y + 36, 0xEE090909);
        g.fill(x, y, x + w, y + 1, 0xFFF2EFE9);
        g.fill(x, y + 35, x + w, y + 36, 0xFF3A3A3A);
        g.fill(x, y + 1, x + 1, y + 35, 0xFF3A3A3A);
        g.fill(x + w - 1, y + 1, x + w, y + 35, 0xFF3A3A3A);
        g.drawCenteredString(font, text, x + w / 2, y + 5, 0xFFFFFFFF);
        int next = actionKey(g, font, x + 10, y + 18);
        g.drawString(font, "Принять", next + 4, y + 20, 0xFF7CF09A, true);
        Component no = DECLINE.getTranslatedKeyMessage();
        int right = x + w - 10 - font.width("Отклонить") - 4 - (font.width(no) + 6);
        next = keyCap(g, font, no, right, y + 18);
        g.drawString(font, "Отклонить", next + 4, y + 20, 0xFFFF7A66, true);
        g.fill(x + 1, y + 33, x + 1 + (w - 2) * inviteLeft / inviteTotal, y + 35, 0xFFD9CBA0);
    }
}
