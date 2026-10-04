package com.feima.movemod.network;

import com.feima.movemod.action.SlideAction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步某个玩家的滑铲状态 + 权威耐力 + 权威初速。
 *
 * <p>{@code sliding = true}：进入滑铲（speed 有效）。
 * <p>{@code sliding = false}：结束滑铲。
 * <p>{@code rejected = true}：本地玩家的请求被服务端拒绝，客户端应清理本地 CD 以允许立即重试。
 * <p>其余情况 {@code rejected = false}。
 */
public class SlideStatePacket {

    private final UUID playerId;
    private final boolean sliding;
    private final double stamina;
    private final double speed;
    private final boolean rejected;

    public SlideStatePacket(UUID playerId, boolean sliding, double stamina, double speed, boolean rejected) {
        this.playerId = playerId;
        this.sliding = sliding;
        this.stamina = stamina;
        this.speed = speed;
        this.rejected = rejected;
    }

    public static void encode(SlideStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.sliding);
        buf.writeDouble(msg.stamina);
        buf.writeDouble(msg.speed);
        buf.writeBoolean(msg.rejected);
    }

    public static SlideStatePacket decode(FriendlyByteBuf buf) {
        return new SlideStatePacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean()
        );
    }

    public static void handle(SlideStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg))
        );
        context.setPacketHandled(true);
    }

    private static void handleClient(SlideStatePacket msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        Player player = level.getPlayerByUUID(msg.playerId);
        if (player == null) return;

        SlideAction.INSTANCE.applyRemoteState(
                player, msg.sliding, msg.stamina, msg.speed, msg.rejected);
    }
}