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
 * 表达绕脚底 pivot 侧倾 θ 后的斜长方体体积。
 *
 * <p>非探头实体完全走 1.20.1 原版的 AABB clip 路径，不引入载具特判。
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
                boundingBox, filter, 0.3F, Double.MAX_VALUE));
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
                boundingBox, filter, inflate, Double.MAX_VALUE));
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
                boundingBox, filter, USE_PICK_RADIUS, distance));
    }

    private static EntityHitResult fmm$doHit(
            Level level, Entity projectile, Vec3 startVec, Vec3 endVec,
            AABB boundingBox, Predicate<Entity> filter, float inflate,
            double initialClosest
    ) {
        double closest = initialClosest;
        Entity hitEntity = null;
        Vec3 hitVec = null;

        for (Entity candidate : level.getEntities(projectile, boundingBox, filter)) {
            float candidateInflate = (inflate == USE_PICK_RADIUS)
                    ? candidate.getPickRadius()
                    : inflate;

            Vec3 hitPoint;

            if (candidate instanceof Player p && PeekAction.INSTANCE.isPeeking(p)) {
                // 探头玩家：精确的旋转体积 clip。
                if (PeekAction.INSTANCE.containsImmediate(p, startVec)) {
                    hitPoint = startVec;
                } else {
                    Optional<Vec3> h = PeekAction.INSTANCE.clipImmediate(
                            p, startVec, endVec, candidateInflate);
                    if (h.isEmpty()) continue;
                    hitPoint = h.get();
                }
            } else {
                // 普通实体：与 1.20.1 原版一致。
                AABB box = candidate.getBoundingBox().inflate(candidateInflate);
                if (box.contains(startVec)) {
                    hitPoint = startVec;
                } else {
                    Optional<Vec3> h = box.clip(startVec, endVec);
                    if (h.isEmpty()) continue;
                    hitPoint = h.get();
                }
            }

            double d = startVec.distanceToSqr(hitPoint);
            if (d < closest) {
                closest = d;
                hitEntity = candidate;
                hitVec = hitPoint;
            }
        }

        return hitEntity == null ? null : new EntityHitResult(hitEntity, hitVec);
    }
}