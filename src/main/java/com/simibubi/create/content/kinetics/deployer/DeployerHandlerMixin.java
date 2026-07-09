package com.simibubi.create.content.kinetics.deployer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hold mode must never punch or right-click — grabbing is handled on the block entity.
 */
@Mixin(DeployerHandler.class)
public class DeployerHandlerMixin {
    @Inject(
            method = "activate",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void deployerhold$skipHoldActivate(
            DeployerFakePlayer player,
            Vec3 vec,
            BlockPos clickedPos,
            Vec3 extensionVector,
            DeployerBlockEntity.Mode mode,
            CallbackInfo ci
    ) {
        if (DeployerHoldModes.isHold(mode))
            ci.cancel();
    }
}
