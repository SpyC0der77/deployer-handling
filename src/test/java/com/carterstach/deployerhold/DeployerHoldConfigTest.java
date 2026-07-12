package com.carterstach.deployerhold;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeployerHoldConfigTest {
    @Test
    void gripTipOffsetMatchesCreateHoldingHandFormula() {
        // Defaults from DHServer: face 0.5 + reach + hand 0.25 − pullIn 0.05
        assertEquals(0.7, DeployerHoldConfig.computeGripTipOffset(0.5, 0.0, 0.25, 0.05), 1.0e-9);
        assertEquals(1.45, DeployerHoldConfig.computeGripTipOffset(0.5, 0.75, 0.25, 0.05), 1.0e-9);
        assertEquals(2.0, DeployerHoldConfig.computeGripTipOffset(0.5, 1.3, 0.25, 0.05), 1.0e-9);
    }

    @Test
    void pullInMovesTipTowardBlockCenter() {
        double withoutPullIn = DeployerHoldConfig.computeGripTipOffset(0.5, 1.0, 0.25, 0.0);
        double withPullIn = DeployerHoldConfig.computeGripTipOffset(0.5, 1.0, 0.25, 0.2);
        assertEquals(0.2, withoutPullIn - withPullIn, 1.0e-9);
    }

    @Test
    void zeroReachStillIncludesFaceAndHand() {
        assertEquals(0.75, DeployerHoldConfig.computeGripTipOffset(0.5, 0.0, 0.25, 0.0), 1.0e-9);
    }
}
