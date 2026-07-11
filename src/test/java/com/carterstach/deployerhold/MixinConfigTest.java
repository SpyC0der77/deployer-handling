package com.carterstach.deployerhold;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConfigTest {
    @Test
    void mixinsJsonListsExpectedDeployerHooks() throws IOException {
        JsonObject root = loadMixinsJson();
        assertEquals("com.carterstach.deployerhold.mixin", root.get("package").getAsString());
        assertEquals("JAVA_21", root.get("compatibilityLevel").getAsString());
        assertTrue(root.get("required").getAsBoolean());

        JsonArray mixins = root.getAsJsonArray("mixins");
        Set<String> names = new HashSet<>();
        mixins.forEach(el -> names.add(el.getAsString()));
        assertTrue(names.contains("DeployerModeMixin"));
        assertTrue(names.contains("DeployerBlockEntityMixin"));
        assertTrue(names.contains("DeployerHandlerMixin"));
        assertEquals(3, names.size());
    }

    private static JsonObject loadMixinsJson() throws IOException {
        Path path = Path.of("src/main/resources/deployerhold.mixins.json");
        if (Files.isRegularFile(path)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        // Fallback when tests run from a different working directory (IDE).
        try (InputStream in = MixinConfigTest.class.getClassLoader()
                .getResourceAsStream("deployerhold.mixins.json")) {
            assertNotNull(in, "deployerhold.mixins.json missing from resources");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
