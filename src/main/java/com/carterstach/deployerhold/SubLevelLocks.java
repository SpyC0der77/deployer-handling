package com.carterstach.deployerhold;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Queries Simulated Physics Staff locks (contraption locked in place).
 */
final class SubLevelLocks {
    private static final @Nullable Method CLIENT_IS_LOCKED = clientIsLocked();

    private SubLevelLocks() {}

    static boolean isLocked(@Nullable SubLevel subLevel) {
        if (subLevel == null)
            return false;

        Level level = subLevel.getLevel();
        if (level == null)
            return false;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            try {
                Class<?> handlerClass = Class.forName(
                        "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler");
                Method get = handlerClass.getMethod("get", ServerLevel.class);
                Object handler = get.invoke(null, serverLevel);
                Method isLocked = handlerClass.getMethod("isLocked", SubLevel.class);
                return (Boolean) isLocked.invoke(handler, subLevel);
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        if (CLIENT_IS_LOCKED == null)
            return false;
        try {
            Class<?> clientClass = Class.forName("dev.simulated_team.simulated.SimulatedClient");
            Object handler = clientClass.getField("PHYSICS_STAFF_CLIENT_HANDLER").get(null);
            return (Boolean) CLIENT_IS_LOCKED.invoke(handler, subLevel);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static @Nullable Method clientIsLocked() {
        try {
            Class<?> handlerClass = Class.forName(
                    "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler");
            Method method = handlerClass.getDeclaredMethod("isLocked", SubLevel.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
