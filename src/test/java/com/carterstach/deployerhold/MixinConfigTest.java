package com.carterstach.deployerhold;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConfigTest {
    @Test
    void mixinsJsonListsExpectedDeployerHooks() throws IOException {
        Path path = Path.of("src/main/resources/deployerhold.mixins.json");
        assertTrue(Files.isRegularFile(path), "deployerhold.mixins.json missing");

        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
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
    }
}
