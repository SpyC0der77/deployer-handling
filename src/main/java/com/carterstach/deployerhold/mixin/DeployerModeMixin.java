package com.carterstach.deployerhold.mixin;

import com.carterstach.deployerhold.DeployerHoldModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Injects Grip: Pull / Grip: Hitch into Create's package-private Mode enum.
 * Uses Unsafe so we never need Mode in our compile-time signatures
 * (avoids JPMS split-packages with Create). Java forbids reflective enum construction.
 */
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity$Mode", remap = false)
public class DeployerModeMixin {
    private static final Object UNSAFE = unsafe();
    private static final Method ALLOCATE_INSTANCE = method("allocateInstance", Class.class);
    private static final Method OBJECT_FIELD_OFFSET = method("objectFieldOffset", Field.class);
    private static final Method STATIC_FIELD_BASE = method("staticFieldBase", Field.class);
    private static final Method STATIC_FIELD_OFFSET = method("staticFieldOffset", Field.class);
    private static final Method GET_OBJECT = method("getObject", Object.class, long.class);
    private static final Method PUT_OBJECT = method("putObject", Object.class, long.class, Object.class);
    private static final Method PUT_INT = method("putInt", Object.class, long.class, int.class);

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void deployerhold$addGripModes(CallbackInfo ci) {
        try {
            Class<?> modeClass = Class.forName(
                    "com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity$Mode");
            Object[] current = getEnumValues(modeClass);

            Object pull = newMode(modeClass, "HOLD_PULL", current.length);
            Object hitch = newMode(modeClass, "HOLD_HITCH", current.length + 1);

            Object[] expanded = (Object[]) Array.newInstance(modeClass, current.length + 2);
            System.arraycopy(current, 0, expanded, 0, current.length);
            expanded[current.length] = pull;
            expanded[current.length + 1] = hitch;
            setEnumValues(modeClass, expanded);

            DeployerHoldModes.HOLD_PULL = pull;
            DeployerHoldModes.HOLD_HITCH = hitch;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to inject Deployer grip modes", t);
        }
    }

    private static Object newMode(Class<?> modeClass, String name, int ordinal) throws Exception {
        Object instance = ALLOCATE_INSTANCE.invoke(UNSAFE, modeClass);

        Field nameField = Enum.class.getDeclaredField("name");
        Field ordinalField = Enum.class.getDeclaredField("ordinal");
        long nameOffset = (Long) OBJECT_FIELD_OFFSET.invoke(UNSAFE, nameField);
        long ordinalOffset = (Long) OBJECT_FIELD_OFFSET.invoke(UNSAFE, ordinalField);
        PUT_OBJECT.invoke(UNSAFE, instance, nameOffset, name);
        PUT_INT.invoke(UNSAFE, instance, ordinalOffset, ordinal);
        return instance;
    }

    private static Object[] getEnumValues(Class<?> enumClass) throws Exception {
        Field valuesField = findValuesField(enumClass);
        Object base = STATIC_FIELD_BASE.invoke(UNSAFE, valuesField);
        long offset = (Long) STATIC_FIELD_OFFSET.invoke(UNSAFE, valuesField);
        return (Object[]) GET_OBJECT.invoke(UNSAFE, base, offset);
    }

    private static void setEnumValues(Class<?> enumClass, Object[] values) throws Exception {
        Field valuesField = findValuesField(enumClass);
        Object base = STATIC_FIELD_BASE.invoke(UNSAFE, valuesField);
        long offset = (Long) STATIC_FIELD_OFFSET.invoke(UNSAFE, valuesField);
        PUT_OBJECT.invoke(UNSAFE, base, offset, values);
        clearClassEnumCaches(enumClass);
    }

    private static Field findValuesField(Class<?> enumClass) throws NoSuchFieldException {
        try {
            return enumClass.getDeclaredField("$VALUES");
        } catch (NoSuchFieldException ignored) {
            for (Field field : enumClass.getDeclaredFields()) {
                if (field.isSynthetic() && field.getType().isArray()
                        && field.getType().getComponentType() == enumClass)
                    return field;
            }
            throw new NoSuchFieldException("$VALUES in " + enumClass.getName());
        }
    }

    /**
     * Null Class's enum caches via Unsafe — {@code Field#setAccessible} on
     * {@code java.lang.Class} fields is blocked by JPMS on Java 17+
     * ({@code InaccessibleObjectException}, which is not a ReflectiveOperationException).
     */
    private static void clearClassEnumCaches(Class<?> enumClass) {
        for (String name : new String[]{"enumConstants", "enumConstantDirectory"}) {
            try {
                Field field = Class.class.getDeclaredField(name);
                long offset = (Long) OBJECT_FIELD_OFFSET.invoke(UNSAFE, field);
                PUT_OBJECT.invoke(UNSAFE, enumClass, offset, null);
            } catch (Throwable ignored) {
                // Best-effort; $VALUES write is what Create's code path needs.
            }
        }
    }

    private static Object unsafe() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return theUnsafe.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method method(String name, Class<?>... params) {
        try {
            return Class.forName("sun.misc.Unsafe").getMethod(name, params);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
