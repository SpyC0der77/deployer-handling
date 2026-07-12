package com.carterstach.deployerhold;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatchStatusTest {
    @Test
    void langKeysUseModNamespace() {
        for (LatchStatus status : LatchStatus.values()) {
            assertTrue(
                    status.langKey().startsWith("deployerhold.tooltip.deployer."),
                    () -> status + " key should be namespaced: " + status.langKey()
            );
        }
    }

    @Test
    void expectedStatusesHaveStableKeys() {
        assertEquals("deployerhold.tooltip.deployer.latch_open", LatchStatus.OPEN.langKey());
        assertEquals("deployerhold.tooltip.deployer.latch_ready", LatchStatus.READY.langKey());
        assertEquals("deployerhold.tooltip.deployer.latch_latched", LatchStatus.LATCHED.langKey());
        assertEquals("deployerhold.tooltip.deployer.latch_locked", LatchStatus.TARGET_LOCKED.langKey());
    }

    @Test
    void allStatusesAreDistinct() {
        assertEquals(4, LatchStatus.values().length);
        assertEquals(4, java.util.Arrays.stream(LatchStatus.values()).map(LatchStatus::langKey).distinct().count());
    }
}
