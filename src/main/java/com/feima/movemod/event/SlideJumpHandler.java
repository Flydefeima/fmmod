package com.feima.movemod.event;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.SlideAction;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 监听跳跃事件，用于触发滑铲跳。仅服务端注册。
 *
 * <p>客户端路径由 {@link com.feima.movemod.client.ClientInputHandler} 的按键边沿检测处理，
 * 这里再注册客户端会造成重复调用（虽然幂等，但属于冗余路径）。
 */
@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.DEDICATED_SERVER)
public final class SlideJumpHandler {

    private SlideJumpHandler() {}

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        SlideAction.INSTANCE.trySlideJump(player);
    }
}