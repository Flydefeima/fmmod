package com.feima.movemod.network;

import com.feima.movemod.action.DiveAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：请求触发飞扑。 */
public class DivePacket {

    public DivePacket() {}

    public static void encode(DivePacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static DivePacket decode(FriendlyByteBuf buf) {
        return new DivePacket();
    }

    public static void handle(DivePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (DiveAction.INSTANCE.isDiving(player)) return;

            if (DiveAction.INSTANCE.tryStart(player)) {
                // 权威启动成功 → commitStart 内部已广播
            } else {
                NetworkHandler.sendDiveReject(player);
            }
        });
        context.setPacketHandled(true);
    }
}