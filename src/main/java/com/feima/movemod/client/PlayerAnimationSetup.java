package com.feima.movemod.client;

import com.feima.movemod.FeimaMoveMod;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 给每个玩家挂一个动画层，供后续播放/停止动画。
 *
 * <p><b>层优先级</b>：PlayerAnimator 按 priority 升序应用各层，
 * 高优先级层后应用，直接覆盖同骨骼的变换。因此本模组的滑铲动画层
 * 使用 priority 97，高于 TaCZ 的 93~96，保证滑铲动画在持枪时
 * 不被 TaCZ 的 idle / walk / run / aim 动画覆盖。
 */
@Mod.EventBusSubscriber(
        modid = FeimaMoveMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class PlayerAnimationSetup {

    /** 本模组的动画层 ID，之后要用它取层 */
    public static final ResourceLocation LAYER_ID =
            new ResourceLocation(FeimaMoveMod.MODID, "animation");

    /**
     * 滑铲层优先级。
     *
     * <p>TaCZ 的层（{@code AnimationDataRegisterFactory}）：
     * <ul>
     *   <li>{@code tacz:lower_animation} → 93</li>
     *   <li>{@code tacz:loop_upper_animation} → 94</li>
     *   <li>{@code tacz:once_upper_animation} → 95</li>
     *   <li>{@code tacz:rotation} → 96</li>
     * </ul>
     * 本层取 97，确保滑铲动画最后应用、覆盖 TaCZ。
     */
    private static final int LAYER_PRIORITY = 97;

    private PlayerAnimationSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                LAYER_PRIORITY,
                PlayerAnimationSetup::createLayer
        );
    }

    private static IAnimation createLayer(AbstractClientPlayer player) {
        return new ModifierLayer<>();
    }
}