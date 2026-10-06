package com.feima.movemod.action;

import com.feima.movemod.client.SlideClientHelper;
import com.feima.movemod.config.MoveConfig;
import com.feima.movemod.network.NetworkHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProneAction {

    public static final ProneAction INSTANCE = new ProneAction();

    private final Set<UUID> serverProne = ConcurrentHashMap.newKeySet();
    private final Set<UUID> clientProne = ConcurrentHashMap.newKeySet();

    private ProneAction() {}

    private Set<UUID> data(Player p) {
        return p.level().isClientSide ? clientProne : serverProne;
    }

    public boolean isProne(Player player) {
        return data(player).contains(player.getUUID());
    }

    // ---------------- 服务端 ----------------
    public boolean tryStart(Player player) {
        if (player.level().isClientSide) return false;
        if (isProne(player)) return false;
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
        if (ActionExclusivity.isAnyOtherActive(player, ActionExclusivity.Action.PRONE)) {
            return false;
        }
        return true;
    }

    private boolean passiveAllowed(Player player) {
        if (!MoveConfig.INSTANCE.proneEnabled.get()) return false;
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        return true;
    }

    // ---------------- 客户端预测 ----------------
    public boolean tryStartClient(Player player) {
        if (!player.level().isClientSide) return false;
        if (isProne(player)) return false;
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
    public void tick(Player player) {
        if (!isProne(player)) return;
        if (passiveAllowed(player)) return;

        if (player.level().isClientSide) {
            if (SlideClientHelper.isLocalPlayer(player)) {
                stopClient(player);
                NetworkHandler.sendProneSet(false);
            }
        } else {
            stop(player);
            NetworkHandler.broadcastProneState(player, false);
        }
    }

    // ---------------- 远端同步 ----------------
    public void applyRemoteState(Player player, boolean prone) {
        if (!player.level().isClientSide) return;
        boolean changed = prone
                ? data(player).add(player.getUUID())
                : data(player).remove(player.getUUID());
        if (changed) player.refreshDimensions();

        if (prone) {
            ActionExclusivity.stopOthers(player, ActionExclusivity.Action.PRONE);
        }
    }

    public void forget(UUID id) {
        serverProne.remove(id);
        clientProne.remove(id);
    }

    public boolean forcePose(Player player) {
        if (!isProne(player)) return false;
        if (!MoveConfig.INSTANCE.proneEnabled.get()) return false;
        if (ActionExclusivity.isAnyOtherActive(player, ActionExclusivity.Action.PRONE)) {
            return false;
        }

        if (player.getPose() != Pose.SWIMMING) {
            player.setPose(Pose.SWIMMING);
            player.refreshDimensions();
        }
        return true;
    }
}