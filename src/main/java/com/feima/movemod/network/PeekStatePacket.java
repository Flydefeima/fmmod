package com.feima.movemod.network;

import com.feima.movemod.action.PeekAction;
import com.feima.movemod.client.SlideClientHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 服务端 → 客户端：同步某个玩家的探头方向。 */
public class PeekStatePacket {

    private final UUID playerId;
    private final PeekAction.Dir dir;
    private final boolean rejected;

    public PeekStatePacket(UUID playerId, PeekAction.Dir dir, boolean rejected) {
        this.playerId = playerId;
        this.dir = dir;
        this.rejected = rejected;
    }

    public static void encode(PeekStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeEnum(msg.dir);
        buf.writeBoolean(msg.rejected);
    }

    public static PeekStatePacket decode(FriendlyByteBuf buf) {
        return new PeekStatePacket(
                buf.readUUID(),
                buf.readEnum(PeekAction.Dir.class),
                buf.readBoolean()
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

        // 用权威路径，避免 trySet 里的 canPeek 因客户端预测滞后而误拒
        PeekAction.INSTANCE.applyRemoteState(player, msg.dir);

        // 本地玩家被拒 → 服务端已明确不接受当前请求。
        // 目前没有本地缓冲，保留占位以便未来扩展。
        if (msg.rejected && SlideClientHelper.isLocalPlayer(player)) {
            // no-op
        }
    }
}