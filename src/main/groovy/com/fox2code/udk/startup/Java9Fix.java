package com.fox2code.udk.startup;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Java9Fix {
    private static final boolean java8 = System.getProperty("java.version").startsWith("1.");
    private static Object unsafe;
    private static Class<?> unsafeClass;

    static {
        if (!java8) try {
            unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = field.get(null);
            try {
                //Disable Java9+ Reflection Warnings
                Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
                Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);
                Class<?> loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
                Field loggerField = loggerClass.getDeclaredField("logger");
                Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
                putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
            } catch (ReflectiveOperationException ignored) {}
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Couldn't init bypass!");
        }
    }

    private static Field access;
    private static Method fieldPutBool;
    private static long accessOffset;

    public static void setAccessible(AccessibleObject field) throws ReflectiveOperationException {
        if (java8) {
            field.setAccessible(true);
        } else {
            if (access == null) {
                access = AccessibleObject.class.getDeclaredField("override");
                Method fieldOffset = unsafeClass.getDeclaredMethod("objectFieldOffset", Field.class);
                fieldPutBool = unsafeClass.getDeclaredMethod("putBoolean", Object.class, long.class, boolean.class);
                accessOffset = (Long) fieldOffset.invoke(unsafe, access);
                setAccessible(access);
            }
            fieldPutBool.invoke(unsafe, field, accessOffset, true);
        }
    }
}
