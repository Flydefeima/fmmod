package com.feima.movemod.network;

import com.feima.movemod.action.ProneAction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 服务端 → 客户端：同步某个玩家的趴下状态。 */
public class ProneStatePacket {

    private final UUID playerId;
    private final boolean prone;

    public ProneStatePacket(UUID playerId, boolean prone) {
        this.playerId = playerId;
        this.prone = prone;
    }

    public static void encode(ProneStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.prone);
    }

    public static ProneStatePacket decode(FriendlyByteBuf buf) {
        return new ProneStatePacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ProneStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg))
        );
        context.setPacketHandled(true);
    }

    private static void handleClient(ProneStatePacket msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Player player = level.getPlayerByUUID(msg.playerId);
        if (player == null) return;
        ProneAction.INSTANCE.applyRemoteState(player, msg.prone);
    }
}