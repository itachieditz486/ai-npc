package com.ravex.civilization.npc;

import com.ravex.civilization.RavexCiv;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Yassine-only combat (EXPERIMENTAL, untested). Real mechanics, no scripted movement:
 *  - Elytra sits in his chest slot; gliding is the vanilla fall-flying state (physics from the game).
 *  - Takeoff = a real jump, then glide, then a Firework Rocket entity attached to him (vanilla boost).
 *  - Steering = changing his pitch/yaw each tick (vanilla elytra physics does the rest).
 *  - Dive-smash: above the target he stops gliding and falls with the Mace (vanilla fall-distance damage bonus,
 *    applied in NpcEntity#doHurtTarget). If he would miss, he re-deploys the elytra before the ground to avoid damage.
 *  - Ground smash: a Wind Charge (taken from his inventory) fired at his feet launches him, then the Mace falls on the target.
 *  - Landing: obstacle check ahead, glide slope that flattens near the ground, glide ends on touchdown.
 *  - Rockets are rationed: max 3 per flight, 2 always kept in reserve (unless stalling), cooldown between boosts.
 */
public final class YassineCombat {
    private enum Phase { GROUND, TAKEOFF, FLYING, SMASH_FALL }

    private final NpcEntity npc;
    private Phase phase = Phase.GROUND;
    private int cooldown;
    private int rocketCooldown;
    private int rocketsThisFlight;
    private int flightTicks;
    private int phaseTicks;
    private boolean landing;
    private float desiredYaw;
    private float desiredPitch;

    public YassineCombat(NpcEntity npc) {
        this.npc = npc;
    }

    // ---- helpers --------------------------------------------------------------------------------------------------
    private boolean canFly() {
        ItemStack c = npc.getItemBySlot(EquipmentSlot.CHEST);
        return c.is(Items.ELYTRA) && c.getDamageValue() < c.getMaxDamage() - 2;
    }

    private int rockets() {
        int n = 0;
        for (int i = 0; i < npc.inventory.getContainerSize(); i++) {
            ItemStack s = npc.inventory.getItem(i);
            if (s.is(Items.FIREWORK_ROCKET)) n += s.getCount();
        }
        return n;
    }

    private boolean fireRocket(ServerLevel level) {
        for (int i = 0; i < npc.inventory.getContainerSize(); i++) {
            if (npc.inventory.getItem(i).is(Items.FIREWORK_ROCKET)) {
                ItemStack one = npc.inventory.removeItem(i, 1); // real consumption
                try {
                    level.addFreshEntity(new FireworkRocketEntity(level, one, npc));
                } catch (RuntimeException ex) {
                    RavexCiv.LOGGER.warn("Yassine firework failed: {}", ex.toString());
                    return false;
                }
                rocketsThisFlight++;
                rocketCooldown = 60;
                return true;
            }
        }
        return false;
    }

    private boolean clearAbove(ServerLevel level, int n) {
        BlockPos f = npc.blockPosition();
        for (int i = 2; i <= n; i++) {
            BlockPos p = f.above(i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    private int groundDistance(ServerLevel level) {
        BlockPos f = npc.blockPosition();
        for (int i = 1; i <= 64; i++) {
            BlockPos p = f.below(i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return i - 1;
        }
        return 64;
    }

    private double horizontalDistance(LivingEntity t) {
        double dx = t.getX() - npc.getX(), dz = t.getZ() - npc.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- main tick ------------------------------------------------------------------------------------------------
    public void tick() {
        if (cooldown > 0) cooldown--;
        if (rocketCooldown > 0) rocketCooldown--;
        if (!(npc.level() instanceof ServerLevel level)) return;
        LivingEntity t = npc.getTarget();
        boolean hasT = t != null && t.isAlive();
        switch (phase) {
            case GROUND -> groundTick(level, t, hasT);
            case TAKEOFF -> takeoffTick(level);
            case FLYING -> flyTick(level, t, hasT);
            case SMASH_FALL -> smashTick(level, t, hasT);
        }
    }

    private void groundTick(ServerLevel level, LivingEntity t, boolean hasT) {
        if (!hasT) return;
        double d = npc.distanceTo(t);

        // far target + working elytra + rockets + open sky: take off
        if (npc.onGround() && cooldown == 0 && d > 22.0 && d < 150.0 && canFly() && rockets() >= 1
                && !npc.isInWater() && npc.getHealth() > 8.0F && clearAbove(level, 14)) {
            npc.getJumpControl().jump();
            phase = Phase.TAKEOFF;
            phaseTicks = 0;
            rocketsThisFlight = 0;
            landing = false;
            return;
        }

        // ground smash: Wind Charge at the feet, then fall on the target with the Mace
        if (!npc.getMainHandItem().is(Items.MACE)) return;
        if (npc.onGround() && cooldown == 0 && d > 2.5 && d < 7.0 && npc.getHealth() > 10.0F
                && npc.consumeOne(Items.WIND_CHARGE)) {
            try {
                WindCharge wc = new WindCharge(EntityType.WIND_CHARGE, level);
                wc.setOwner(npc);
                wc.setPos(npc.getX(), npc.getY() + 0.1, npc.getZ());
                wc.setDeltaMovement(0.0, -1.0, 0.0);
                level.addFreshEntity(wc);
            } catch (RuntimeException ex) {
                RavexCiv.LOGGER.warn("Yassine wind charge failed: {}", ex.toString());
            }
            cooldown = 100;
        }
        if (!npc.onGround() && npc.fallDistance > 0.5F) {
            npc.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
            npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        }
    }

    private void takeoffTick(ServerLevel level) {
        phaseTicks++;
        if (!npc.onGround()) {
            npc.startGliding();
            desiredPitch = -40.0F;
            desiredYaw = npc.getYRot();
            fireRocket(level);
            phase = Phase.FLYING;
            flightTicks = 0;
        } else if (phaseTicks > 20) {
            phase = Phase.GROUND;
            cooldown = 200;
        }
    }

    private void flyTick(ServerLevel level, LivingEntity t, boolean hasT) {
        flightTicks++;
        if (!npc.isFallFlying()) {
            if (npc.onGround()) {
                phase = Phase.GROUND;
                cooldown = 120;
                return;
            }
            if (canFly()) npc.startGliding();
        }
        if (npc.onGround() && flightTicks > 10) {
            npc.stopGliding();
            phase = Phase.GROUND;
            cooldown = 120;
            return;
        }
        if (!canFly() || flightTicks > 900 || npc.getHealth() < 8.0F || !hasT) landing = true;

        Vec3 pos = npc.position();
        double speed = npc.getDeltaMovement().horizontalDistance();
        int gd = groundDistance(level);

        if (landing) {
            // glide slope that flattens near the ground
            desiredPitch = gd > 14 ? 28.0F : gd > 5 ? 10.0F : -6.0F;
            desiredYaw = npc.getYRot();
        } else {
            boolean mace = npc.getMainHandItem().is(Items.MACE);
            double dh = horizontalDistance(t);
            double above = npc.getY() - t.getY();
            // dive-smash: stop gliding right above the target and fall on it with the Mace
            if (mace && dh < 7.0 && above > 7.0 && gd > 8) {
                npc.stopGliding();
                phase = Phase.SMASH_FALL;
                phaseTicks = 0;
                return;
            }
            double aimY = mace ? t.getY() + 12.0 : t.getY() + 1.0;
            double dx = t.getX() - pos.x, dy = aimY - pos.y, dz = t.getZ() - pos.z;
            double hd = Math.sqrt(dx * dx + dz * dz);
            desiredYaw = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
            desiredPitch = Mth.clamp((float) (-Math.atan2(dy, Math.max(hd, 0.01)) * (180.0 / Math.PI)), -50.0F, 45.0F);
            // keep a safe altitude while approaching
            if (gd < 6 && hd > 10) desiredPitch = Math.min(desiredPitch, -15.0F);

            // ration rockets: only when slowing down and far, never more than 3, keep 2 in reserve
            boolean stalling = speed < 0.45;
            if (rocketCooldown == 0 && rocketsThisFlight < 3 && npc.distanceTo(t) > 15.0
                    && (speed < 0.9) && (rockets() > 2 || stalling) && rockets() > 0) {
                fireRocket(level);
            }
        }

        // obstacle avoidance: look ahead along the flight direction and climb if rock/tree is close
        Vec3 look = Vec3.directionFromRotation(desiredPitch, desiredYaw);
        BlockHitResult hit = level.clip(new ClipContext(npc.getEyePosition(), npc.getEyePosition().add(look.scale(14.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(npc.getEyePosition()) < 11.0) {
            desiredPitch = -40.0F;
            if (landing) desiredYaw += 35.0F; // slide sideways when landing into a wall
        }
    }

    private void smashTick(ServerLevel level, LivingEntity t, boolean hasT) {
        phaseTicks++;
        if (npc.onGround() || phaseTicks > 200) {
            phase = Phase.GROUND;
            cooldown = 60;
            return;
        }
        if (hasT) {
            npc.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
            npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        }
        // not going to connect: re-deploy the elytra before the ground to avoid fall damage
        int gd = groundDistance(level);
        if (gd <= 6 && npc.fallDistance > 5.0F && (!hasT || npc.distanceTo(t) > 4.0) && canFly()) {
            npc.startGliding();
            landing = true;
            phase = Phase.FLYING;
            flightTicks = 0;
        }
    }

    /** Called from NpcEntity#travel right before vanilla elytra physics, so pitch/yaw steer the glide. */
    public void applyRotation() {
        float yaw = Mth.approachDegrees(npc.getYRot(), desiredYaw, 12.0F);
        float pitch = Mth.approachDegrees(npc.getXRot(), desiredPitch, 10.0F);
        npc.setYRot(yaw);
        npc.setXRot(pitch);
        npc.yBodyRot = yaw;
        npc.yHeadRot = yaw;
    }
}
