package com.feima.movemod.client;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.CrawlAction;
import com.feima.movemod.action.PeekAction;
import com.feima.movemod.action.SlideAction;
import com.feima.movemod.action.StaminaTracker;
import com.feima.movemod.config.MoveConfig;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.CLIENT)
public final class SlideAnimationController {

    private static final ResourceLocation SLIDE_ANIMATION =
            new ResourceLocation(FeimaMoveMod.MODID, "sliding");

    private static final Map<UUID, Boolean> lastSliding = new ConcurrentHashMap<>();

    private SlideAnimationController() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            lastSliding.clear();
            return;
        }

        // 裁剪离场玩家：UUID 不再访问会留下空条目，长期运行会让 map 缓慢增长。
        // retainAll 是 O(n)，n 是历史玩家数，每 tick 一次，量级很小。
        Set<UUID> activeIds = new HashSet<>();
        for (AbstractClientPlayer p : level.players()) {
            activeIds.add(p.getUUID());
        }
        lastSliding.keySet().retainAll(activeIds);

        boolean enabled = MoveConfig.INSTANCE.enabled.get()
                && MoveConfig.INSTANCE.slideEnabled.get();

        for (AbstractClientPlayer player : level.players()) {
            UUID id = player.getUUID();

            boolean sliding = enabled && SlideAction.INSTANCE.isSliding(player);
            Boolean prevBoxed = lastSliding.put(id, sliding);
            boolean prev = prevBoxed != null && prevBoxed;

            if (prev == sliding) continue;

            if (sliding) {
                play(player, SLIDE_ANIMATION);
            } else {
                play(player, null);
            }
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() == null) return;
        UUID id = event.getPlayer().getUUID();
        forget(id);
        SlideAction.INSTANCE.forget(id);
        StaminaTracker.INSTANCE.forget(id);
        CrawlAction.INSTANCE.forget(id);
        PeekAction.INSTANCE.forget(id);
    }

    public static void forget(UUID id) {
        lastSliding.remove(id);
    }

    private static void play(AbstractClientPlayer player, ResourceLocation animation) {
        ModifierLayer<IAnimation> layer = getLayer(player);
        if (layer == null) return;

        if (animation == null) {
            layer.setAnimation(null);
            return;
        }

        var anim = PlayerAnimationRegistry.getAnimation(animation);
        if (anim == null) {
            FeimaMoveMod.LOGGER.warn("[Feima Move] 找不到动画: {}", animation);
            return;
        }
        layer.setAnimation(new KeyframeAnimationPlayer(anim));
    }

    /**
     * 取本模组的动画层。
     *
     * <p>如果同一 {@code LAYER_ID} 被其它模组/旧版本注册了不同实现，
     * 直接强转会 ClassCastException，进而把整个 tick 崩掉；这里改成
     * instanceof 判断，不匹配时降级为"跳过动画"，只影响本模组表现。
     */
    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> getLayer(AbstractClientPlayer player) {
        Object raw = PlayerAnimationAccess
                .getPlayerAssociatedData(player)
                .get(PlayerAnimationSetup.LAYER_ID);
        if (!(raw instanceof ModifierLayer<?>)) return null;
        return (ModifierLayer<IAnimation>) raw;
    }
}