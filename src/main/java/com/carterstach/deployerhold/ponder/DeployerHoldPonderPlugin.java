package com.carterstach.deployerhold.ponder;

import com.carterstach.deployerhold.DeployerHoldMod;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class DeployerHoldPonderPlugin implements PonderPlugin {
    private static final ResourceLocation DEPLOYER = ResourceLocation.fromNamespaceAndPath("create", "deployer");
    /** Own schematic id so we do not collide with Create's {@code create:deployer/modes} storyboard. */
    private static final ResourceLocation SCHEMATIC = DeployerHoldMod.asResource("deployer_grip");

    @Override
    public String getModId() {
        return DeployerHoldMod.ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(DEPLOYER, SCHEMATIC, DeployerHoldScenes::grip, AllCreatePonderTags.KINETIC_APPLIANCES)
                .orderAfter("create", "deployer/contraption");
    }
}
