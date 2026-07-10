package com.carterstach.deployerhold;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflective access to Create deployer fields that use package-private Mode/State types.
 * Access transformers make the fields public at runtime; we still can't name those types
 * from outside Create's package without causing a JPMS split-package.
 */
public final class DeployerFields {
    private static final Field MODE = field("mode");
    private static final Field STATE = field("state");
    private static final Method GET_TIMER_SPEED = method("getTimerSpeed");

    private DeployerFields() {}

    public static Object getMode(DeployerBlockEntity deployer) {
        return get(MODE, deployer);
    }

    public static void setMode(DeployerBlockEntity deployer, Object mode) {
        set(MODE, deployer, mode);
    }

    public static Object getState(DeployerBlockEntity deployer) {
        return get(STATE, deployer);
    }

    public static void setState(DeployerBlockEntity deployer, Object state) {
        set(STATE, deployer, state);
    }

    public static Object stateNamed(DeployerBlockEntity deployer, String name) {
        Object state = getState(deployer);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object value = Enum.valueOf((Class) state.getClass(), name);
        return value;
    }

    public static int getTimerSpeed(DeployerBlockEntity deployer) {
        try {
            return (Integer) GET_TIMER_SPEED.invoke(deployer);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object get(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(Field field, Object instance, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Field field(String name) {
        try {
            Field field = DeployerBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method method(String name) {
        try {
            Method method = DeployerBlockEntity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
