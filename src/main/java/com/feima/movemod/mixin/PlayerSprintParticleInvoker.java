package com.feima.movemod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把 {@code Entity.spawnSprintParticle()} 从 protected 暴露出来，
 * 让 {@link com.feima.movemod.action.SlideAction} 能在滑铲时复用原版疾跑粒子。
 *
 * <p>方法声明在 {@link Entity} 上（不是 Player），所以 Mixin 目标必须是 Entity。
 * 只暴露、不修改行为，因此完全是原版实现（含脚下方块决定粒子类型的分支）。
 */
@Mixin(Entity.class)
public interface PlayerSprintParticleInvoker {

    @Invoker("spawnSprintParticle")
    void fmm$spawnSprintParticle();
}