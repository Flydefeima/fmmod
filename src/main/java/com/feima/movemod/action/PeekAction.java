package com.feima.movemod.action;

import com.feima.movemod.client.SlideClientHelper;
import com.feima.movemod.config.MoveConfig;
import com.feima.movemod.network.NetworkHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探头：整个玩家体积绕<b>脚底</b>竖直轴侧倾 θ。
 *
 * <p><b>耐力</b>：探头期间每 tick 消耗
 * {@code stamina.peek.costPerTick}，耗尽时自动退出。
 * 消耗只由服务端和本地客户端执行——远端玩家的耐力消耗在服务端，
 * 客户端侧不重复扣减。
 *
 * <p>详细说明见项目文档。这里只列出关键不变量：
 * <ul>
 *   <li>{@link #worldToLocal} 与 {@link #localToWorld} 严格互逆</li>
 *   <li>旋转 pivot = 基盒底边中心（脚底）</li>
 *   <li>动画只走客户端；命中判定使用 {@link #thetaImmediate}（即时几何）</li>
 * </ul>
 */
public final class PeekAction {

    public static final PeekAction INSTANCE = new PeekAction();

    public enum Dir { NONE, LEFT, RIGHT }

    private static final double EPS = 1.0E-4;

    private final Map<UUID, Dir> serverDir = new ConcurrentHashMap<>();
    private final Map<UUID, Dir> clientDir = new ConcurrentHashMap<>();

    /** 客户端动画状态：每玩家一份。 */
    private final Map<UUID, AnimState> clientAnim = new ConcurrentHashMap<>();

    private PeekAction() {}

    private static final class AnimState {
        double prev;
        double cur;
        double start;
        double target;
        float  time;
        boolean initialized;

        void begin(double newTarget) {
            this.start  = this.cur;
            this.time   = 0f;
            this.target = newTarget;
        }
    }

    private Map<UUID, Dir> dirs(Player p) {
        return p.level().isClientSide ? clientDir : serverDir;
    }

    public Dir dir(Player p) {
        return dirs(p).getOrDefault(p.getUUID(), Dir.NONE);
    }

    public boolean isPeeking(Player p) {
        Dir d = dir(p);
        return d != null && d != Dir.NONE;
    }

    /**
     * 是否处于「探头活跃」状态：方向非 NONE，或客户端显示值尚未归零。
     */
    public boolean isPeekActive(Player p) {
        if (isPeeking(p)) return true;
        if (!p.level().isClientSide) return false;
        AnimState s = clientAnim.get(p.getUUID());
        if (s == null) return false;
        return Math.abs(s.cur) > EPS || Math.abs(s.prev) > EPS;
    }

    // ============================================================
    // 目标偏移
    // ============================================================
    public double targetOffset(Player p) {
        Dir d = dir(p);
        if (d == Dir.NONE) return 0.0;
        double mag = MoveConfig.INSTANCE.peekDistance.get();
        return d == Dir.LEFT ? -mag : mag;
    }

    public static Vec3 rightVector(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.cos(r), 0.0, -Math.sin(r));
    }

    public static float bodyYaw(Player p, float partial) {
        return p.yBodyRotO + (p.yBodyRot - p.yBodyRotO) * partial;
    }

    // ============================================================
    // 基盒
    // ============================================================
    public AABB baseBoxAt(Player p, double signedOffset) {
        AABB bb = p.getBoundingBox();
        double width  = MoveConfig.INSTANCE.peekHitboxWidth.get();
        double height = p.isCrouching()
                ? MoveConfig.INSTANCE.peekHitboxCrouchHeight.get()
                : MoveConfig.INSTANCE.peekHitboxHeight.get();

        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;

        if (Math.abs(signedOffset) > EPS) {
            double rad = Math.toRadians(p.yBodyRot);
            double rX = -Math.cos(rad);
            double rZ = -Math.sin(rad);
            cx += rX * signedOffset;
            cz += rZ * signedOffset;
        }

        double halfW = width * 0.5;
        double bottomY = bb.minY;
        double topY    = bottomY + height;

        return new AABB(
                cx - halfW, bottomY, cz - halfW,
                cx + halfW, topY,    cz + halfW
        );
    }

    public AABB baseBox(Player p) {
        double cfg = MoveConfig.INSTANCE.peekHitboxOffset.get()
                   + MoveConfig.INSTANCE.peekModelOffset.get();
        Dir d = dir(p);
        double signedOffset = (d == Dir.RIGHT) ? cfg
                            : (d == Dir.LEFT)  ? -cfg
                            : 0.0;
        return baseBoxAt(p, signedOffset);
    }

    public AABB baseBoxRender(Player p, float partial) {
        double max = MoveConfig.INSTANCE.peekDistance.get();
        double cfg = MoveConfig.INSTANCE.peekHitboxOffset.get()
                   + MoveConfig.INSTANCE.peekModelOffset.get();
        double offset = smoothOffset(p, partial);
        double ratio = (max <= 0.0) ? 0.0 : offset / max;
        return baseBoxAt(p, ratio * cfg);
    }

    public static Vec3 pivotOf(AABB base) {
        return new Vec3(
                (base.minX + base.maxX) * 0.5,
                base.minY,
                (base.minZ + base.maxZ) * 0.5
        );
    }

    public Vec3 pivot(Player p) {
        return pivotOf(baseBox(p));
    }

    public Vec3 pivotRender(Player p, float partial) {
        return pivotOf(baseBoxRender(p, partial));
    }

    // ============================================================
    // 旋转角
    // ============================================================
    public double thetaFor(Player p, double offset) {
        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < EPS) return 0.0;
        double ratio = offset / max;
        return Math.toRadians(ratio * MoveConfig.INSTANCE.peekAngleThirdPerson.get());
    }

    public double thetaImmediate(Player p) {
        return thetaFor(p, targetOffset(p));
    }

    public double thetaRender(Player p, float partial) {
        return thetaFor(p, smoothOffset(p, partial));
    }

    // ============================================================
    // 坐标变换：世界 ⇄ 玩家未旋转局部系
    // ============================================================
    public Vec3 worldToLocal(Player p, Vec3 w, float bodyYaw, double theta) {
        Vec3 pv = pivot(p);
        Vec3 d = w.subtract(pv);

        double rad = Math.toRadians(bodyYaw);
        double rX = -Math.cos(rad), rZ = -Math.sin(rad);
        double fX = -Math.sin(rad), fZ = Math.cos(rad);

        double xr = d.x * rX + d.z * rZ;
        double zf = d.x * fX + d.z * fZ;
        double yu = d.y;

        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        double xr2 = xr * cosT - yu * sinT;
        double yu2 = xr * sinT + yu * cosT;

        return new Vec3(xr2, yu2, zf);
    }

    public Vec3 localToWorld(Player p, Vec3 l, float bodyYaw, double theta) {
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);

        double xr = l.x * cosT + l.y * sinT;
        double yu = -l.x * sinT + l.y * cosT;
        double zf = l.z;

        double rad = Math.toRadians(bodyYaw);
        double rX = -Math.cos(rad), rZ = -Math.sin(rad);
        double fX = -Math.sin(rad), fZ = Math.cos(rad);

        Vec3 pv = pivot(p);
        return new Vec3(
                pv.x + xr * rX + zf * fX,
                pv.y + yu,
                pv.z + xr * rZ + zf * fZ
        );
    }

    // ============================================================
    // 精确命中检测（局部系）
    // ============================================================
    public boolean containsImmediate(Player p, Vec3 w) {
        float bodyYaw = p.yBodyRot;
        double theta = thetaImmediate(p);
        Vec3 pv = pivot(p);
        AABB localBox = baseBox(p).move(-pv.x, -pv.y, -pv.z);
        Vec3 l = worldToLocal(p, w, bodyYaw, theta);
        return localBox.contains(l);
    }

    public Optional<Vec3> clipImmediate(Player p, Vec3 start, Vec3 end, float inflate) {
        float bodyYaw = p.yBodyRot;
        double theta = thetaImmediate(p);
        Vec3 pv = pivot(p);

        AABB localBox = baseBox(p).move(-pv.x, -pv.y, -pv.z);
        if (inflate > 0.0F) localBox = localBox.inflate(inflate);

        Vec3 ls = worldToLocal(p, start, bodyYaw, theta);
        Vec3 le = worldToLocal(p, end, bodyYaw, theta);

        Optional<Vec3> hl = localBox.clip(ls, le);
        if (hl.isEmpty()) return Optional.empty();
        return Optional.of(localToWorld(p, hl.get(), bodyYaw, theta));
    }

    // ============================================================
    // 外接 AABB（仅调试渲染用）
    // ============================================================
    public AABB boxAt(Player p, float bodyYaw, double offset) {
        AABB base = baseBox(p);

        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < EPS) {
            return base;
        }

        double halfW = (base.maxX - base.minX) * 0.5;
        double h     = base.maxY - base.minY;

        double pvX = (base.minX + base.maxX) * 0.5;
        double pvZ = (base.minZ + base.maxZ) * 0.5;

        double ratio    = offset / max;
        double thetaDeg = ratio * MoveConfig.INSTANCE.peekAngleThirdPerson.get();
        double theta    = Math.toRadians(thetaDeg);

        double rad = Math.toRadians(bodyYaw);
        double rX = -Math.cos(rad), rZ = -Math.sin(rad);
        double fX = -Math.sin(rad), fZ = Math.cos(rad);

        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (int sx = -1; sx <= 1; sx += 2) {
            double xr = sx * halfW;
            for (int sy = 0; sy <= 1; sy++) {
                double yu = sy * h;
                for (int sz = -1; sz <= 1; sz += 2) {
                    double zf = sz * halfW;

                    double xr2 =  xr * cosT - yu * sinT;
                    double yu2 =  xr * sinT + yu * cosT;

                    double wx = pvX + xr2 * rX + zf * fX;
                    double wy = base.minY + yu2;
                    double wz = pvZ + xr2 * rZ + zf * fZ;

                    if (wx < minX) minX = wx;
                    if (wx > maxX) maxX = wx;
                    if (wy < minY) minY = wy;
                    if (wy > maxY) maxY = wy;
                    if (wz < minZ) minZ = wz;
                    if (wz > maxZ) maxZ = wz;
                }
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public AABB box(Player p, float partial) {
        return boxAt(p, bodyYaw(p, partial), smoothOffset(p, partial));
    }

    public double smoothOffset(Player p, float partial) {
        AnimState s = clientAnim.get(p.getUUID());
        if (s == null) return 0.0;
        return Mth.lerp(partial, s.prev, s.cur);
    }

    // ============================================================
    // tick
    // ============================================================
    public void tick(Player player) {
        if (player.level().isClientSide) {
            tickClientAnim(player);
        }

        if (!isPeeking(player)) return;

        // 耐力消耗只由「该玩家的权威持有者」执行：
        //   - 服务端：消耗所有探头玩家的耐力
        //   - 客户端：只为本地玩家消耗（远端玩家的耐力由服务端广播驱动）
        // 避免双端对同一玩家重复扣减。
        boolean isStaminaOwner = !player.level().isClientSide
                              || SlideClientHelper.isLocalPlayer(player);
        if (!isStaminaOwner) return;

        // 持续耐力消耗
        boolean staminaExhausted = false;
        if (MoveConfig.INSTANCE.staminaEnabled.get()) {
            double perTick = MoveConfig.INSTANCE.staminaPeekCostPerTick.get();
            if (perTick > 0.0D) {
                StaminaTracker.INSTANCE.consumeUpTo(player, perTick);
                if (StaminaTracker.INSTANCE.get(player) <= 0.0D) {
                    staminaExhausted = true;
                }
            }
        }

        if (!staminaExhausted && passiveAllowed(player)) return;

        // 退出：耐力耗尽 或 环境不再允许
        if (player.level().isClientSide) {
            // 只可能是本地玩家（前面的 isStaminaOwner 已保证）
            stop(player);
            NetworkHandler.sendPeekSet(Dir.NONE);
        } else {
            stop(player);
            NetworkHandler.broadcastPeekState(player, Dir.NONE);
        }
    }

    private void tickClientAnim(Player player) {
        UUID id = player.getUUID();
        AnimState s = clientAnim.computeIfAbsent(id, k -> new AnimState());

        double target = targetOffset(player);

        s.prev = s.cur;

        if (!s.initialized) {
            s.prev = 0.0;
            s.cur = 0.0;
            s.start = 0.0;
            s.target = target;
            s.time = 0f;
            s.initialized = true;
        }

        if (Math.abs(target - s.target) > EPS) {
            s.begin(target);
        }

        float durationTicks = MoveConfig.INSTANCE.peekTransitionTicks.get();

        if (durationTicks <= 0f) {
            s.cur = target;
            s.time = 0f;
            s.target = target;
            return;
        }

        if (s.time >= durationTicks) {
            s.cur = target;
            return;
        }

        s.time += 1f;
        float t = Math.min(s.time / durationTicks, 1f);
        float eased = 1f - (float) Math.pow(1.0 - t, 3.0);
        s.cur = s.start + (target - s.start) * eased;
    }

    // ============================================================
    // 状态切换
    // ============================================================
    public boolean canPeek(Player p) {
        if (!passiveAllowed(p)) return false;
        // 互斥：集中判定
        if (ActionExclusivity.isAnyOtherActive(p, ActionExclusivity.Action.PEEK)) {
            return false;
        }
        return true;
    }

    private boolean passiveAllowed(Player p) {
        if (!MoveConfig.INSTANCE.peekEnabled.get()) return false;
        if (p.isSpectator() || p.isDeadOrDying()) return false;
        if (p.isPassenger() || p.isSleeping() || p.isFallFlying()) return false;
        if (p.isInWater() || p.isInLava()) return false;
        return true;
    }

    public boolean trySet(Player p, Dir d) {
        if (d != Dir.NONE && !canPeek(p)) return false;

        UUID id = p.getUUID();
        Dir old = dir(p);
        if (old == d) return true;

        dirs(p).put(id, d);
        return true;
    }

    public void applyRemoteState(Player player, Dir dir) {
        if (!player.level().isClientSide) return;

        UUID id = player.getUUID();
        Dir old = dirs(player).getOrDefault(id, Dir.NONE);
        if (old == dir) return;

        dirs(player).put(id, dir);

        if (dir != Dir.NONE) {
            // 互斥：远端进入探头 → 清掉其它本地预测
            ActionExclusivity.stopOthers(player, ActionExclusivity.Action.PEEK);
        }
    }

    public void stop(Player p) {
        dirs(p).put(p.getUUID(), Dir.NONE);
    }

    public void forget(UUID id) {
        serverDir.remove(id);
        clientDir.remove(id);
        clientAnim.remove(id);
    }
}