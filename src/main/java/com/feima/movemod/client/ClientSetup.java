package com.feima.movemod.client;

import com.feima.movemod.FeimaMoveMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = FeimaMoveMod.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.SLIDE);
        event.register(KeyBindings.CRAWL);
        event.register(KeyBindings.PEEK_LEFT);
        event.register(KeyBindings.PEEK_RIGHT);
    }
}