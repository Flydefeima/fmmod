package com.feima.movemod.network;

import com.feima.movemod.action.DiveAction;
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
 * 服务端 → 客户端：同步某个玩家的飞扑状态 + 权威耐力。
 *
 * <p><b>为什么要带 stamina</b>：飞扑的每 tick 耐力消耗由服务端与本地
 * 客户端各自独立执行，两者副本在临界值上会短暂偏差。当服务端耐力先
 * 归零、客户端仍 > 0 时，客户端会误判 `canDive` 通过 → 本地预测
 * `commitStart`（施加初速 + 关疾跑）→ 发请求 → 服务端拒绝 → 客户端
 * 已飞出去一小段但无动画，且因状态已清、WASD 输入恢复，距离骤减。
 *
 * <p>把权威耐力随每次广播带回，让客户端本地副本持续跟随服务端，
 * 从根源上压缩偏差窗口；reject 包更是直接携带服务端当前耐力，客户端
 * 收到即覆盖本地值，不再反复重试。
 */
public class DiveStatePacket {

    private final UUID playerId;
    private final boolean diving;
    private final boolean rejected;
    private final double stamina;

    public DiveStatePacket(UUID playerId, boolean diving, boolean rejected, double stamina) {
        this.playerId = playerId;
        this.diving = diving;
        this.rejected = rejected;
        this.stamina = stamina;
    }

    public static void encode(DiveStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.diving);
        buf.writeBoolean(msg.rejected);
        buf.writeDouble(msg.stamina);
    }

    public static DiveStatePacket decode(FriendlyByteBuf buf) {
        return new DiveStatePacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readDouble()
        );
    }

    public static void handle(DiveStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg))
        );
        context.setPacketHandled(true);
    }

    private static void handleClient(DiveStatePacket msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Player player = level.getPlayerByUUID(msg.playerId);
        if (player == null) return;

        // 本地玩家的耐力跟随权威值，避免本地预测与服务端持续偏差
        if (SlideClientHelper.isLocalPlayer(player)
                && MoveConfig.INSTANCE.staminaEnabled.get()) {
            StaminaTracker.INSTANCE.set(player, msg.stamina);
        }

        DiveAction.INSTANCE.applyRemoteState(player, msg.diving, msg.rejected);
    }
}