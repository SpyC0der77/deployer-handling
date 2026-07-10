package com.carterstach.deployerhold.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * Registers Deployer Hold configs for NeoForge and Create's in-game config UI.
 */
public final class DeployerHoldConfigs {
    private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

    private static DHServer server;

    private DeployerHoldConfigs() {}

    public static DHServer server() {
        return server;
    }

    public static boolean serverLoaded() {
        return server != null
                && server.specification != null
                && server.specification.isLoaded();
    }

    public static void register(ModContainer container) {
        server = register(DHServer::new, ModConfig.Type.SERVER);
        for (Entry<ModConfig.Type, ConfigBase> entry : CONFIGS.entrySet())
            container.registerConfig(entry.getKey(), entry.getValue().specification);
    }

    private static <T extends ConfigBase> T register(Supplier<T> factory, ModConfig.Type type) {
        Pair<T, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(builder -> {
            T config = factory.get();
            config.registerAll(builder);
            return config;
        });
        T config = pair.getLeft();
        config.specification = pair.getRight();
        CONFIGS.put(type, config);
        return config;
    }
}
