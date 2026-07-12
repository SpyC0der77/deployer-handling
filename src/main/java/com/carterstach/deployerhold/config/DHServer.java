package com.carterstach.deployerhold.config;

import net.createmod.catnip.config.ConfigBase;

/**
 * Server config shown in Create's "Access Configs of other Mods" menu.
 */
public class DHServer extends ConfigBase {
    public final ConfigGroup grab = group(0, "grab", Comments.grab);
    public final ConfigFloat grabTolerance = f(0.75f, 0.25f, 8.0f, "grabTolerance",
            Comments.grabTolerance);
    public final ConfigFloat holdRange = f(5.0f, 1.0f, 16.0f, "holdRange",
            Comments.holdRange);
    public final ConfigFloat tipDistance = f(2.25f, 0.5f, 6.0f, "tipDistance",
            Comments.tipDistance);
    public final ConfigInt tipSearchRadius = i(2, 0, 4, "tipSearchRadius",
            Comments.tipSearchRadius);

    public final ConfigGroup gripAnchor = group(0, "gripAnchor", Comments.gripAnchor);
    public final ConfigFloat faceOffset = f(0.5f, 0.0f, 2.0f, "faceOffset",
            Comments.faceOffset);
    public final ConfigFloat handLength = f(0.25f, 0.0f, 1.0f, "handLength",
            Comments.handLength);
    public final ConfigFloat pullIn = f(0.05f, 0.0f, 1.5f, "pullIn",
            Comments.pullIn);

    public final ConfigGroup constraint = group(0, "constraint", Comments.constraint);
    public final ConfigFloat stiffness = f(800.0f, 1.0f, 5000.0f, "stiffness",
            Comments.stiffness);
    public final ConfigFloat damping = f(60.0f, 0.0f, 500.0f, "damping",
            Comments.damping);
    public final ConfigFloat angularStiffness = f(80.0f, 0.0f, 2000.0f, "angularStiffness",
            Comments.angularStiffness);
    public final ConfigFloat angularDamping = f(8.0f, 0.0f, 100.0f, "angularDamping",
            Comments.angularDamping);

    @Override
    public String getName() {
        return "server";
    }

    private static class Comments {
        static final String grab = "Latching and tip search";
        static final String grabTolerance = "Max distance from the deployer tip to a handle grab center when latching.";
        static final String holdRange = "Max tip-to-handle distance while a grip is held before it drops.";
        static final String tipDistance = "How far along facing (from block center) the search tip sits when fully extended.";
        static final String tipSearchRadius = "Half-size of the block cube searched around the tip for handles (in blocks).";

        static final String gripAnchor = "Grip tip geometry";
        static final String faceOffset = "Distance from deployer block center to the front face along facing (Create uses 0.5).";
        static final String handLength = "Holding-hand length added to the tip (Create holding pose uses 4/16 = 0.25).";
        static final String pullIn = "How far inward from the visual tip the rider rests. Lower = rider sits farther out toward the tip.";

        static final String constraint = "Rider pull toward the anchor";
        static final String stiffness = "Linear motor stiffness for the rider (how quickly it moves toward the anchor). Higher = snappier pull.";
        static final String damping = "Linear motor damping for the rider constraint.";
        static final String angularStiffness = "Angular motor stiffness that keeps the handle and deployer facing each other. Twist around facing stays free.";
        static final String angularDamping = "Angular motor damping (keeps the gripped body from spinning wildly).";
    }
}
