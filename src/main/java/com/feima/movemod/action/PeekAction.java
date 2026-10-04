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
 * <p><b>为什么 pivot 在脚而不是腰</b>：绕腰旋转时只有上半身明显侧倾，
 * 腿几乎不动，看起来像"上半身扭了一下"。绕脚底旋转时整个模型作为
 * 刚体倾斜，脚钉在原地、头向侧方远离，视觉上就是"整个人向侧面探出"。
 * 第三人称模型（{@code PeekFullBodyMixin}）用的是同一个脚底 pivot，
 * 所以模型的倾斜形状与碰撞箱的倾斜形状能精确对齐。
 *
 * <p><b>体积怎么表达</b>：玩家实际占据的空间是一个绕脚底 pivot
 * 侧倾 θ 的斜长方体。AABB 本身无法旋转，但我们可以在
 * <b>玩家未旋转的局部参照系</b>里做命中检测：
 * <ol>
 *   <li>把世界坐标点平移回 pivot（脚底中心）原点，再绕玩家 forward 轴
 *       反旋转 -θ（即应用 R(+θ)）；</li>
 *   <li>在这个局部参照系里，玩家体积 = 未旋转的 baseBox；</li>
 *   <li>用局部 AABB 做 clip，得到命中点；</li>
 *   <li>把命中点正旋转回世界坐标（应用 R(-θ)）。</li>
 * </ol>
 *
 * <p>{@link #worldToLocal} 与 {@link #localToWorld} 是严格互逆的。
 *
 * <p><b>动画模型</b>：
 * <ul>
 *   <li>目标变化时，锁定 {@code start = cur} 并重置时间轴</li>
 *   <li>时间轴线性推进 {@code t = time / duration}</li>
 *   <li>缓动 {@code eased = 1 - (1-t)^3}（easeOutCubic）</li>
 *   <li>输出 {@code cur = start + (target - start) * eased}</li>
 *   <li>渲染时 {@code Mth.lerp(partial, prev, cur)} 在 tick 间插值</li>
 * </ul>
 *
 * <p><b>横向偏移</b>：碰撞箱 / 命中判定的总横向偏移 =
 * {@code peek.hitbox.offset}（对准微调）+ {@code peek.modelOffset}（跟随模型）。
 * 前者是固定校准量，后者与第三人称模型的横向偏移一致。
 *
 * <p><b>动画只走客户端</b>：服务端没有 prev / cur 数据，
 * {@link #thetaImmediate} 仍是即时几何，命中判定使用它。
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
     *
     * <p><b>视觉 mixin 必须用这个，而不是 {@link #isPeeking}</b>。
     * 收回时 {@code dir} 立即变 NONE，但动画要经过 transitionTicks
     * 才归零。
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
    /**
     * 用<b>显式 signedOffset</b>（正=右，负=左，0=居中）构造基盒。
     *
     * <p>玩家基盒（未旋转）：水平居中于玩家（再沿探头方向横向平移
     * {@code signedOffset}），底边贴合玩家碰撞盒底部，高度由
     * {@code peek.hitbox.height}（站立）或 {@code peek.hitbox.crouchHeight}
     * （蹲下）决定，宽度由 {@code peek.hitbox.width} 决定。
     *
     * <p>旋转 pivot = 基盒<b>底边中心</b>（脚底），见 {@link #pivotOf}。
     */
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

    /**
     * 即时基盒：横向偏移 = {@code peek.hitbox.offset}（对准）
     * + {@code peek.modelOffset}（跟随模型），方向由 {@code dir} 决定。
     * 命中判定用。
     */
    public AABB baseBox(Player p) {
        double cfg = MoveConfig.INSTANCE.peekHitboxOffset.get()
                   + MoveConfig.INSTANCE.peekModelOffset.get();
        Dir d = dir(p);
        double signedOffset = (d == Dir.RIGHT) ? cfg
                            : (d == Dir.LEFT)  ? -cfg
                            : 0.0;
        return baseBoxAt(p, signedOffset);
    }

    /**
     * 渲染用基盒：横向偏移随动画插值，量同样为
     * {@code peekHitboxOffset + peekModelOffset}。
     */
    public AABB baseBoxRender(Player p, float partial) {
        double max = MoveConfig.INSTANCE.peekDistance.get();
        double cfg = MoveConfig.INSTANCE.peekHitboxOffset.get()
                   + MoveConfig.INSTANCE.peekModelOffset.get();
        double offset = smoothOffset(p, partial);
        double ratio = (max <= 0.0) ? 0.0 : offset / max;
        return baseBoxAt(p, ratio * cfg);
    }

    /**
     * 由基盒取旋转 pivot：<b>底边中心</b>（脚底）。
     *
     * <p>与 {@code PeekFullBodyMixin} 里模型的脚底 pivot 一致，
     * 因此碰撞箱的倾斜形状与模型的倾斜形状精确匹配。
     */
    public static Vec3 pivotOf(AABB base) {
        return new Vec3(
                (base.minX + base.maxX) * 0.5,
                base.minY,
                (base.minZ + base.maxZ) * 0.5
        );
    }

    /** 即时 pivot。命中判定用。 */
    public Vec3 pivot(Player p) {
        return pivotOf(baseBox(p));
    }

    /** 渲染用 pivot。 */
    public Vec3 pivotRender(Player p, float partial) {
        return pivotOf(baseBoxRender(p, partial));
    }

    // ============================================================
    // 旋转角
    // ============================================================
    /** 由 offset 换算旋转角（弧度）。 */
    public double thetaFor(Player p, double offset) {
        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < EPS) return 0.0;
        double ratio = offset / max;
        return Math.toRadians(ratio * MoveConfig.INSTANCE.peekAngleThirdPerson.get());
    }

    /** 即时旋转角（弧度）。命中判定用。 */
    public double thetaImmediate(Player p) {
        return thetaFor(p, targetOffset(p));
    }

    /** 渲染用旋转角（弧度）。 */
    public double thetaRender(Player p, float partial) {
        return thetaFor(p, smoothOffset(p, partial));
    }

    // ============================================================
    // 坐标变换：世界 ⇄ 玩家未旋转局部系
    //
    // pivot 在脚底：局部系的原点就是脚底中心。
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

        // pivot 在脚底：局部 y 从 0 到 h，绕 0 旋转。
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
                double yu = sy * h;  // 0（脚底）或 h（头顶）
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

    /** 渲染帧插值：{@code lerp(partial, prev, cur)}。 */
    public double smoothOffset(Player p, float partial) {
        AnimState s = clientAnim.get(p.getUUID());
        if (s == null) return 0.0;
        return Mth.lerp(partial, s.prev, s.cur);
    }

    // ============================================================
    // tick
    // ============================================================
    public void tick(Player player) {
        // 1) 客户端动画推进
        if (player.level().isClientSide) {
            tickClientAnim(player);
        }

        // 2) 运行期合法性复查（与 CrawlAction.tick 对称）
        if (!isPeeking(player)) return;
        if (passiveAllowed(player)) return;

        if (player.level().isClientSide) {
            if (SlideClientHelper.isLocalPlayer(player)) {
                stop(player);
                NetworkHandler.sendPeekSet(Dir.NONE);
            }
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
        if (SlideAction.INSTANCE.isSliding(p)) return false;
        if (CrawlAction.INSTANCE.isCrawling(p)) return false;
        return true;
    }

    /** 与启动条件共享的被动合法性检查：开关、状态、环境。 */
    private boolean passiveAllowed(Player p) {
        if (!MoveConfig.INSTANCE.enabled.get()) return false;
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

    /**
     * S2C 权威同步：直接写入状态，不走 {@link #canPeek}。
     *
     * <p>为什么不能用 {@link #trySet}：{@code trySet} 会做互斥检查
     * （滑铲/趴下），而客户端的滑铲/趴下预测状态常比服务端滞后几 tick，
     * 会导致合法的服务端探头广播被本地误拒。这里以服务端为权威，
     * 收到广播后立即写入，并清理本地对同一玩家的竞争预测状态。
     */
    public void applyRemoteState(Player player, Dir dir) {
        if (!player.level().isClientSide) return;

        UUID id = player.getUUID();
        Dir old = dirs(player).getOrDefault(id, Dir.NONE);
        if (old == dir) return;

        dirs(player).put(id, dir);

        // 互斥：服务端权威决定进入探头 → 清掉本地预测的滑铲 / 趴下
        if (dir != Dir.NONE) {
            SlideAction.INSTANCE.stop(player);
            CrawlAction.INSTANCE.stop(player);
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