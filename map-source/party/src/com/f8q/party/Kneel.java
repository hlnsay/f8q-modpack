package com.f8q.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Down on one knee over a friend, hands on their chest. Played on top of whatever Punchy Moves
 * has posed, because its emotes only ever play on your own player, and everyone must see this.
 */
public final class Kneel {
    private static final float SECONDS_IN = 0.45F;
    private static final Map<UUID, UUID> helping = new HashMap<>();
    private static final Map<UUID, float[]> weights = new HashMap<>();

    private Kneel() {
    }

    static void helpers(Map<UUID, UUID> now) {
        helping.clear();
        helping.putAll(now);
    }

    /** Turns those kneeling to face who they're lifting. */
    static void tick(Minecraft mc) {
        for (Map.Entry<UUID, UUID> pair : helping.entrySet()) {
            Player helper = mc.level.getPlayerByUUID(pair.getKey());
            Player target = mc.level.getPlayerByUUID(pair.getValue());
            if (helper != null && target != null) {
                double dx = target.getX() - helper.getX();
                double dz = target.getZ() - helper.getZ();
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
                helper.yBodyRot = helper.yBodyRotO + Mth.wrapDegrees(yaw - helper.yBodyRotO) * 0.25F;
            }
        }
    }

    private static void move(ModelPart part, float weight, float y, float z, float xRot, float yRot, float zRot) {
        part.y = Mth.lerp(weight, part.y, y);
        part.z = Mth.lerp(weight, part.z, z);
        part.xRot = Mth.lerp(weight, part.xRot, xRot);
        part.yRot = Mth.lerp(weight, part.yRot, yRot);
        part.zRot = Mth.lerp(weight, part.zRot, zRot);
    }

    public static void apply(AbstractClientPlayer player, PlayerModel<?> model) {
        boolean wanted = helping.containsKey(player.getUUID());
        float[] state = weights.get(player.getUUID());
        if (state == null) {
            if (!wanted) {
                return;
            }
            state = new float[] {0F, System.nanoTime() / 1.0E9F};
            weights.put(player.getUUID(), state);
        }
        float now = System.nanoTime() / 1.0E9F;
        float step = Math.min(0.1F, now - state[1]) / SECONDS_IN;
        state[1] = now;
        state[0] = Mth.clamp(state[0] + (wanted ? step : -step), 0F, 1F);
        if (state[0] <= 0F) {
            weights.remove(player.getUUID());
            return;
        }
        float w = state[0] * state[0] * (3F - 2F * state[0]);
        // A slow press on the chest while the lift goes on.
        float press = Mth.sin(player.tickCount * 0.35F) * 0.07F;
        float drop = 8.5F;
        move(model.body, w, 3.2F + drop, 0F, 0.55F, 0F, 0F);
        move(model.head, w, 4.2F + drop, -2F, 0.5F, model.head.yRot, 0F);
        move(model.leftLeg, w, 12.2F + drop, 5F, -1.45F, -0.12F, -0.05F);
        move(model.rightLeg, w, 12.2F + drop, 3.6F, 0.95F, 0.08F, 0.05F);
        move(model.leftArm, w, 5.2F + drop + press * 6F, -1.5F, -1.05F + press, 0.32F, 0F);
        move(model.rightArm, w, 5.2F + drop + press * 6F, -1.5F, -1.05F + press, -0.32F, 0F);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
    }
}
