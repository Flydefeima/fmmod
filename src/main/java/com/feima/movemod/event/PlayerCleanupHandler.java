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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID)
public final class PlayerCleanupHandler {

    private PlayerCleanupHandler() {}

    @SubscribeEvent
    public static void onServerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        forgetAll(player.getUUID());
    }

    /**
     * 1.20.1 中 {@code PlayerEvent.Clone} 在死亡重生与跨维度传送时均会触发，
     * 新实例由 {@code restoreFrom} 出来，UUID 不变。因此本事件已覆盖
     * 「跨维度切换」路径，无需再注册 {@code PlayerChangedDimensionEvent}。
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        forgetAll(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        forgetAll(player.getUUID());
    }

    private static void forgetAll(UUID id) {
        SlideAction.INSTANCE.forget(id);
        StaminaTracker.INSTANCE.forget(id);
        ProneAction.INSTANCE.forget(id);
        PeekAction.INSTANCE.forget(id);
        DiveAction.INSTANCE.forget(id);
        // ProneArmPoseMixin 的手臂权重状态（不依赖客户端 API，双端安全）
        ProneArmPoseState.forget(id);
        // SlidePartPoseMixin 的滑铲姿态状态（不依赖客户端 API，双端安全）
        SlidePoseState.forget(id);
    }
}