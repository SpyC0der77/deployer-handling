package com.carterstach.deployerhold;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeployerHoldModTest {
    @Test
    void modIdIsStable() {
        assertEquals("deployerhold", DeployerHoldMod.ID);
        assertFalse(DeployerHoldMod.ID.isBlank());
    }
}
