package com.feima.movemod.client;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.PeekAction;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第一人称探头：屏幕绕视线轴 roll。
 *
 * <p>用 Forge 的 {@link ViewportEvent.ComputeCameraAngles}，
 * 参数里带 roll，setRoll() 会被后续渲染直接消费。
 *
 * <p>用 {@link PeekAction#isPeekActive}（而非 isPeeking）作为入口判断，
 * 保证收回时也有过渡。
 */
@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.CLIENT)
public final class PeekCameraRollHandler {

    private PeekCameraRollHandler() {}

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity entity = event.getCamera().getEntity();
        if (!(entity instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        float partial = (float) event.getPartialTick();
        double offset = PeekAction.INSTANCE.smoothOffset(player, partial);
        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < 1.0E-4) return;

        float ratio = (float) (offset / max);
        float roll = ratio * MoveConfig.INSTANCE.peekAngleFirstPerson.get().floatValue();

        // 累加到原 roll 上，不覆盖
        event.setRoll(event.getRoll() + roll);
    }
}