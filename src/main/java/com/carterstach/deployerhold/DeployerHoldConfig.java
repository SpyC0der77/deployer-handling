package com.carterstach.deployerhold;

import com.carterstach.deployerhold.config.DHServer;
import com.carterstach.deployerhold.config.DeployerHoldConfigs;

/**
 * Accessors for server config (grab range, tip search, grip anchor, rider constraint).
 * Editable in Create → Mod Config → Access Configs of other Mods → Deployer Hold.
 * File: {@code config/deployerhold-server.toml}
 */
public final class DeployerHoldConfig {
    private DeployerHoldConfig() {}

    private static DHServer server() {
        return DeployerHoldConfigs.server();
    }

    public static double grabTolerance() {
        return server().grabTolerance.getF();
    }

    public static double holdRange() {
        return server().holdRange.getF();
    }

    public static double tipDistance() {
        return server().tipDistance.getF();
    }

    public static int tipSearchRadius() {
        return server().tipSearchRadius.get();
    }

    /** World-space offset from block center to the animated grip tip along facing. */
    public static double gripTipOffset(double animatedReach) {
        DHServer config = server();
        return config.faceOffset.getF()
                + animatedReach
                + config.handLength.getF()
                - config.pullIn.getF();
    }

    /** Rider linear stiffness — higher values pull the rider toward the anchor faster. */
    public static double constraintStiffness() {
        return server().stiffness.getF();
    }

    public static double constraintDamping() {
        return server().damping.getF();
    }

    /** Facing-alignment stiffness (pitch/yaw toward facing each other). Twist around facing is not driven. */
    public static double constraintAngularStiffness() {
        return server().angularStiffness.getF();
    }

    public static double constraintAngularDamping() {
        return server().angularDamping.getF();
    }
}
