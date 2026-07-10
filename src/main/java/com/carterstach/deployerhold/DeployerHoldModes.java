package com.carterstach.deployerhold;

/**
 * Runtime-injected grip modes for Create's deployer Mode enum.
 * Values are {@code DeployerBlockEntity.Mode} instances (typed as Object to avoid
 * a split-package with Create under JPMS). Populated by DeployerModeMixin.
 */
public final class DeployerHoldModes {
    /** Pull the handle's sub-level toward the retracting tip. */
    public static Object HOLD_PULL;
    /** Pull the deployer's sub-level toward the handle while retracting. */
    public static Object HOLD_HITCH;

    private DeployerHoldModes() {}

    public static boolean isGrip(Object mode) {
        return isPull(mode) || isHitch(mode);
    }

    public static boolean isPull(Object mode) {
        return HOLD_PULL != null && mode == HOLD_PULL;
    }

    public static boolean isHitch(Object mode) {
        return HOLD_HITCH != null && mode == HOLD_HITCH;
    }

    /**
     * Wrench cycle: Use → Grip: Pull → Grip: Hitch → Attack → Use…
     */
    public static Object next(Object current) {
        String name = current instanceof Enum<?> e ? e.name() : String.valueOf(current);
        if ("PUNCH".equals(name))
            return modeNamed("USE");
        if ("USE".equals(name))
            return HOLD_PULL != null ? HOLD_PULL : modeNamed("PUNCH");
        if (isPull(current))
            return HOLD_HITCH != null ? HOLD_HITCH : modeNamed("PUNCH");
        return modeNamed("PUNCH");
    }

    public static String tooltipKey(Object mode) {
        if (isPull(mode))
            return "deployerhold.tooltip.deployer.grip_pull";
        if (isHitch(mode))
            return "deployerhold.tooltip.deployer.grip_hitch";
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object modeNamed(String name) {
        Class type = HOLD_PULL != null ? HOLD_PULL.getClass()
                : HOLD_HITCH != null ? HOLD_HITCH.getClass()
                : null;
        if (type == null)
            throw new IllegalStateException("Grip modes not injected yet");
        return Enum.valueOf(type, name);
    }
}
