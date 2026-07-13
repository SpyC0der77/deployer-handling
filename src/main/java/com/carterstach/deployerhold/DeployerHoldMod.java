package com.carterstach.deployerhold;

import com.carterstach.deployerhold.client.DeployerHoldClient;
import com.carterstach.deployerhold.config.DeployerHoldConfigs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DeployerHoldMod.ID)
public class DeployerHoldMod {
    public static final String ID = "deployerhold";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public DeployerHoldMod(IEventBus modBus, ModContainer container) {
        DeployerHoldConfigs.register(container);
        modBus.addListener(this::onCommonSetup);
        // Single @Mod entry: client setup is gated so DeployerHoldClient is not loaded on dedicated servers.
        if (FMLEnvironment.dist == Dist.CLIENT)
            DeployerHoldClient.init(modBus);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Deployer Hold ready — Deployers can cycle into Grip: Pull / Grip: Hitch for Simulated handles.");
    }
}
