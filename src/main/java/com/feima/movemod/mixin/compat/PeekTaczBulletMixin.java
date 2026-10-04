package com.feima.movemod.mixin.compat;

import com.feima.movemod.action.PeekAction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 兼容 TaCZ：让 {@code EntityKineticBullet} 出生点跟随探头横向偏移。
 *
 * <p>TaCZ 的子弹出生点在构造器尾部由
 * {@code throwerIn.xOld + (getX - xOld)/2 + ... + getEyeHeight()}
 * 计算，横向偏移不会体现在其中（{@code getEyeY()} 不经过
 * {@code getEyePosition(float)}）。这里在构造器 TAIL 平移子弹位置，
 * 并同步 {@code startPos}（伤害距离衰减参考原点）。
 *
 * <p>{@link Pseudo} + 独立 mixin config（required = false）：
 * 没装 TaCZ 时静默跳过，不崩游戏。
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet")
public abstract class PeekTaczBulletMixin {

    @Shadow
    private Vec3 startPos;

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;" +
                     "Lnet/minecraft/world/level/Level;" +
                     "Lnet/minecraft/world/entity/LivingEntity;" +
                     "Lnet/minecraft/world/item/ItemStack;" +
                     "Lnet/minecraft/resources/ResourceLocation;" +
                     "Lnet/minecraft/resources/ResourceLocation;" +
                     "Lnet/minecraft/resources/ResourceLocation;" +
                     "Z" +
                     "Lcom/tacz/guns/resource/pojo/data/gun/GunData;" +
                     "Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("TAIL")
    )
    private void fmm$shiftForPeek(CallbackInfo ci) {
        // EntityKineticBullet 继承 Projectile，用 Projectile 才能访问 getOwner()
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeeking(player)) return;

        double offset = PeekAction.INSTANCE.targetOffset(player);
        if (Math.abs(offset) < 1.0E-4) return;

        Vec3 right = PeekAction.rightVector(player.yBodyRot);
        Vec3 pos = self.position();

        self.setPos(pos.x + right.x * offset, pos.y, pos.z + right.z * offset);
        this.startPos = self.position();
    }
}