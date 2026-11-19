package com.phantom.p2cx;

import android.net.Uri;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

abstract class CaptureCallback {
    abstract void success(Uri uri) throws Exception;
    abstract void failure(String message) throws Exception;

    static CaptureCallback create(Object callback, CaptureOutput output) throws Exception {
        ClassLoader loader = callback.getClass().getClassLoader();
        if (callback.getClass().getName().equals("j6.f")) {
            Object successFunction = field(callback, "b");
            Object errorFunction = field(callback, "a");
            File file = (File) field(callback, "c");
            Object front = field(callback, "d");
            if (!file.equals(output.file())) throw new IllegalArgumentException("GhostShift output mismatch");
            Class<?> errorClass = loader.loadClass("y.g0");
            Method success = loader.loadClass("x6.e").getMethod("f0", Object.class, Object.class);
            Method failure = loader.loadClass("x6.c").getMethod("o0", Object.class);
            success.setAccessible(true);
            failure.setAccessible(true);
            return new CaptureCallback() {
                @Override void success(Uri uri) throws Exception { success.invoke(successFunction, Uri.fromFile(file), front); }
                @Override void failure(String message) throws Exception {
                    // R8 removed this exception's constructors from the shipped DEX.
                    Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                    Field instance = unsafeClass.getDeclaredField("theUnsafe");
                    instance.setAccessible(true);
                    Object unsafe = instance.get(null);
                    Object error = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, errorClass);
                    failure.invoke(errorFunction, error);
                }
            };
        }
        if (callback.getClass().getName().equals("A4.M")) {
            File file = (File) field(callback, "p");
            if (!file.equals(output.file())) throw new IllegalArgumentException("Nera output mismatch");
            Object reply = field(callback, "q");
            Method success = loader.loadClass("c6.D0").getDeclaredMethod("b", Object.class, Object.class);
            success.setAccessible(true);
            Constructor<?> error = loader.loadClass("z.a0").getDeclaredConstructor(int.class, String.class, Throwable.class);
            error.setAccessible(true);
            Method failure = callback.getClass().getDeclaredMethod("I", error.getDeclaringClass());
            failure.setAccessible(true);
            return new CaptureCallback() {
                @Override void success(Uri uri) throws Exception { success.invoke(null, file.getAbsolutePath(), reply); }
                @Override void failure(String message) throws Exception { failure.invoke(callback, error.newInstance(0, message, null)); }
            };
        }
        Method foundSuccess = null;
        Method foundFailure = null;
        for (Method method : callback.getClass().getMethods()) {
            Class<?>[] arguments = method.getParameterTypes();
            if (method.getReturnType() != void.class || arguments.length != 1) continue;
            if (Throwable.class.isAssignableFrom(arguments[0])) {
                foundFailure = method;
                continue;
            }
            try {
                arguments[0].getDeclaredConstructor(Uri.class);
                foundSuccess = method;
            } catch (NoSuchMethodException ignored) { }
        }
        if (foundSuccess == null || foundFailure == null) throw new IllegalArgumentException("Unsupported CameraX callback layout");
        Method success = foundSuccess;
        Method failure = foundFailure;
        success.setAccessible(true);
        failure.setAccessible(true);
        Constructor<?> result = success.getParameterTypes()[0].getDeclaredConstructor(Uri.class);
        result.setAccessible(true);
        Constructor<?> error = failure.getParameterTypes()[0].getDeclaredConstructor(int.class, String.class, Throwable.class);
        error.setAccessible(true);
        return new CaptureCallback() {
            @Override void success(Uri uri) throws Exception { success.invoke(callback, result.newInstance(uri)); }
            @Override void failure(String message) throws Exception { failure.invoke(callback, error.newInstance(0, message, null)); }
        };
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
