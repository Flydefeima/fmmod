package com.feima.movemod.action;

import com.feima.movemod.client.SlideClientHelper;
import com.feima.movemod.config.MoveConfig;
import com.feima.movemod.network.NetworkHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞扑：疾跑中按趴下键触发。
 *
 * <p>给一次向上初速，然后水平冲出一段距离，pose 强制为
 * {@link Pose#SWIMMING}（趴在地上），手臂姿态由
 * {@code ProneArmPoseMixin} 的 dive 分支驱动。
 *
 * <p><b>耐力</b>：启动时若耐力不足 {@code stamina.dive.costOnStart}
 * 直接拒绝；启动时一次性扣除。期间每 tick 扣除
 * {@code stamina.dive.costPerTick}，但<b>不因中途耗尽而中断</b>——
 * 飞扑是启动即付款的一次性动作，付完就该按物理曲线跑完，中途耗尽
 * 只是停止继续扣。
 *
 * <p><b>客户端预测回滚</b>：{@code commitStart} 会保存速度与疾跑状态
 * 快照。若服务端拒绝本地预测（通常是耐力同步偏差），客户端用快照
 * 回滚副作用，避免「飞出去一小段但无动画」。
 */
public final class DiveAction {

    public static final DiveAction INSTANCE = new DiveAction();

    private final Map<UUID, State> serverStates = new ConcurrentHashMap<>();
    private final Map<UUID, State> clientStates = new ConcurrentHashMap<>();

    private DiveAction() {}

    private Map<UUID, State> states(Player p) {
        return p.level().isClientSide ? clientStates : serverStates;
    }

    public boolean isDiving(Player player) {
        return states(player).containsKey(player.getUUID());
    }

    // ============================================================
    // 启动
    // ============================================================
    public boolean tryStart(Player player) {
        if (player.level().isClientSide) return false;
        if (serverStates.containsKey(player.getUUID())) return false;
        if (!canDive(player)) return false;
        commitStart(player);
        return true;
    }

    public boolean tryStartClient(Player player) {
        if (!player.level().isClientSide) return false;
        if (clientStates.containsKey(player.getUUID())) return false;
        if (!canDive(player)) return false;
        commitStart(player);
        return true;
    }

    public boolean canDive(Player player) {
        if (!passiveAllowed(player)) return false;
        if (isDiving(player)) return false;
        if (ActionExclusivity.isAnyOtherActive(player, ActionExclusivity.Action.DIVE)) {
            return false;
        }

        if (!player.onGround()) return false;
        if (MoveConfig.INSTANCE.diveRequireSprint.get() && !player.isSprinting()) {
            return false;
        }

        if (!StaminaTracker.INSTANCE.hasEnough(player,
                MoveConfig.INSTANCE.staminaDiveCostOnStart.get())) {
            return false;
        }

        return true;
    }

    private boolean passiveAllowed(Player player) {
        if (!MoveConfig.INSTANCE.proneEnabled.get()) return false;
        if (!MoveConfig.INSTANCE.diveEnabled.get()) return false;
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        return true;
    }

    private void commitStart(Player player) {
        float yaw = player.getYRot();
        Vec3 dir = yawToHorizontal(yaw);
        double hSpeed = MoveConfig.INSTANCE.diveHorizontalSpeed.get();

        // 保存快照，供客户端 reject 回滚使用
        Vec3 preMotion = player.getDeltaMovement();
        boolean preSprinting = player.isSprinting();

        double up = MoveConfig.INSTANCE.diveUpBoost.get();
        player.setDeltaMovement(dir.x * hSpeed, up, dir.z * hSpeed);
        player.setSprinting(false);
        player.hasImpulse = true;

        StaminaTracker.INSTANCE.consumeUpTo(player,
                MoveConfig.INSTANCE.staminaDiveCostOnStart.get());

        states(player).put(player.getUUID(),
                new State(dir, hSpeed, preMotion, preSprinting));

        if (!player.level().isClientSide) {
            NetworkHandler.broadcastDiveState(player, true);
        }
        player.refreshDimensions();
    }

    // ============================================================
    // Tick
    // ============================================================
    public void tick(Player player) {
        if (player.level().isClientSide && !SlideClientHelper.isLocalPlayer(player)) {
            return;
        }

        State s = states(player).get(player.getUUID());
        if (s == null) return;

        s.ticks++;

        if (!passiveAllowed(player)) {
            finishDive(player);
            return;
        }

        // 每 tick 扣耐力，但不再因此中断飞扑
        if (MoveConfig.INSTANCE.staminaEnabled.get()) {
            double perTick = MoveConfig.INSTANCE.staminaDiveCostPerTick.get();
            if (perTick > 0.0D) {
                StaminaTracker.INSTANCE.consumeUpTo(player, perTick);
            }
        }

        if (player.horizontalCollision && s.ticks > 2) {
            finishDive(player);
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(
                s.direction.x * s.horizontalSpeed,
                motion.y,
                s.direction.z * s.horizontalSpeed
        );

        s.horizontalSpeed *= MoveConfig.INSTANCE.diveFriction.get();

        if (s.horizontalSpeed <= MoveConfig.INSTANCE.diveEndSpeed.get()) {
            finishDive(player);
        }
    }

    private void finishDive(Player player) {
        stop(player);

        if (!MoveConfig.INSTANCE.diveConvertToProneOnLand.get()) return;
        if (!player.onGround()) return;

        if (player.level().isClientSide) {
            ProneAction.INSTANCE.tryStartClient(player);
        } else {
            if (ProneAction.INSTANCE.tryStart(player)) {
                NetworkHandler.broadcastProneState(player, true);
            }
        }
    }

    // ============================================================
    // 停止 / 同步
    // ============================================================
    public void stop(Player player) {
        if (states(player).remove(player.getUUID()) == null) return;
        player.refreshDimensions();
        if (!player.level().isClientSide) {
            NetworkHandler.broadcastDiveState(player, false);
        }
    }

    public void applyRemoteState(Player player, boolean diving, boolean rejected) {
        if (!player.level().isClientSide) return;

        if (SlideClientHelper.isLocalPlayer(player)) {
            if (rejected || !diving) {
                State s = clientStates.remove(player.getUUID());
                if (s != null) {
                    if (rejected) {
                        // 服务端否决了本地预测 → 回滚 commitStart 的副作用，
                        // 避免「飞出去一小段但无动画」。
                        player.setDeltaMovement(s.preDiveMotion);
                        if (s.preDiveSprinting && !player.isSprinting()) {
                            player.setSprinting(true);
                        }
                    }
                    player.refreshDimensions();
                }
            }
            return;
        }

        boolean changed;
        if (diving) {
            changed = clientStates.put(player.getUUID(),
                    new State(yawToHorizontal(player.getYRot()), 0.0D)) == null;
            if (changed) {
                ActionExclusivity.stopOthers(player, ActionExclusivity.Action.DIVE);
            }
        } else {
            changed = clientStates.remove(player.getUUID()) != null;
        }
        if (changed) player.refreshDimensions();
    }

    public boolean forcePose(Player player) {
        if (!isDiving(player)) return false;
        if (!MoveConfig.INSTANCE.proneEnabled.get()) return false;
        if (!MoveConfig.INSTANCE.diveEnabled.get()) return false;

        if (player.getPose() != Pose.SWIMMING) {
            player.setPose(Pose.SWIMMING);
            player.refreshDimensions();
        }
        return true;
    }

    public void forget(UUID id) {
        serverStates.remove(id);
        clientStates.remove(id);
    }

    private static Vec3 yawToHorizontal(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    private static final class State {
        final Vec3 direction;
        double horizontalSpeed;
        int ticks;
        /** 客户端预测回滚快照：启动前的速度。 */
        final Vec3 preDiveMotion;
        /** 客户端预测回滚快照：启动前是否疾跑。 */
        final boolean preDiveSprinting;

        State(Vec3 dir, double speed, Vec3 preMotion, boolean preSprinting) {
            this.direction = dir;
            this.horizontalSpeed = speed;
            this.preDiveMotion = preMotion;
            this.preDiveSprinting = preSprinting;
        }

        /** 远端玩家 / 无回滚需求场景的简化构造。 */
        State(Vec3 dir, double speed) {
            this(dir, speed, new Vec3(0.0, 0.0, 0.0), false);
        }
    }
}