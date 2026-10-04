package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 弹射物命中：探头玩家使用「玩家未旋转局部系」的精确 clip，
 * 表达绕腰 pivot 侧倾 θ 后的斜长方体体积。
 *
 * <p>不做外接 AABB 近似——那样会把头顶的角也填满，头部躲不开。
 * 精确做法见 {@link PeekAction#clipImmediate} / {@link PeekAction#containsImmediate}。
 *
 * <p>命中判定主要在服务端执行，那里没有渲染平滑数据，使用即时几何
 * （当前 {@code yBodyRot} + 目标偏移）。
 *
 * <p>原版行为细节保留：同载具实体走 {@code canRiderInteract()} 判定；
 * 6 参数 Entity 重载使用每个候选者自己的 {@code getPickRadius()}。
 */
@Mixin(ProjectileUtil.class)
public abstract class PeekProjectileMixin {

    /** 哨兵：表示"对每个候选者使用其自身的 getPickRadius()"。 */
    private static final float USE_PICK_RADIUS = -1.0F;

    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void fmm$hitLevel(
            Level level, Entity projectile, Vec3 startVec, Vec3 endVec,
            AABB boundingBox, Predicate<Entity> filter,
            CallbackInfoReturnable<EntityHitResult> cir
    ) {
        cir.setReturnValue(fmm$doHit(level, projectile, startVec, endVec,
                boundingBox, filter, 0.3F));
    }

    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void fmm$hitLevelInflate(
            Level level, Entity projectile, Vec3 startVec, Vec3 endVec,
            AABB boundingBox, Predicate<Entity> filter, float inflate,
            CallbackInfoReturnable<EntityHitResult> cir
    ) {
        cir.setReturnValue(fmm$doHit(level, projectile, startVec, endVec,
                boundingBox, filter, inflate));
    }

    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void fmm$hitEntity(
            Entity projectile, Vec3 startVec, Vec3 endVec,
            AABB boundingBox, Predicate<Entity> filter, double distance,
            CallbackInfoReturnable<EntityHitResult> cir
    ) {
        cir.setReturnValue(fmm$doHit(
                projectile.level(), projectile, startVec, endVec,
                boundingBox, filter, USE_PICK_RADIUS));
    }

    private static EntityHitResult fmm$doHit(
            Level level, Entity projectile, Vec3 startVec, Vec3 endVec,
            AABB boundingBox, Predicate<Entity> filter, float inflate
    ) {
        double closest = Double.MAX_VALUE;
        Entity hitEntity = null;
        Vec3 hitVec = null;

        for (Entity candidate : level.getEntities(projectile, boundingBox, filter)) {
            float candidateInflate = (inflate == USE_PICK_RADIUS)
                    ? candidate.getPickRadius()
                    : inflate;

            boolean startInside;
            Vec3 hitPoint = null;

            if (candidate instanceof Player p && PeekAction.INSTANCE.isPeeking(p)) {
                // 探头玩家：精确的旋转体积 clip。
                startInside = PeekAction.INSTANCE.containsImmediate(p, startVec);
                if (!startInside) {
                    Optional<Vec3> h = PeekAction.INSTANCE.clipImmediate(
                            p, startVec, endVec, candidateInflate);
                    if (h.isEmpty()) continue;
                    hitPoint = h.get();
                }
            } else {
                // 普通实体：原版 AABB clip。
                AABB box = candidate.getBoundingBox().inflate(candidateInflate);
                if (box.contains(startVec)) {
                    startInside = true;
                } else {
                    startInside = false;
                    Optional<Vec3> h = box.clip(startVec, endVec);
                    if (h.isEmpty()) continue;
                    hitPoint = h.get();
                }
            }

            if (startInside) {
                if (closest >= 0.0D) {
                    hitEntity = candidate;
                    hitVec = startVec;
                    closest = 0.0D;
                }
                continue;
            }

            double d = startVec.distanceToSqr(hitPoint);
            if (d >= closest) continue;

            // 与原版一致：同载具实体只有在 canRiderInteract()==false
            // 时才走"特殊替换"分支；否则正常按距离竞争。
            if (candidate.getRootVehicle() == projectile.getRootVehicle()
                    && !candidate.canRiderInteract()) {
                if (closest == 0.0D) {
                    hitEntity = candidate;
                    hitVec = hitPoint;
                }
            } else {
                hitEntity = candidate;
                hitVec = hitPoint;
                closest = d;
            }
        }

        return hitEntity == null ? null : new EntityHitResult(hitEntity, hitVec);
    }
}