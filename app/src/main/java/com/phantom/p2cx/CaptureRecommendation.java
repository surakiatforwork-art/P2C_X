package com.phantom.p2cx;

import android.content.Intent;
import android.graphics.Rect;
import android.util.Size;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads CameraX ResolutionInfo, including renamed classes, without changing capture state. */
final class CaptureRecommendation {
    static Intent attach(Intent picker, Object capture) {
        try {
            for (Method getter : capture.getClass().getMethods()) {
                if (getter.getParameterCount() != 0 || java.lang.reflect.Modifier.isStatic(getter.getModifiers())) continue;
                Class<?> type = getter.getReturnType();
                boolean size = false, rect = false, rotation = false;
                for (Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    size |= field.getType() == Size.class;
                    rect |= field.getType() == Rect.class;
                    rotation |= field.getType() == int.class;
                }
                for (Method value : type.getDeclaredMethods()) {
                    if (value.getParameterCount() != 0 || java.lang.reflect.Modifier.isStatic(value.getModifiers())) continue;
                    size |= value.getReturnType() == Size.class;
                    rect |= value.getReturnType() == Rect.class;
                    rotation |= value.getReturnType() == int.class;
                }
                if (!size || !rect || !rotation) continue;
                getter.setAccessible(true);
                Object info = getter.invoke(capture);
                if (info == null) continue;
                Rect crop = null; int degrees = 0;
                for (Field field : info.getClass().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    if (field.getType() == Rect.class) crop = (Rect) field.get(info);
                    if (field.getType() == int.class) degrees = field.getInt(info);
                }
                for (Method value : type.getDeclaredMethods()) {
                    if (value.getParameterCount() != 0 || java.lang.reflect.Modifier.isStatic(value.getModifiers())) continue;
                    if (value.getReturnType() != Rect.class && value.getReturnType() != int.class) continue;
                    value.setAccessible(true);
                    if (value.getReturnType() == Rect.class) crop = (Rect) value.invoke(info);
                    if (value.getReturnType() == int.class) degrees = (Integer) value.invoke(info);
                }
                if (crop == null || crop.width() <= 0 || crop.height() <= 0) continue;
                boolean swap = Math.floorMod(degrees, 180) == 90;
                picker.putExtra(TargetProfile.WIDTH, swap ? crop.height() : crop.width());
                picker.putExtra(TargetProfile.HEIGHT, swap ? crop.width() : crop.height());
                return picker;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return renamed(picker, capture);
    }

    // Verified against GhostShift 1.29 and Nera 2691.1.0 capture implementations.
    // These read the same viewport, stream size and relative rotation used by takePicture.
    private static Intent renamed(Intent picker, Object capture) {
        try {
            String name = capture.getClass().getName();
            boolean ghost = "y.f0".equals(name), nera = "z.Z".equals(name);
            if (!ghost && !nera) return picker;
            Object camera = method(capture.getClass(), ghost ? "b" : "e", 0).invoke(capture);
            if (camera == null) return picker;
            Method rotation = method(capture.getClass(), ghost ? "g" : "j", 2);
            if (rotation.getReturnType() != int.class || rotation.getParameterTypes()[1] != boolean.class) return picker;
            int degrees = (Integer) rotation.invoke(capture, camera, false);
            Rect viewport = (Rect) field(capture, ghost ? "i" : "k", Rect.class);
            Size size;
            if (ghost) {
                Object stream = field(capture, "g", null);
                if (stream == null) return picker;
                size = (Size) field(stream, "a", Size.class);
            } else {
                Method resolution = method(capture.getClass(), "d", 0);
                if (resolution.getReturnType() != Size.class) return picker;
                size = (Size) resolution.invoke(capture);
            }
            android.util.Rational ratio = (android.util.Rational) field(capture, ghost ? "q" : "u", android.util.Rational.class);
            int[] result = dimensions(size, viewport, ratio, degrees);
            if (result != null) picker.putExtra(TargetProfile.WIDTH, result[0]).putExtra(TargetProfile.HEIGHT, result[1]);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return picker;
    }

    static int[] dimensions(Size size, Rect viewport, android.util.Rational ratio, int degrees) {
        if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) return null;
        boolean swap = Math.floorMod(degrees, 180) == 90;
        int width = viewport == null ? size.getWidth() : viewport.width();
        int height = viewport == null ? size.getHeight() : viewport.height();
        if (viewport == null && ratio != null && !ratio.isNaN() && !ratio.isInfinite() && ratio.floatValue() > 0) {
            float sensorRatio = swap ? 1f / ratio.floatValue() : ratio.floatValue();
            if (sensorRatio > width / (float) height) height = Math.max(1, Math.round(width / sensorRatio));
            else width = Math.max(1, Math.round(height * sensorRatio));
        }
        if (width <= 0 || height <= 0) return null;
        return swap ? new int[]{height, width} : new int[]{width, height};
    }

    private static Object field(Object owner, String name, Class<?> expected) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (expected != null && field.getType() != expected) throw new NoSuchFieldException(name);
                field.setAccessible(true); return field.get(owner);
            } catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method method(Class<?> owner, String name, int parameters) throws NoSuchMethodException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                method.setAccessible(true); return method;
            }
        }
        throw new NoSuchMethodException(name);
    }
}
