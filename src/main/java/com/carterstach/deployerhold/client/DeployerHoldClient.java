package com.carterstach.deployerhold.client;

import com.carterstach.deployerhold.ponder.DeployerHoldPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only bootstrap. Not a second {@code @Mod} entry — registered from
 * {@link com.carterstach.deployerhold.DeployerHoldMod} when running on the client.
 */
@OnlyIn(Dist.CLIENT)
public final class DeployerHoldClient {
    private DeployerHoldClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(DeployerHoldClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new DeployerHoldPonderPlugin());
    }
}
