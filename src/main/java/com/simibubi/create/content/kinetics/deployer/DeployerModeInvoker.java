package com.simibubi.create.content.kinetics.deployer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DeployerBlockEntity.Mode.class)
public interface DeployerModeInvoker {
    @Invoker("<init>")
    static DeployerBlockEntity.Mode deployerhold$create(String internalName, int internalId) {
        throw new AssertionError();
    }
}
