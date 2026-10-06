package com.feima.movemod.event;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.DiveAction;
import com.feima.movemod.action.PeekAction;
import com.feima.movemod.action.ProneAction;
import com.feima.movemod.action.SlideAction;
import com.feima.movemod.action.StaminaTracker;
import com.feima.movemod.client.ProneArmPoseState;
import com.feima.movemod.client.SlidePoseState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 客户端侧的玩家状态清理。
 *
 * <p><b>为什么需要本类</b>：{@code PlayerCleanupHandler} 挂的三个事件
 * （Logout / Clone / Respawn）只在服务端触发。客户端上「远端玩家退出」
 * 不会触发它们，导致各 action 类与 {@link ProneArmPoseState} /
 * {@link SlidePoseState} 里的 client* Map 按 UUID 永久累积——长期挂机 /
 * 频繁换服时内存会缓慢增长。
 *
 * <p><b>清理时机</b>：{@link EntityLeaveLevelEvent} 在任意玩家实体离开
 * 当前客户端世界时触发（断开连接、被服务端移除、维度切换的旧实例等），
 * 覆盖了客户端侧的所有「玩家消失」路径。
 *
 * <p>所有 action 类的 {@code forget(UUID)} 对双端 Map 都执行 remove，
 * 在客户端调用它们只会清空 client* 条目，不会误伤服务端数据——而本类
 * 本身只挂在 {@code Dist.CLIENT}，服务端根本不会执行。
 */
@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.CLIENT)
public final class ClientPlayerCleanupHandler {

    private ClientPlayerCleanupHandler() {}

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.level().isClientSide) return;
        forgetClient(player.getUUID());
    }

    private static void forgetClient(UUID id) {
        SlideAction.INSTANCE.forget(id);
        StaminaTracker.INSTANCE.forget(id);
        ProneAction.INSTANCE.forget(id);
        PeekAction.INSTANCE.forget(id);
        DiveAction.INSTANCE.forget(id);
        // ProneArmPoseMixin 的手臂权重状态（不依赖客户端 API，双端安全）
        ProneArmPoseState.forget(id);
        // SlidePartPoseMixin 的滑铲姿态状态
        SlidePoseState.forget(id);
    }
}