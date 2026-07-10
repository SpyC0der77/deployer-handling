package com.carterstach.deployerhold.mixin;

import com.carterstach.deployerhold.DeployerHoldModes;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grip modes must never punch or right-click — latching is handled on the block entity.
 */
@Mixin(value = DeployerHandler.class, remap = false)
public class DeployerHandlerMixin {
    @Inject(
            method = "activate",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void deployerhold$skipGripActivate(
            DeployerFakePlayer player,
            Vec3 vec,
            BlockPos clickedPos,
            Vec3 extensionVector,
            @Coerce Object mode,
            CallbackInfo ci
    ) {
        if (DeployerHoldModes.isGrip(mode))
            ci.cancel();
    }
}
