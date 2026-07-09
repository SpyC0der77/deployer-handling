package com.simibubi.create.content.kinetics.deployer;

/**
 * Lives in Create's deployer package so the package-private {@link DeployerBlockEntity.Mode}
 * enum is accessible at compile time. HOLD is injected by mixin at class init.
 */
public final class DeployerHoldModes {
    public static DeployerBlockEntity.Mode HOLD;

    private DeployerHoldModes() {}

    public static boolean isHold(DeployerBlockEntity.Mode mode) {
        return HOLD != null && mode == HOLD;
    }

    public static DeployerBlockEntity.Mode next(DeployerBlockEntity.Mode current) {
        if (current == DeployerBlockEntity.Mode.PUNCH)
            return DeployerBlockEntity.Mode.USE;
        if (current == DeployerBlockEntity.Mode.USE)
            return HOLD != null ? HOLD : DeployerBlockEntity.Mode.PUNCH;
        return DeployerBlockEntity.Mode.PUNCH;
    }
}
