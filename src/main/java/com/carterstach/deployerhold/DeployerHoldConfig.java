package com.carterstach.deployerhold;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config for grab range, tip search, grip anchor, and constraint feel.
 * File: {@code config/deployerhold-server.toml}
 */
public final class DeployerHoldConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue GRAB_TOLERANCE;
    public static final ModConfigSpec.DoubleValue HOLD_RANGE;
    public static final ModConfigSpec.DoubleValue TIP_DISTANCE;
    public static final ModConfigSpec.IntValue TIP_SEARCH_RADIUS;

    public static final ModConfigSpec.DoubleValue GRIP_FACE_OFFSET;
    public static final ModConfigSpec.DoubleValue GRIP_HAND_LENGTH;
    public static final ModConfigSpec.DoubleValue GRIP_PULL_IN;

    public static final ModConfigSpec.DoubleValue CONSTRAINT_STIFFNESS;
    public static final ModConfigSpec.DoubleValue CONSTRAINT_DAMPING;
    public static final ModConfigSpec.DoubleValue CONSTRAINT_ANGULAR_DAMPING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("grab");
        GRAB_TOLERANCE = builder
                .comment("Max distance from the deployer tip to a handle grab center when latching.")
                .defineInRange("grabTolerance", 2.75, 0.25, 8.0);
        HOLD_RANGE = builder
                .comment("Max tip-to-handle distance while a grip is held before it drops.")
                .defineInRange("holdRange", 5.0, 1.0, 16.0);
        TIP_DISTANCE = builder
                .comment("How far along facing (from block center) the search tip sits when fully extended.")
                .defineInRange("tipDistance", 2.25, 0.5, 6.0);
        TIP_SEARCH_RADIUS = builder
                .comment("Half-size of the block cube searched around the tip for handles (in blocks).")
                .defineInRange("tipSearchRadius", 2, 0, 4);
        builder.pop();

        builder.push("gripAnchor");
        GRIP_FACE_OFFSET = builder
                .comment("Distance from deployer block center to the front face along facing (Create uses 0.5).")
                .defineInRange("faceOffset", 0.5, 0.0, 2.0);
        GRIP_HAND_LENGTH = builder
                .comment("Holding-hand length added to the tip (Create holding pose uses 4/16 = 0.25).")
                .defineInRange("handLength", 0.25, 0.0, 1.0);
        GRIP_PULL_IN = builder
                .comment("Extra pull toward the deployer so the handle sits flush with the visual tip.")
                .defineInRange("pullIn", 0.2, 0.0, 1.5);
        builder.pop();

        builder.push("constraint");
        CONSTRAINT_STIFFNESS = builder
                .comment("Linear motor stiffness for the grip constraint.")
                .defineInRange("stiffness", 240.0, 1.0, 2000.0);
        CONSTRAINT_DAMPING = builder
                .comment("Linear motor damping for the grip constraint.")
                .defineInRange("damping", 30.0, 0.0, 500.0);
        CONSTRAINT_ANGULAR_DAMPING = builder
                .comment("Angular motor damping (keeps the gripped body from spinning wildly).")
                .defineInRange("angularDamping", 4.5, 0.0, 100.0);
        builder.pop();

        SPEC = builder.build();
    }

    private DeployerHoldConfig() {}

    public static double grabTolerance() {
        return GRAB_TOLERANCE.getAsDouble();
    }

    public static double holdRange() {
        return HOLD_RANGE.getAsDouble();
    }

    public static double tipDistance() {
        return TIP_DISTANCE.getAsDouble();
    }

    public static int tipSearchRadius() {
        return TIP_SEARCH_RADIUS.getAsInt();
    }

    /** World-space offset from block center to the animated grip tip along facing. */
    public static double gripTipOffset(double animatedReach) {
        return GRIP_FACE_OFFSET.getAsDouble()
                + animatedReach
                + GRIP_HAND_LENGTH.getAsDouble()
                - GRIP_PULL_IN.getAsDouble();
    }

    public static double constraintStiffness() {
        return CONSTRAINT_STIFFNESS.getAsDouble();
    }

    public static double constraintDamping() {
        return CONSTRAINT_DAMPING.getAsDouble();
    }

    public static double constraintAngularDamping() {
        return CONSTRAINT_ANGULAR_DAMPING.getAsDouble();
    }
}
