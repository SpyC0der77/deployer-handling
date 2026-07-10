package com.carterstach.deployerhold;

/**
 * Goggle latch line for grip modes only.
 */
public enum LatchStatus {
    OPEN("deployerhold.tooltip.deployer.latch_open"),
    READY("deployerhold.tooltip.deployer.latch_ready"),
    LATCHED("deployerhold.tooltip.deployer.latch_latched"),
    TARGET_LOCKED("deployerhold.tooltip.deployer.latch_locked");

    private final String langKey;

    LatchStatus(String langKey) {
        this.langKey = langKey;
    }

    public String langKey() {
        return langKey;
    }
}
