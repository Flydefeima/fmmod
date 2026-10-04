package com.feima.movemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端专属工具：隔离 {@code Minecraft} / {@code LocalPlayer} 的引用，
 * 避免服务端加载 {@link com.feima.movemod.action.SlideAction} 时触发客户端类解析。
 *
 * <p>只有在 {@code player.level().isClientSide == true} 时才会被调用，
 * 服务端代码路径永远不会执行到这里。
 */
public final class SlideClientHelper {

    private SlideClientHelper() {}

    /** 玩家是否按下了前进键（本地玩家）。 */
    public static boolean hasForwardInput() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return true;
        return mc.options.keyUp.isDown();
    }

    /** 是否为本地玩家实例。 */
    public static boolean isLocalPlayer(Player player) {
        return player instanceof LocalPlayer;
    }
}