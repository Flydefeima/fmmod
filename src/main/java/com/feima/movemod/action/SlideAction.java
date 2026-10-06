package com.feima.movemod.action;

import com.feima.movemod.client.SlideClientHelper;
import com.feima.movemod.config.MoveConfig;
import com.feima.movemod.mixin.PlayerSprintParticleInvoker;
import com.feima.movemod.network.NetworkHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SlideAction {

    public static final SlideAction INSTANCE = new SlideAction();

    private static final double MIN_SPEED = 1.0E-4;
    private static final int STAMINA_SYNC_INTERVAL = 20;
    private static final double SPEED_RESYNC_FACTOR = 0.5D;
    private static final double SPACE_CHECK_DIST = 0.35D;

    private final Map<UUID, State> serverStates = new ConcurrentHashMap<>();
    private final Map<UUID, State> clientStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> serverTriggerCds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clientTriggerCds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clientLastSeq = new ConcurrentHashMap<>();

    private SlideAction() {}

    private Map<UUID, State> states(Player p) {
        return p.level().isClientSide ? clientStates : serverStates;
    }

    private Map<UUID, Integer> triggerCds(Player p) {
        return p.level().isClientSide ? clientTriggerCds : serverTriggerCds;
    }

    public boolean isSliding(Player player) {
        return states(player).containsKey(player.getUUID());
    }

    public double currentSpeed(Player player) {
        State s = states(player).get(player.getUUID());
        return s == null ? 0.0D : s.speed;
    }

    // ============================================================
    // 启动
    // ============================================================
    public boolean tryStart(Player player) {
        if (player.level().isClientSide) return false;
        if (serverStates.containsKey(player.getUUID())) return false;
        if (!canStart(player)) return false;
        commitStart(player);
        return true;
    }

    public boolean tryStartClient(Player player) {
        if (!player.level().isClientSide) return false;
        if (clientStates.containsKey(player.getUUID())) return false;
        if (!canStart(player)) return false;
        commitStart(player);
        return true;
    }

    private boolean canStart(Player player) {
        if (!passiveAllowed(player)) return false;
        // 互斥：集中判定，新增动作只需在 ActionExclusivity 里加一行
        if (ActionExclusivity.isAnyOtherActive(player, ActionExclusivity.Action.SLIDE)) {
            return false;
        }
        if (!player.onGround()) return false;
        if (isTriggerOnCd(player)) return false;

        if (MoveConfig.INSTANCE.requireSprint.get() && !player.isSprinting()) return false;
        if (!hasForwardInput(player)) return false;
        if (!hasSpaceToSlide(player)) return false;

        // 耐力门槛：允许空耐力滑铲时跳过（速度会自动降档）
        if (!MoveConfig.INSTANCE.staminaSlideAllowWhenEmpty.get()
                && MoveConfig.INSTANCE.staminaEnabled.get()
                && !StaminaTracker.INSTANCE.canStart(player)) {
            return false;
        }

        return true;
    }

    private boolean passiveAllowed(Player player) {
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return false;
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        return true;
    }

    private boolean hasForwardInput(Player player) {
        if (!player.level().isClientSide) return true;
        return SlideClientHelper.hasForwardInput();
    }

    /**
     * 检查滑铲目标位置是否有足够空间。
     *
     * <p>高度用的是<b>碰撞箱顶</b>（{@code player.getY() + h}），不是眼睛。
     */
    private boolean hasSpaceToSlide(Player player) {
        double w = MoveConfig.INSTANCE.hitboxWidth.get() / 2.0D;
        double h = MoveConfig.INSTANCE.hitboxHeight.get();

        Vec3 fwd = yawToHorizontal(player.getYRot());
        double cx = player.getX() + fwd.x * SPACE_CHECK_DIST;
        double cz = player.getZ() + fwd.z * SPACE_CHECK_DIST;

        AABB box = new AABB(
                cx - w, player.getY(), cz - w,
                cx + w, player.getY() + h, cz + w
        );
        return player.level().noCollision(player, box);
    }

    private void commitStart(Player player) {
        float yaw = player.getYRot();
        boolean wasSprinting = player.isSprinting();
        player.setSprinting(false);

        boolean lowStamina = !StaminaTracker.INSTANCE.canStart(player);
        double speed;
        if (lowStamina && MoveConfig.INSTANCE.staminaEnabled.get()) {
            speed = MoveConfig.INSTANCE.staminaSlideLevel3Speed.get();
        } else {
            speed = StaminaTracker.INSTANCE.speedFor(player);
        }
        StaminaTracker.INSTANCE.consumeUpTo(
                player, MoveConfig.INSTANCE.staminaSlideCostOnStart.get());

        State state = new State(yaw, speed, wasSprinting);
        states(player).put(player.getUUID(), state);
        setTriggerCd(player);
        player.refreshDimensions();
    }

    // ============================================================
    // 滑铲跳
    // ============================================================
    public boolean trySlideJump(Player player) {
        if (player.level().isClientSide && !SlideClientHelper.isLocalPlayer(player)) {
            return false;
        }

        State state = states(player).get(player.getUUID());
        if (state == null) return false;

        Vec3 dir = resolveJumpDirection(player, state);
        double scale = StaminaTracker.INSTANCE.jumpScaleFor(player);
        double forward = MoveConfig.INSTANCE.slideJumpForward.get() * scale;
        double up      = MoveConfig.INSTANCE.slideJumpUp.get();

        double currentY = player.getDeltaMovement().y;
        double newY = Math.max(currentY, up);

        player.setDeltaMovement(dir.x * forward, newY, dir.z * forward);
        player.hasImpulse = true;
        stop(player);
        return true;
    }

    private Vec3 resolveJumpDirection(Player player, State state) {
        if (MoveConfig.INSTANCE.slideJumpFollowLook.get()) {
            return yawToHorizontal(player.getYRot());
        }
        return state.initialDirection;
    }

    // ============================================================
    // 远端同步
    // ============================================================
    public void applyRemoteState(Player player, boolean sliding, double stamina,
                                 double speed, boolean rejected, long seq) {
        if (!player.level().isClientSide) return;

        UUID id = player.getUUID();

        Long last = clientLastSeq.get(id);
        if (last != null && seq <= last) return;
        clientLastSeq.put(id, seq);

        if (SlideClientHelper.isLocalPlayer(player)) {
            if (MoveConfig.INSTANCE.staminaEnabled.get()) {
                StaminaTracker.INSTANCE.set(player, stamina);
            }

            if (sliding) {
                // 互斥：权威决定进入滑铲 → 清掉其它本地预测
                ActionExclusivity.stopOthers(player, ActionExclusivity.Action.SLIDE);

                State state = clientStates.get(id);
                if (state == null) {
                    state = new State(player.getYRot(), speed, false);
                    clientStates.put(id, state);
                    player.refreshDimensions();
                } else {
                    state.speed = Mth.lerp(SPEED_RESYNC_FACTOR, state.speed, speed);
                }
            } else {
                State removed = clientStates.remove(id);
                if (removed != null) {
                    player.refreshDimensions();
                    if (rejected && removed.wasSprinting && !player.isSprinting()) {
                        player.setSprinting(true);
                    }
                }
                if (rejected) {
                    clientTriggerCds.remove(id);
                }
            }
            return;
        }

        if (sliding) {
            if (clientStates.containsKey(id)) return;

            // 互斥：远端进入滑铲 → 清掉其它本地预测（通常为空）
            ActionExclusivity.stopOthers(player, ActionExclusivity.Action.SLIDE);

            State s = new State(player.getYRot(), 0.0D, false);
            clientStates.put(id, s);
            player.refreshDimensions();
        } else {
            if (clientStates.remove(id) != null) {
                player.refreshDimensions();
            }
        }
    }

    // ============================================================
    // Tick
    // ============================================================
    public void tick(Player player) {
        boolean isClient = player.level().isClientSide;
        boolean isLocalClient = isClient && SlideClientHelper.isLocalPlayer(player);

        if (isClient && !isLocalClient) {
            tickRemotePresentation(player);
            return;
        }

        tickTriggerCd(player);
        StaminaTracker.INSTANCE.tick(player);

        State state = states(player).get(player.getUUID());
        if (state == null) return;

        if (!passiveAllowed(player)) {
            stop(player);
            return;
        }

        state.ticks++;

        if (player.horizontalCollision) {
            stop(player);
            return;
        }

        // 每 tick 耐力消耗
        if (MoveConfig.INSTANCE.staminaEnabled.get()) {
            double perTick = MoveConfig.INSTANCE.staminaSlideCostPerTick.get();
            if (perTick > 0.0D) {
                StaminaTracker.INSTANCE.consumeUpTo(player, perTick);
                if (!MoveConfig.INSTANCE.staminaSlideAllowWhenEmpty.get()
                        && StaminaTracker.INSTANCE.get(player) <= 0.0D) {
                    stop(player);
                    return;
                }
            }
        }

        if (MoveConfig.INSTANCE.hungerEnabled.get()) {
            double exhaust = MoveConfig.INSTANCE.hungerPerTick.get();
            if (exhaust > 0.0D) {
                player.causeFoodExhaustion((float) exhaust);
            }
        }

        if (MoveConfig.INSTANCE.followLook.get()) {
            float deltaYaw   = Mth.wrapDegrees(player.getYRot() - state.initialYaw);
            double maxOffset = MoveConfig.INSTANCE.maxTurnOffset.get();
            double zeroYaw   = MoveConfig.INSTANCE.turnOffsetZeroYaw.get();
            double factor    = MoveConfig.INSTANCE.turnFactor.get();

            double targetOffset = (Math.abs(deltaYaw) > zeroYaw)
                    ? 0.0D
                    : Mth.clamp(deltaYaw * factor, -maxOffset, maxOffset);

            float targetYaw = Mth.wrapDegrees((float) (state.initialYaw + targetOffset));

            float maxStep = MoveConfig.INSTANCE.turnSpeed.get().floatValue();
            float diff = Mth.wrapDegrees(targetYaw - state.currentYaw);

            if (maxStep <= 0.0F) {
                state.currentYaw = targetYaw;
            } else {
                state.currentYaw = Mth.wrapDegrees(
                        state.currentYaw + Mth.clamp(diff, -maxStep, maxStep)
                );
            }
            state.direction = yawToHorizontal(state.currentYaw);
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(
                state.direction.x * state.speed,
                motion.y,
                state.direction.z * state.speed
        );

        if (MoveConfig.INSTANCE.slideParticle.get()
                && player.onGround()
                && player instanceof PlayerSprintParticleInvoker invoker) {
            invoker.fmm$spawnSprintParticle();
        }

        if (!isClient && state.ticks % STAMINA_SYNC_INTERVAL == 0) {
            NetworkHandler.broadcastSlideState(player, true);
        }

        int decayDelay  = MoveConfig.INSTANCE.decayDelay.get();
        double friction = MoveConfig.INSTANCE.friction.get();
        double endSpeed = MoveConfig.INSTANCE.endSpeed.get();

        if (state.ticks >= decayDelay) state.speed *= friction;

        if (state.speed <= endSpeed || state.speed < MIN_SPEED) {
            stop(player);
        }
    }

    private void tickRemotePresentation(Player player) {
        if (!MoveConfig.INSTANCE.slideParticle.get()) return;
        if (!player.onGround()) return;
        if (!clientStates.containsKey(player.getUUID())) return;
        if (player instanceof PlayerSprintParticleInvoker invoker) {
            invoker.fmm$spawnSprintParticle();
        }
    }

    // ============================================================
    // 停止
    // ============================================================
    public void stop(Player player) {
        if (states(player).remove(player.getUUID()) == null) return;
        player.refreshDimensions();
        if (!player.level().isClientSide) {
            NetworkHandler.broadcastSlideState(player, false);
        }
    }

    // ============================================================
    // 触发 CD
    // ============================================================
    private boolean isTriggerOnCd(Player player) {
        Integer cd = triggerCds(player).get(player.getUUID());
        return cd != null && cd > 0;
    }

    private void setTriggerCd(Player player) {
        int cd = MoveConfig.INSTANCE.slideTriggerCd.get();
        if (cd <= 0) {
            triggerCds(player).remove(player.getUUID());
        } else {
            triggerCds(player).put(player.getUUID(), cd);
        }
    }

    private void tickTriggerCd(Player player) {
        Map<UUID, Integer> map = triggerCds(player);
        UUID id = player.getUUID();
        Integer cd = map.get(id);
        if (cd == null || cd <= 0) return;
        map.put(id, cd - 1);
    }

    public void forget(UUID id) {
        serverStates.remove(id);
        clientStates.remove(id);
        serverTriggerCds.remove(id);
        clientTriggerCds.remove(id);
        clientLastSeq.remove(id);
    }

    private static Vec3 yawToHorizontal(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    private static final class State {
        final float initialYaw;
        final Vec3 initialDirection;
        final boolean wasSprinting;
        Vec3 direction;
        float currentYaw;
        double speed;
        int ticks;

        State(float yaw, double speed, boolean wasSprinting) {
            this.initialYaw = yaw;
            this.initialDirection = yawToHorizontal(yaw);
            this.direction = this.initialDirection;
            this.currentYaw = yaw;
            this.speed = speed;
            this.wasSprinting = wasSprinting;
        }
    }
}