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
    private static final int SPEED_RESYNC_TICKS = 5;

    private static final double SPACE_CHECK_DIST = 0.35D;

    private final Map<UUID, State> serverStates = new ConcurrentHashMap<>();
    private final Map<UUID, State> clientStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> serverTriggerCds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clientTriggerCds = new ConcurrentHashMap<>();

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
        if (!MoveConfig.INSTANCE.enabled.get()) return false;
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return false;
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        // 与趴下互斥
        if (CrawlAction.INSTANCE.isCrawling(player)) return false;
        // 与探头互斥
        if (PeekAction.INSTANCE.isPeeking(player)) return false;
        // 触发时必须在场地上（之后的空中阶段不再检查）
        if (!player.onGround()) return false;
        if (isTriggerOnCd(player)) return false;

        if (MoveConfig.INSTANCE.requireSprint.get() && !player.isSprinting()) return false;
        if (!hasForwardInput(player)) return false;
        if (!hasSpaceToSlide(player)) return false;

        if (!MoveConfig.INSTANCE.allowWhenEmpty.get()
                && MoveConfig.INSTANCE.staminaEnabled.get()
                && !StaminaTracker.INSTANCE.canStart(player)) {
            return false;
        }

        return true;
    }

    private boolean hasForwardInput(Player player) {
        if (!player.level().isClientSide) return true;
        return SlideClientHelper.hasForwardInput();
    }

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
        player.setSprinting(false);

        boolean lowStamina = !StaminaTracker.INSTANCE.canStart(player);
        double speed;
        if (lowStamina && MoveConfig.INSTANCE.staminaEnabled.get()) {
            speed = MoveConfig.INSTANCE.staminaLevel3Speed.get();
        } else {
            speed = StaminaTracker.INSTANCE.speedFor(player);
        }
        StaminaTracker.INSTANCE.consumeUpTo(
                player, MoveConfig.INSTANCE.staminaCostOnStart.get());

        State state = new State(yaw, speed);
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
                                 double speed, boolean rejected) {
        if (!player.level().isClientSide) return;

        if (SlideClientHelper.isLocalPlayer(player)) {
            if (MoveConfig.INSTANCE.staminaEnabled.get()) {
                StaminaTracker.INSTANCE.set(player, stamina);
            }

            if (sliding) {
                State state = clientStates.get(player.getUUID());
                if (state == null) {
                    state = new State(player.getYRot(), speed);
                    clientStates.put(player.getUUID(), state);
                    player.refreshDimensions();
                } else if (state.ticks <= SPEED_RESYNC_TICKS) {
                    state.speed = speed;
                }
            } else {
                if (clientStates.remove(player.getUUID()) != null) {
                    player.refreshDimensions();
                }
                if (rejected) {
                    clientTriggerCds.remove(player.getUUID());
                }
            }
            return;
        }

        if (sliding) {
            if (clientStates.containsKey(player.getUUID())) return;
            State s = new State(player.getYRot(), 0.0D);
            clientStates.put(player.getUUID(), s);
            player.refreshDimensions();
        } else {
            if (clientStates.remove(player.getUUID()) != null) {
                player.refreshDimensions();
            }
        }
    }

    // ============================================================
    // Tick
    // ============================================================
    public void tick(Player player) {
        if (player.level().isClientSide && !SlideClientHelper.isLocalPlayer(player)) return;

        tickTriggerCd(player);
        StaminaTracker.INSTANCE.tick(player);

        State state = states(player).get(player.getUUID());
        if (state == null) return;

        state.ticks++;

        // 撞墙 = 主动结束（空中不会触发水平碰撞，所以不影响空中滑铲）
        if (player.horizontalCollision) {
            stop(player);
            return;
        }

        if (MoveConfig.INSTANCE.staminaEnabled.get()) {
            double perTick = MoveConfig.INSTANCE.staminaCostPerTick.get();
            if (perTick > 0.0D) {
                StaminaTracker.INSTANCE.consumeUpTo(player, perTick);
                if (!MoveConfig.INSTANCE.allowWhenEmpty.get()
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

            // 视角偏移超过阈值 → 转向偏移归零（滑铲方向回到初始方向）。
            // 否则按 maxTurnOffset 截断。
            double targetOffset = (Math.abs(deltaYaw) > zeroYaw)
                    ? 0.0D
                    : Mth.clamp(deltaYaw, -maxOffset, maxOffset);

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

        int decayDelay  = MoveConfig.INSTANCE.decayDelay.get();
        double friction = MoveConfig.INSTANCE.friction.get();
        double endSpeed = MoveConfig.INSTANCE.endSpeed.get();

        if (state.ticks >= decayDelay) state.speed *= friction;

        // 注意：这里不再有 !player.onGround()。
        // 空中滑铲保留状态，速度按 friction 自然衰减，
        // 直到速度 ≤ endSpeed 或 < MIN_SPEED 才结束。
        if (state.speed <= endSpeed || state.speed < MIN_SPEED) {
            stop(player);
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
    }

    private static Vec3 yawToHorizontal(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    private static final class State {
        final float initialYaw;
        final Vec3 initialDirection;
        Vec3 direction;
        float currentYaw;
        double speed;
        int ticks;

        State(float yaw, double speed) {
            this.initialYaw = yaw;
            this.initialDirection = yawToHorizontal(yaw);
            this.direction = this.initialDirection;
            this.currentYaw = yaw;
            this.speed = speed;
        }
    }
}