package com.simibubi.create.content.kinetics.deployer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeployerBlockEntity.Mode.class)
public class DeployerModeMixin {
    @Shadow
    @Final
    @Mutable
    private static DeployerBlockEntity.Mode[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void deployerhold$addHoldMode(CallbackInfo ci) {
        DeployerBlockEntity.Mode hold = DeployerModeInvoker.deployerhold$create("HOLD", $VALUES.length);
        DeployerBlockEntity.Mode[] expanded = new DeployerBlockEntity.Mode[$VALUES.length + 1];
        System.arraycopy($VALUES, 0, expanded, 0, $VALUES.length);
        expanded[$VALUES.length] = hold;
        $VALUES = expanded;
        DeployerHoldModes.HOLD = hold;
    }
}
