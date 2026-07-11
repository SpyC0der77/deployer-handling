package com.carterstach.deployerhold;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards against wrench/goggle tooltips drifting from en_us.json.
 */
class LangKeysTest {
    private static JsonObject lang;

    @BeforeAll
    static void loadLang() throws IOException {
        Path path = Path.of("src/main/resources/assets/deployerhold/lang/en_us.json");
        if (Files.isRegularFile(path)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
                return;
            }
        }
        // Fallback when tests run from a different working directory (IDE).
        try (InputStream in = LangKeysTest.class.getClassLoader()
                .getResourceAsStream("assets/deployerhold/lang/en_us.json")) {
            assertNotNull(in, "en_us.json missing from resources");
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void latchStatusKeysAreTranslated() {
        for (LatchStatus status : LatchStatus.values()) {
            assertHasNonBlank(status.langKey());
        }
    }

    @Test
    void gripModeTooltipKeysAreTranslated() {
        assertHasNonBlank("deployerhold.tooltip.deployer.grip_pull");
        assertHasNonBlank("deployerhold.tooltip.deployer.grip_hitch");
        assertHasNonBlank("deployerhold.tooltip.deployer.holding_handle");
    }

    private static void assertHasNonBlank(String key) {
        if (!lang.has(key))
            fail("Missing lang entry: " + key);
        String value = lang.get(key).getAsString();
        assertTrue(!value.isBlank(), () -> "Blank lang value for " + key);
    }
}
