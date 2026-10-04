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

public final class NetworkHandler {

    private NetworkHandler() {}

    /** 协议版本：新增 Peek 包，从 2 升到 3。 */
    private static final String PROTOCOL = "3";

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
        CHANNEL.registerMessage(id++, CrawlSetPacket.class,
                CrawlSetPacket::encode, CrawlSetPacket::decode, CrawlSetPacket::handle);
        CHANNEL.registerMessage(id++, CrawlStatePacket.class,
                CrawlStatePacket::encode, CrawlStatePacket::decode, CrawlStatePacket::handle);
        CHANNEL.registerMessage(id++, PeekSetPacket.class,
                PeekSetPacket::encode, PeekSetPacket::decode, PeekSetPacket::handle);
        CHANNEL.registerMessage(id++, PeekStatePacket.class,
                PeekStatePacket::encode, PeekStatePacket::decode, PeekStatePacket::handle);
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

    public static void sendCrawlSet(boolean crawling) {
        CHANNEL.sendToServer(new CrawlSetPacket(crawling));
    }

    public static void sendPeekSet(PeekAction.Dir dir) {
        CHANNEL.sendToServer(new PeekSetPacket(dir));
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
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new SlideStatePacket(entity.getUUID(), sliding, stamina, speed, false)
        );
    }

    public static void sendSlideReject(ServerPlayer player) {
        UUID id = player.getUUID();
        double stamina = StaminaTracker.INSTANCE.get(player);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SlideStatePacket(id, false, stamina, 0.0D, true)
        );
    }

    public static void broadcastCrawlState(Entity entity, boolean crawling) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new CrawlStatePacket(entity.getUUID(), crawling)
        );
    }

    public static void sendCrawlReject(ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CrawlStatePacket(player.getUUID(), false)
        );
    }

    public static void broadcastPeekState(Entity entity, PeekAction.Dir dir) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new PeekStatePacket(entity.getUUID(), dir, false)
        );
    }

    public static void sendPeekReject(ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PeekStatePacket(player.getUUID(), PeekAction.Dir.NONE, true)
        );
    }
}