package com.feima.movemod.network;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.PeekAction;
import com.feima.movemod.action.SlideAction;
import com.feima.movemod.action.StaminaTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class NetworkHandler {

    private NetworkHandler() {}

    /**
     * 协议版本：
     *   - 5 → 6：DiveStatePacket 增加 fromAir 字段
     *   - 6 → 7：SlideStatePacket 增加 seq 字段
     *   - 7 → 8：移除空中飞扑，DiveStatePacket 删除 fromAir 字段
     *   - 8 → 9：DiveStatePacket / PeekStatePacket 增加 stamina 字段
     */
    private static final String PROTOCOL = "9";

    private static final AtomicLong SLIDE_BROADCAST_SEQ = new AtomicLong();

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FeimaMoveMod.MODID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL::equals),
            NetworkRegistry.acceptMissingOr(PROTOCOL::equals)
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SlidePacket.class,
                SlidePacket::encode, SlidePacket::decode, SlidePacket::handle);
        CHANNEL.registerMessage(id++, SlideStatePacket.class,
                SlideStatePacket::encode, SlideStatePacket::decode, SlideStatePacket::handle);
        CHANNEL.registerMessage(id++, SlideJumpPacket.class,
                SlideJumpPacket::encode, SlideJumpPacket::decode, SlideJumpPacket::handle);
        CHANNEL.registerMessage(id++, ProneSetPacket.class,
                ProneSetPacket::encode, ProneSetPacket::decode, ProneSetPacket::handle);
        CHANNEL.registerMessage(id++, ProneStatePacket.class,
                ProneStatePacket::encode, ProneStatePacket::decode, ProneStatePacket::handle);
        CHANNEL.registerMessage(id++, PeekSetPacket.class,
                PeekSetPacket::encode, PeekSetPacket::decode, PeekSetPacket::handle);
        CHANNEL.registerMessage(id++, PeekStatePacket.class,
                PeekStatePacket::encode, PeekStatePacket::decode, PeekStatePacket::handle);
        CHANNEL.registerMessage(id++, DivePacket.class,
                DivePacket::encode, DivePacket::decode, DivePacket::handle);
        CHANNEL.registerMessage(id++, DiveStatePacket.class,
                DiveStatePacket::encode, DiveStatePacket::decode, DiveStatePacket::handle);
    }

    // ============================================================
    // C2S
    // ============================================================
    public static void sendSlide() {
        CHANNEL.sendToServer(new SlidePacket());
    }

    public static void sendSlideJump() {
        CHANNEL.sendToServer(new SlideJumpPacket());
    }

    public static void sendProneSet(boolean prone) {
        CHANNEL.sendToServer(new ProneSetPacket(prone));
    }

    public static void sendPeekSet(PeekAction.Dir dir) {
        CHANNEL.sendToServer(new PeekSetPacket(dir));
    }

    public static void sendDive() {
        CHANNEL.sendToServer(new DivePacket());
    }

    // ============================================================
    // S2C
    // ============================================================
    public static void broadcastSlideState(Entity entity, boolean sliding) {
        double stamina = 0.0D;
        double speed = 0.0D;
        if (entity instanceof Player p) {
            stamina = StaminaTracker.INSTANCE.get(p);
            if (sliding) {
                speed = SlideAction.INSTANCE.currentSpeed(p);
            }
        }
        long seq = SLIDE_BROADCAST_SEQ.incrementAndGet();
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new SlideStatePacket(entity.getUUID(), sliding, stamina, speed, false, seq)
        );
    }

    public static void sendSlideReject(ServerPlayer player) {
        UUID id = player.getUUID();
        double stamina = StaminaTracker.INSTANCE.get(player);
        long seq = SLIDE_BROADCAST_SEQ.incrementAndGet();
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SlideStatePacket(id, false, stamina, 0.0D, true, seq)
        );
    }

    public static void broadcastProneState(Entity entity, boolean prone) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new ProneStatePacket(entity.getUUID(), prone)
        );
    }

    public static void sendProneReject(ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ProneStatePacket(player.getUUID(), false)
        );
    }

    public static void broadcastPeekState(Entity entity, PeekAction.Dir dir) {
        double stamina = 0.0D;
        if (entity instanceof Player p) {
            stamina = StaminaTracker.INSTANCE.get(p);
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new PeekStatePacket(entity.getUUID(), dir, false, stamina)
        );
    }

    public static void sendPeekReject(ServerPlayer player) {
        double stamina = StaminaTracker.INSTANCE.get(player);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PeekStatePacket(player.getUUID(), PeekAction.Dir.NONE, true, stamina)
        );
    }

    public static void broadcastDiveState(Entity entity, boolean diving) {
        double stamina = 0.0D;
        if (entity instanceof Player p) {
            stamina = StaminaTracker.INSTANCE.get(p);
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new DiveStatePacket(entity.getUUID(), diving, false, stamina)
        );
    }

    public static void sendDiveReject(ServerPlayer player) {
        double stamina = StaminaTracker.INSTANCE.get(player);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new DiveStatePacket(player.getUUID(), false, true, stamina)
        );
    }
}