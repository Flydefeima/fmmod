package com.feima.movemod.network;

import com.feima.movemod.action.PeekAction;
import com.feima.movemod.action.StaminaTracker;
import com.feima.movemod.client.SlideClientHelper;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步某个玩家的探头方向 + 权威耐力。
 *
 * <p><b>为什么要带 stamina</b>：探头每 tick 消耗耐力，服务端先耗尽
 * 时会广播 {@code dir = NONE}；但客户端本地耐力若仍 > 0，下一次 tick
 * 会重新 {@code trySet} 并发请求，被服务端拒绝后再清零，形成
 * 「伸出 → 收回 → 再伸出」的每 tick 抽搐。携带权威耐力后，客户端本地
 * 副本持续跟随服务端，reject 时更是直接归零，不再反复重试。
 */
public class PeekStatePacket {

    private final UUID playerId;
    private final PeekAction.Dir dir;
    private final boolean rejected;
    private final double stamina;

    public PeekStatePacket(UUID playerId, PeekAction.Dir dir, boolean rejected, double stamina) {
        this.playerId = playerId;
        this.dir = dir;
        this.rejected = rejected;
        this.stamina = stamina;
    }

    public static void encode(PeekStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeEnum(msg.dir);
        buf.writeBoolean(msg.rejected);
        buf.writeDouble(msg.stamina);
    }

    public static PeekStatePacket decode(FriendlyByteBuf buf) {
        return new PeekStatePacket(
                buf.readUUID(),
                buf.readEnum(PeekAction.Dir.class),
                buf.readBoolean(),
                buf.readDouble()
        );
    }

    public static void handle(PeekStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg))
        );
        context.setPacketHandled(true);
    }

    private static void handleClient(PeekStatePacket msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Player player = level.getPlayerByUUID(msg.playerId);
        if (player == null) return;

        // 本地玩家的耐力跟随权威值
        if (SlideClientHelper.isLocalPlayer(player)
                && MoveConfig.INSTANCE.staminaEnabled.get()) {
            StaminaTracker.INSTANCE.set(player, msg.stamina);
        }

        PeekAction.INSTANCE.applyRemoteState(player, msg.dir);
    }
}