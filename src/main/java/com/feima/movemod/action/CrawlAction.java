package com.feima.movemod.action;

import com.feima.movemod.client.SlideClientHelper;
import com.feima.movemod.config.MoveConfig;
import com.feima.movemod.network.NetworkHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrawlAction {

    public static final CrawlAction INSTANCE = new CrawlAction();

    private final Set<UUID> serverCrawling = ConcurrentHashMap.newKeySet();
    private final Set<UUID> clientCrawling = ConcurrentHashMap.newKeySet();

    private CrawlAction() {}

    private Set<UUID> data(Player p) {
        return p.level().isClientSide ? clientCrawling : serverCrawling;
    }

    public boolean isCrawling(Player player) {
        return data(player).contains(player.getUUID());
    }

    // ---------------- 服务端 ----------------
    public boolean tryStart(Player player) {
        if (player.level().isClientSide) return false;
        if (isCrawling(player)) return false;
        if (!canStart(player)) return false;
        data(player).add(player.getUUID());
        player.refreshDimensions();
        return true;
    }

    public void stop(Player player) {
        if (!data(player).remove(player.getUUID())) return;
        player.refreshDimensions();
    }

    private boolean canStart(Player player) {
        if (!passiveAllowed(player)) return false;
        if (!player.onGround()) return false;
        if (SlideAction.INSTANCE.isSliding(player)) return false;
        if (PeekAction.INSTANCE.isPeeking(player)) return false;
        return true;
    }

    /** 与启动条件共享的"被动合法性"检查：开关、状态、环境。 */
    private boolean passiveAllowed(Player player) {
        if (!MoveConfig.INSTANCE.enabled.get()) return false;
        if (!MoveConfig.INSTANCE.crawlEnabled.get()) return false;
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        return true;
    }

    // ---------------- 客户端预测 ----------------
    public boolean tryStartClient(Player player) {
        if (!player.level().isClientSide) return false;
        if (isCrawling(player)) return false;
        if (!canStart(player)) return false;
        data(player).add(player.getUUID());
        player.refreshDimensions();
        return true;
    }

    public void stopClient(Player player) {
        if (!data(player).remove(player.getUUID())) return;
        player.refreshDimensions();
    }

    // ---------------- 运行期 ----------------
    /**
     * 每 tick 由 {@code PlayerSlideMixin} 调用。
     *
     * <p>原版 {@code updatePlayerPose} 被 cancel 期间，入水 / 上船 / 鞘翅等
     * 条件变化时不会自动解除趴下姿态。这里补上运行期检测：
     * 一旦不再允许趴下，就主动 stop 并同步。
     *
     * <p>客户端只对本地玩家预测退出（远端玩家以服务端广播为准）。
     */
    public void tick(Player player) {
        if (!isCrawling(player)) return;
        if (passiveAllowed(player)) return;

        if (player.level().isClientSide) {
            if (SlideClientHelper.isLocalPlayer(player)) {
                stopClient(player);
                NetworkHandler.sendCrawlSet(false);
            }
        } else {
            stop(player);
            NetworkHandler.broadcastCrawlState(player, false);
        }
    }

    // ---------------- 远端同步 ----------------
    public void applyRemoteState(Player player, boolean crawling) {
        if (!player.level().isClientSide) return;
        boolean changed = crawling
                ? data(player).add(player.getUUID())
                : data(player).remove(player.getUUID());
        if (changed) player.refreshDimensions();
    }

    public void forget(UUID id) {
        serverCrawling.remove(id);
        clientCrawling.remove(id);
    }

    public boolean forcePose(Player player) {
        if (!isCrawling(player)) return false;
        if (!MoveConfig.INSTANCE.enabled.get()) return false;
        if (!MoveConfig.INSTANCE.crawlEnabled.get()) return false;
        if (SlideAction.INSTANCE.isSliding(player)) return false;

        if (player.getPose() != Pose.SWIMMING) {
            player.setPose(Pose.SWIMMING);
            player.refreshDimensions();
        }
        return true;
    }
}