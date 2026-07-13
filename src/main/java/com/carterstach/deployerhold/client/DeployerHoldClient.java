package com.carterstach.deployerhold.client;

import com.carterstach.deployerhold.DeployerHoldMod;
import com.carterstach.deployerhold.ponder.DeployerHoldPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = DeployerHoldMod.ID, dist = Dist.CLIENT)
public class DeployerHoldClient {
    public DeployerHoldClient(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new DeployerHoldPonderPlugin());
    }
}
