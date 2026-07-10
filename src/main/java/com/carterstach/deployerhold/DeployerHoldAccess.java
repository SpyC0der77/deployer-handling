package com.carterstach.deployerhold;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Duck interface applied to {@link DeployerBlockEntity} by mixin.
 * Field accessors use Object for Create's package-private Mode/State enums.
 */
public interface DeployerHoldAccess {
    DeployerHoldController deployerhold$getHoldController();

    Object deployerhold$getMode();

    void deployerhold$setMode(Object mode);

    Object deployerhold$getState();

    void deployerhold$setState(Object state);

    int deployerhold$getTimer();

    void deployerhold$setTimer(int timer);

    float deployerhold$getReach();

    void deployerhold$setReach(float reach);

    boolean deployerhold$isRedstoneLocked();

    ItemStack deployerhold$getHeldItem();

    int deployerhold$getTimerSpeed();

    static DeployerHoldAccess of(DeployerBlockEntity deployer) {
        return (DeployerHoldAccess) deployer;
    }

    static DeployerHoldController get(DeployerBlockEntity deployer) {
        return of(deployer).deployerhold$getHoldController();
    }
}
