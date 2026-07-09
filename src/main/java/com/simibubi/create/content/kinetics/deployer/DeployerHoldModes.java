package com.simibubi.create.content.kinetics.deployer;

/**
 * Runtime-injected grip modes living beside Create's package-private {@link DeployerBlockEntity.Mode}.
 * Populated by {@link DeployerModeMixin}.
 */
public final class DeployerHoldModes {
    /** Deployer's sub-level pulls the handle's sub-level (player shift-grab). */
    public static DeployerBlockEntity.Mode HOLD_PULL;
    /** Deployer's sub-level follows / rides the handle's sub-level (player no-shift grab). */
    public static DeployerBlockEntity.Mode HOLD_HITCH;

    private DeployerHoldModes() {}

    public static boolean isGrip(DeployerBlockEntity.Mode mode) {
        return isPull(mode) || isHitch(mode);
    }

    public static boolean isPull(DeployerBlockEntity.Mode mode) {
        return HOLD_PULL != null && mode == HOLD_PULL;
    }

    public static boolean isHitch(DeployerBlockEntity.Mode mode) {
        return HOLD_HITCH != null && mode == HOLD_HITCH;
    }

    /**
     * Wrench cycle: Use → Grip: Pull → Grip: Hitch → Attack → Use…
     */
    public static DeployerBlockEntity.Mode next(DeployerBlockEntity.Mode current) {
        if (current == DeployerBlockEntity.Mode.PUNCH)
            return DeployerBlockEntity.Mode.USE;
        if (current == DeployerBlockEntity.Mode.USE)
            return HOLD_PULL != null ? HOLD_PULL : DeployerBlockEntity.Mode.PUNCH;
        if (isPull(current))
            return HOLD_HITCH != null ? HOLD_HITCH : DeployerBlockEntity.Mode.PUNCH;
        return DeployerBlockEntity.Mode.PUNCH;
    }

    public static String tooltipKey(DeployerBlockEntity.Mode mode) {
        if (isPull(mode))
            return "deployerhold.tooltip.deployer.grip_pull";
        if (isHitch(mode))
            return "deployerhold.tooltip.deployer.grip_hitch";
        return null;
    }
}
