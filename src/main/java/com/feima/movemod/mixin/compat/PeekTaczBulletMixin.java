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
 * <p><b>为什么 method 用 {@code "<init>"} 而不带参数签名</b>：
 * TaCZ 在 1.1.x 系列的构造器参数列表在不同 patch 版本之间偶有变动
 * （例如新增 {@code GunData} / {@code BulletData} 之外的字段）。
 * 精确签名一旦不匹配，注入就会失败——若 {@code require} 为默认值，
 * 还会直接抛 {@code InjectionError} 让游戏启动崩。
 *
 * <p>用通配的 {@code "<init>"} 只依赖"存在构造器"这一事实，配合
 * {@code require = 0} 让注入失败时静默跳过而不是崩溃；若 TaCZ 未来
 * 引入多个构造器重载，{@code at = TAIL} 会对每个构造器尾部都跑一次，
 * 但因为内部会检查 {@code owner instanceof Player && isPeeking}，
 * 对非子弹构造器通常不满足条件，无副作用。
 *
 * <p><b>兼容性说明</b>：本 Mixin 依赖 TaCZ 的 {@code EntityKineticBullet}
 * 内部字段 {@code startPos}。如果未来 TaCZ 重命名或删除了这个字段，
 * 注入会在运行时静默失效（{@code require = 0} 保证不崩），届时需要
 * 检查 TaCZ 变更并同步更新。
 *
 * <p>{@link Pseudo} + 独立 mixin config（required = false）：
 * 没装 TaCZ 时静默跳过，不崩游戏。
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet")
public abstract class PeekTaczBulletMixin {

    @Shadow
    private Vec3 startPos;

    /**
     * {@code method = "<init>"} 匹配所有构造器；
     * {@code require = 0} 让注入失败时静默跳过（不崩游戏）。
     */
    @Inject(
            method = "<init>",
            at = @At("TAIL"),
            require = 0
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