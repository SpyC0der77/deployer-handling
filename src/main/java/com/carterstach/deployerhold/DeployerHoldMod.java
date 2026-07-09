package com.carterstach.deployerhold;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DeployerHoldMod.ID)
public class DeployerHoldMod {
    public static final String ID = "deployerhold";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public DeployerHoldMod(IEventBus modBus) {
        modBus.addListener(this::onCommonSetup);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Deployer Hold ready — Deployers can cycle into Grip: Pull / Grip: Hitch for Simulated handles.");
    }
}
