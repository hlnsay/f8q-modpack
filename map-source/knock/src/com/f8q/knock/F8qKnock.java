/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.f8q.party.PartyClient
 *  com.mojang.blaze3d.platform.NativeImage
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.client.resources.sounds.SimpleSoundInstance
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.phys.EntityHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.Vec3
 *  net.neoforged.api.distmarker.Dist
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.fml.common.Mod
 *  net.neoforged.neoforge.client.event.ClientTickEvent$Post
 *  net.neoforged.neoforge.client.event.RenderGuiEvent$Post
 *  net.neoforged.neoforge.client.event.RenderLevelStageEvent
 *  net.neoforged.neoforge.client.event.RenderLevelStageEvent$Stage
 *  net.neoforged.neoforge.common.NeoForge
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Vector4f
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  team.creative.playerrevive.PlayerRevive
 *  team.creative.playerrevive.api.IBleeding
 *  team.creative.playerrevive.client.ReviveEventClient
 *  team.creative.playerrevive.server.PlayerReviveServer
 */
package com.f8q.knock;

import com.f8q.knock.Grim;
import com.f8q.party.PartyClient;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.client.ReviveEventClient;
import team.creative.playerrevive.server.PlayerReviveServer;

@Mod(value="f8qknock", dist={Dist.CLIENT})
public final class F8qKnock {
    static final Logger LOG = LoggerFactory.getLogger((String)"f8qknock");
    private static final String[] HEART = new String[]{".XX...XX.", "X++X.XXXX", "X+XXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X...."};
    private static final int FRAMES = 8;
    private static final int TW = 112;
    private static final int TH = 63;
    private static final int[][] BAYER = new int[][]{{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    private static final SoundEvent HEARTBEAT = SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath((String)"subtle_effects", (String)"entity.player.heartbeat"));
    private static final ResourceLocation[] VIGNETTE = new ResourceLocation[8];
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static boolean viewKnown;
    private static long ticks;
    private static final ResourceLocation MOUSE_LEFT;
    private static final ResourceLocation MOUSE_RIGHT;
    private static long nextBeat;
    private static long lastBeat;
    private static int giveUp;

    public F8qKnock(IEventBus iEventBus) {
        NeoForge.EVENT_BUS.addListener(F8qKnock::tick);
        NeoForge.EVENT_BUS.addListener(F8qKnock::level);
        NeoForge.EVENT_BUS.addListener(F8qKnock::hud);
        Grim.register();
    }

    public static boolean isDowned(Entity entity) {
        Player player;
        return entity instanceof Player && (player = (Player)entity).isAlive() && PlayerReviveServer.getBleeding((Player)player).isBleeding();
    }

    public static int outlineColor(Player player) {
        if (ReviveEventClient.helpActive && player.getUUID().equals(ReviveEventClient.helpTarget)) {
            return 5234810;
        }
        return F8qKnock.mix(11735580, 16734780, 0.5f + 0.5f * Mth.sin((float)((float)ticks * 0.25f)));
    }

    private static int mix(int n, int n2, float f) {
        int n3 = Mth.lerpInt((float)f, (int)(n >> 16 & 0xFF), (int)(n2 >> 16 & 0xFF));
        int n4 = Mth.lerpInt((float)f, (int)(n >> 8 & 0xFF), (int)(n2 >> 8 & 0xFF));
        int n5 = Mth.lerpInt((float)f, (int)(n & 0xFF), (int)(n2 & 0xFF));
        return n3 << 16 | n4 << 8 | n5;
    }

    private static float remaining(IBleeding iBleeding) {
        return Mth.clamp((float)((float)iBleeding.timeLeft() / (float)Math.max(1, PlayerRevive.CONFIG.bleeding.bleedTime)), (float)0.0f, (float)1.0f);
    }

    private static float revival(IBleeding iBleeding) {
        return Mth.clamp((float)(iBleeding.getProgress() / (float)PlayerRevive.CONFIG.revive.requiredReviveProgress), (float)0.0f, (float)1.0f);
    }

    private static void tick(ClientTickEvent.Post post) {
        ++ticks;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null || !F8qKnock.isDowned((Entity)localPlayer)) {
            giveUp = 0;
            nextBeat = 0L;
            return;
        }
        IBleeding iBleeding = PlayerReviveServer.getBleeding((Player)localPlayer);
        int n = giveUp = minecraft.options.keyAttack.isDown() ? giveUp + 1 : 0;
        if (ticks >= nextBeat) {
            boolean bl = iBleeding.getProgress() > 0.0f;
            float f = F8qKnock.remaining(iBleeding);
            float f2 = bl ? 0.9f : 1.15f - 0.25f * f;
            minecraft.getSoundManager().play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)HEARTBEAT, (float)f2, (float)(bl ? 0.6f : 1.0f)));
            lastBeat = ticks;
            nextBeat = ticks + (long)(bl ? 34 : Math.round(Mth.lerp((float)f, (float)13.0f, (float)30.0f)));
        }
    }

    private static void level(RenderLevelStageEvent renderLevelStageEvent) {
        if (renderLevelStageEvent.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            VIEW.set((Matrix4fc)renderLevelStageEvent.getModelViewMatrix());
            PROJECTION.set((Matrix4fc)renderLevelStageEvent.getProjectionMatrix());
            viewKnown = true;
        }
    }

    private static void hud(RenderGuiEvent.Post post) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null || minecraft.level == null) {
            return;
        }
        GuiGraphics guiGraphics = post.getGuiGraphics();
        float f = post.getPartialTick().getGameTimeDeltaPartialTick(true);
        if (F8qKnock.isDowned((Entity)localPlayer)) {
            F8qKnock.downed(minecraft, guiGraphics, localPlayer, f);
            return;
        }
        for (Player player : minecraft.level.players()) {
            if (player == localPlayer || !F8qKnock.isDowned((Entity)player) || !PartyClient.isMate((UUID)player.getUUID())) continue;
            F8qKnock.friend(minecraft, guiGraphics, localPlayer, player, f);
        }
    }

    private static float beat(float f) {
        return (float)Math.exp(-((float)ticks + f - (float)lastBeat) / 4.0f);
    }

    private static void vignette(Minecraft minecraft, GuiGraphics guiGraphics, float f, float f2) {
        int n;
        if (VIGNETTE[0] == null) {
            for (n = 0; n < 8; ++n) {
                float f3 = Mth.lerp((float)((float)n / 7.0f), (float)1.02f, (float)0.52f);
                NativeImage nativeImage = new NativeImage(112, 63, true);
                for (int i = 0; i < 63; ++i) {
                    for (int j = 0; j < 112; ++j) {
                        float f4 = ((float)j + 0.5f) / 112.0f * 2.0f - 1.0f;
                        float f5 = ((float)i + 0.5f) / 63.0f * 2.0f - 1.0f;
                        float f6 = Mth.sqrt((float)(f4 * f4 * 0.92f + f5 * f5 * 0.72f));
                        float f7 = (f6 - f3) / 0.42f;
                        boolean bl = f7 > ((float)BAYER[i & 3][j & 3] + 0.5f) / 16.0f;
                        int n2 = F8qKnock.mix(12850206, 4851467, Mth.clamp((float)(f7 - 0.35f), (float)0.0f, (float)1.0f));
                        int n3 = (bl ? 224 : 0) << 24 | (n2 & 0xFF) << 16 | n2 & 0xFF00 | n2 >> 16 & 0xFF;
                        nativeImage.setPixelRGBA(j, i, n3);
                    }
                }
                F8qKnock.VIGNETTE[n] = minecraft.getTextureManager().register("f8qknock_vignette" + n, new DynamicTexture(nativeImage));
            }
        }
        n = Mth.clamp((int)Math.round(f * 7.0f), (int)0, (int)7);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, f2);
        guiGraphics.blit(VIGNETTE[n], 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0.0f, 0.0f, 112, 63, 112, 63);
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static void downed(Minecraft minecraft, GuiGraphics guiGraphics, LocalPlayer localPlayer, float f) {
        String string;
        IBleeding iBleeding = PlayerReviveServer.getBleeding((Player)localPlayer);
        float f2 = F8qKnock.remaining(iBleeding);
        float f3 = F8qKnock.revival(iBleeding);
        boolean bl = f3 > 0.0f;
        float f4 = F8qKnock.beat(f);
        float f5 = 1.0f - f2;
        // Grim already supplies the same low-health vignette for a downed player.
        if (minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        int n = guiGraphics.guiWidth() / 2;
        int n2 = Math.min(204, guiGraphics.guiWidth() - 60) / 24 * 24;
        int n3 = n - n2 / 2;
        int n4 = guiGraphics.guiHeight() - 96;
        MutableComponent mutableComponent = Component.translatable((String)(bl ? "f8qknock.lifting" : "f8qknock.downed"));
        int n5 = bl ? 8188058 : F8qKnock.mix(15022139, 16765124, f4);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float)n, (float)(n4 - 25), 0.0f);
        guiGraphics.pose().scale(2.0f, 2.0f, 1.0f);
        guiGraphics.drawString(font, (Component)mutableComponent, -font.width((FormattedText)mutableComponent) / 2, 0, n5, true);
        guiGraphics.pose().popPose();

        int n6 = bl ? -11542406 : (f2 < 0.25f ? 0xFF000000 | F8qKnock.mix(14689839, 16757145, f4) : -2087377);
        com.f8q.party.PartyHudAssets.health(guiGraphics,n3,n4,n2,10,bl?f3:f2,0,0);
        F8qKnock.heart(guiGraphics, n3 - 29, n4 - 3, bl ? 5234810 : 15017518, 16758696, f4 > 0.45f ? 2 : 1);
        String string2 = string = bl ? Math.round(f3 * 100.0f) + "%" : F8qKnock.clock(iBleeding.timeLeft());
        guiGraphics.drawString(font, string, n3 + n2 + 8, n4 + 1, bl ? 8188058 : (f2 < 0.25f ? 16743014 : 16050656), true);
        MutableComponent mutableComponent2 = Component.translatable((String)(bl ? "f8qknock.hold_on" : "f8qknock.wait"));

        if (!bl) {
            MutableComponent mutableComponent3 = Component.translatable((String)"f8qknock.give_up");
            int n7 = 15 + font.width((FormattedText)mutableComponent3);
            int n8 = n - n7 / 2;
            F8qKnock.mouse(guiGraphics, MOUSE_LEFT, n8, n4 + 24);
            guiGraphics.drawString(font, (Component)mutableComponent3, n8 + 15, n4 + 28, 11048588, true);
            int n9 = Math.max(1, PlayerRevive.CONFIG.bleeding.giveUpSeconds);
            if (giveUp > 0) {
                float f6 = Mth.clamp((float)(((float)giveUp + f) / ((float)n9 * 20.0f)), (float)0.0f, (float)1.0f);
                guiGraphics.fill(n8, n4 + 39, n8 + n7, n4 + 41, -1609300730);
                guiGraphics.fill(n8, n4 + 39, n8 + Math.round((float)n7 * f6), n4 + 41, -1711912);
            }
        }
    }

    private static String clock(int n) {
        int n2 = Math.max(0, n) / 20;
        return n2 / 60 + ":" + (n2 % 60 < 10 ? "0" : "") + n2 % 60;
    }

    private static void frame(GuiGraphics guiGraphics, int n, int n2, int n3, int n4) {
        guiGraphics.fill(n - 1, n2 - 2, n + n3 + 1, n2 + n4 + 2, -16055034);
        guiGraphics.fill(n - 2, n2 - 1, n + n3 + 2, n2 + n4 + 1, -16055034);
        guiGraphics.fill(n, n2, n + n3, n2 + n4, -14022131);
    }

    private static void segments(GuiGraphics guiGraphics, int n, int n2, int n3, int n4, float f, boolean bl, int n5, int n6) {
        int n7 = n3 / 24;
        int n8 = Mth.ceil((float)(f * 24.0f));
        for (int i = 0; i < 24; ++i) {
            boolean bl2 = bl ? Math.abs(i * 2 - 23) < n8 : i < n8;
            if (!bl2) continue;
            int n9 = n + i * n7;
            guiGraphics.fill(n9, n2, n9 + n7 - 1, n2 + n4, n5);
            guiGraphics.fill(n9, n2 + n4 - 2, n9 + n7 - 1, n2 + n4, n6);
            guiGraphics.fill(n9, n2, n9 + n7 - 1, n2 + 1, 0x60FFFFFF);
        }
    }

    private static void heart(GuiGraphics guiGraphics, int n, int n2, int n3, int n4, int n5) {
        com.f8q.party.PartyHudAssets.heart(guiGraphics,n+9,n2+8,13);
    }

    private static void mouse(GuiGraphics guiGraphics, ResourceLocation resourceLocation, int n, int n2) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0,0,400);
        RenderSystem.setShaderColor(1,1,1,1);
        // Use the existing left-button asset directly: a negative X scale culls the quad.
        guiGraphics.blit(MOUSE_LEFT,n,n2,11,15,0f,0f,24,32,24,32);
        guiGraphics.flush();
        guiGraphics.pose().popPose();
    }

    private static int keyWidth(Font font, Component component) {
        return font.width((FormattedText)component) + 6;
    }

    private static int key(GuiGraphics guiGraphics, Font font, Component component, int n, int n2) {
        int n3 = F8qKnock.keyWidth(font, component);
        guiGraphics.fill(n, n2, n + n3, n2 + 11, -9542824);
        guiGraphics.fill(n, n2, n + n3, n2 + 10, -1055016);
        guiGraphics.fill(n + 1, n2 + 1, n + n3 - 1, n2 + 2, -1);
        guiGraphics.drawString(font, component, n + 3, n2 + 2, -14017772, false);
        return n + n3;
    }

    private static void friend(Minecraft minecraft, GuiGraphics guiGraphics, LocalPlayer localPlayer, Player player, float f) {
        EntityHitResult entityHitResult;
        boolean bl;
        if (!viewKnown) {
            return;
        }
        Font font = minecraft.font;
        Vec3 vec3 = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 vec32 = player.getPosition(f).add(0.0, 0.75, 0.0).subtract(vec3);
        Vector4f vector4f = new Vector4f((float)vec32.x, (float)vec32.y, (float)vec32.z, 1.0f);
        VIEW.transform(vector4f);
        PROJECTION.transform(vector4f);
        int n = guiGraphics.guiWidth();
        int n2 = guiGraphics.guiHeight();
        boolean bl2 = vector4f.w <= 0.0f;
        float f2 = vector4f.x / Math.abs(vector4f.w);
        float f3 = vector4f.y / Math.abs(vector4f.w);
        boolean bl3 = bl = !bl2 && Math.abs(f2) < 0.92f && Math.abs(f3) < 0.88f;
        if (!bl) {
            float f4 = 1.0f / Math.max(1.0E-4f, Math.max(Math.abs(f2) / 0.92f, Math.abs(f3) / 0.88f));
            f2 *= f4;
            f3 *= f4;
            if (bl2) {
                f3 = 0.88f * (float)(f3 > 0.0f ? 1 : -1);
            }
        }
        int n3 = Math.round((f2 + 1.0f) / 2.0f * (float)n);
        int n4 = Math.round((1.0f - f3) / 2.0f * (float)n2);
        IBleeding iBleeding = PlayerReviveServer.getBleeding((Player)player);
        float f5 = localPlayer.distanceTo((Entity)player);
        float f6 = 0.5f + 0.5f * Mth.sin((float)(((float)ticks + f) * 0.3f));
        boolean bl4 = ReviveEventClient.helpActive && player.getUUID().equals(ReviveEventClient.helpTarget);
        HitResult hitResult = minecraft.hitResult;
        boolean bl5 = hitResult instanceof EntityHitResult && (entityHitResult = (EntityHitResult)hitResult).getEntity() == player;
        int n5 = Math.max(4, n4 - 42);
        if (bl4) {
            Component message = Component.translatable((String)"f8qknock.lifting_friend", (Object[])new Object[]{player.getName()});
            int n6 = Math.max(font.width((FormattedText)message) + 16, 110);
            int n7 = Mth.clamp((int)(n3 - n6 / 2), (int)4, (int)(n - n6 - 4));
            F8qKnock.card(guiGraphics, n7, n5, n6, 26, -11542406);
            guiGraphics.drawCenteredString(font, message, n7 + n6 / 2, n5 + 4, 0xFFFFFF);
            int n8 = Math.round((float)(n6 - 12) * F8qKnock.revival(iBleeding));
            guiGraphics.fill(n7 + 6, n5 + 16, n7 + n6 - 6, n5 + 21, -16048366);
            guiGraphics.fill(n7 + 6, n5 + 16, n7 + 6 + n8, n5 + 21, -11542406);
            guiGraphics.fill(n7 + 6, n5 + 16, n7 + 6 + n8, n5 + 17, -2130706433);
            return;
        }
        if (!bl) {
            F8qKnock.heart(guiGraphics, n3 - 9, n4 - 8, 15017518, 16758696, 2);
            return;
        }
        if (bl5 && (double)f5 <= PlayerRevive.CONFIG.revive.maxDistance) {
            Component message = Component.translatable((String)"f8qknock.lift");
            int n9 = 15 + font.width((FormattedText)message);
            int n10 = n3 - n9 / 2;
            F8qKnock.mouse(guiGraphics, MOUSE_RIGHT, n10, n4 - 34);
            guiGraphics.drawString(font, message, n10 + 15, n4 - 30, 0xFFFFFF, true);
        }
    }

    private static void card(GuiGraphics guiGraphics, int n, int n2, int n3, int n4, int n5) {
        guiGraphics.fill(n + 1, n2, n + n3 - 1, n2 + n4, -434894069);
        guiGraphics.fill(n, n2 + 1, n + n3, n2 + n4 - 1, -434894069);
        guiGraphics.fill(n + 1, n2 + n4 - 1, n + n3 - 1, n2 + n4, n5);
        guiGraphics.fill(n + 1, n2, n + n3 - 1, n2 + 1, n5 & 0xFFFFFF | 0x60000000);
    }

    static {
        MOUSE_LEFT = ResourceLocation.fromNamespaceAndPath((String)"f8qknock", (String)"textures/gui/mouse_left.png");
        MOUSE_RIGHT = ResourceLocation.fromNamespaceAndPath((String)"f8qknock", (String)"textures/gui/mouse_right.png");
        lastBeat = -100L;
    }
}

