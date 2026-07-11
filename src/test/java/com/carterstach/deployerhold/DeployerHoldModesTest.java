package com.carterstach.deployerhold;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployerHoldModesTest {
    /** Stand-in for Create's DeployerBlockEntity.Mode (PUNCH/USE + injected grips). */
    private enum FakeMode {
        PUNCH,
        USE,
        HOLD_PULL,
        HOLD_HITCH
    }

    @BeforeEach
    void injectGripModes() {
        DeployerHoldModes.HOLD_PULL = FakeMode.HOLD_PULL;
        DeployerHoldModes.HOLD_HITCH = FakeMode.HOLD_HITCH;
    }

    @AfterEach
    void clearGripModes() {
        DeployerHoldModes.HOLD_PULL = null;
        DeployerHoldModes.HOLD_HITCH = null;
    }

    @Test
    void isPullAndIsHitchMatchInjectedConstants() {
        assertTrue(DeployerHoldModes.isPull(FakeMode.HOLD_PULL));
        assertFalse(DeployerHoldModes.isPull(FakeMode.HOLD_HITCH));
        assertTrue(DeployerHoldModes.isHitch(FakeMode.HOLD_HITCH));
        assertFalse(DeployerHoldModes.isHitch(FakeMode.HOLD_PULL));
        assertFalse(DeployerHoldModes.isPull(FakeMode.USE));
        assertFalse(DeployerHoldModes.isHitch(FakeMode.PUNCH));
    }

    @Test
    void isGripCoversPullAndHitchOnly() {
        assertTrue(DeployerHoldModes.isGrip(FakeMode.HOLD_PULL));
        assertTrue(DeployerHoldModes.isGrip(FakeMode.HOLD_HITCH));
        assertFalse(DeployerHoldModes.isGrip(FakeMode.USE));
        assertFalse(DeployerHoldModes.isGrip(FakeMode.PUNCH));
        assertFalse(DeployerHoldModes.isGrip(null));
    }

    @Test
    void wrenchCycleIsUsePullHitchAttack() {
        assertEquals(FakeMode.HOLD_PULL, DeployerHoldModes.next(FakeMode.USE));
        assertEquals(FakeMode.HOLD_HITCH, DeployerHoldModes.next(FakeMode.HOLD_PULL));
        assertEquals(FakeMode.PUNCH, DeployerHoldModes.next(FakeMode.HOLD_HITCH));
        assertEquals(FakeMode.USE, DeployerHoldModes.next(FakeMode.PUNCH));
    }

    @Test
    void wrenchCycleFallsBackWhenGripModesMissing() {
        DeployerHoldModes.HOLD_PULL = null;
        DeployerHoldModes.HOLD_HITCH = FakeMode.HOLD_HITCH;
        assertEquals(FakeMode.PUNCH, DeployerHoldModes.next(FakeMode.USE));

        DeployerHoldModes.HOLD_PULL = FakeMode.HOLD_PULL;
        DeployerHoldModes.HOLD_HITCH = null;
        assertEquals(FakeMode.PUNCH, DeployerHoldModes.next(FakeMode.HOLD_PULL));
    }

    @Test
    void nextThrowsWhenNoModesInjectedAndNeedEnumLookup() {
        DeployerHoldModes.HOLD_PULL = null;
        DeployerHoldModes.HOLD_HITCH = null;
        assertThrows(IllegalStateException.class, () -> DeployerHoldModes.next(FakeMode.PUNCH));
    }

    @Test
    void tooltipKeysMatchLangEntries() {
        assertEquals("deployerhold.tooltip.deployer.grip_pull", DeployerHoldModes.tooltipKey(FakeMode.HOLD_PULL));
        assertEquals("deployerhold.tooltip.deployer.grip_hitch", DeployerHoldModes.tooltipKey(FakeMode.HOLD_HITCH));
        assertNull(DeployerHoldModes.tooltipKey(FakeMode.USE));
        assertNull(DeployerHoldModes.tooltipKey(FakeMode.PUNCH));
    }
}
