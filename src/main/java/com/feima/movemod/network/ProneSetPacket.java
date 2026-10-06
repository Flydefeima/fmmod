package com.feima.movemod.network;

import com.feima.movemod.action.ProneAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：请求设置趴下状态。 */
public class ProneSetPacket {

    private final boolean prone;

    public ProneSetPacket(boolean prone) {
        this.prone = prone;
    }

    public static void encode(ProneSetPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.prone);
    }

    public static ProneSetPacket decode(FriendlyByteBuf buf) {
        return new ProneSetPacket(buf.readBoolean());
    }

    public static void handle(ProneSetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            boolean cur = ProneAction.INSTANCE.isProne(player);
            if (msg.prone == cur) return; // 幂等

            if (msg.prone) {
                if (ProneAction.INSTANCE.tryStart(player)) {
                    NetworkHandler.broadcastProneState(player, true);
                } else {
                    NetworkHandler.sendProneReject(player);
                }
            } else {
                ProneAction.INSTANCE.stop(player);
                NetworkHandler.broadcastProneState(player, false);
            }
        });
        context.setPacketHandled(true);
    }
}