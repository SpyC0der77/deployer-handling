package com.simibubi.create.content.kinetics.deployer;


/**
 * Duck interface applied to {@link DeployerBlockEntity} by mixin.
 */
public interface DeployerHoldAccess {
    DeployerHoldController deployerhold$getHoldController();

    static DeployerHoldController get(DeployerBlockEntity deployer) {
        return ((DeployerHoldAccess) deployer).deployerhold$getHoldController();
    }
}
