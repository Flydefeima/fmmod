package com.feima.movemod.network;

import com.feima.movemod.action.PeekAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：请求设置探头方向。 */
public class PeekSetPacket {

    private final PeekAction.Dir dir;

    public PeekSetPacket(PeekAction.Dir dir) {
        this.dir = dir;
    }

    public static void encode(PeekSetPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.dir);
    }

    public static PeekSetPacket decode(FriendlyByteBuf buf) {
        return new PeekSetPacket(buf.readEnum(PeekAction.Dir.class));
    }

    public static void handle(PeekSetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            if (PeekAction.INSTANCE.dir(player) == msg.dir) return;

            if (PeekAction.INSTANCE.trySet(player, msg.dir)) {
                NetworkHandler.broadcastPeekState(player, msg.dir);
            } else {
                NetworkHandler.sendPeekReject(player);
            }
        });
        context.setPacketHandled(true);
    }
}