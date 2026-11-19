package com.phantom.p2cx;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;

/** Internal adapter to the modern interceptor API. No legacy Xposed code is used. */
final class Hooks {
    private static XposedInterface framework;
    static void attach(XposedInterface value) { framework = value; }
    static final class Call {
        final Object thisObject;
        final Member method;
        final Object[] args;
        private boolean skipped;
        private Object result;
        Call(XposedInterface.Chain chain) {
            thisObject = chain.getThisObject(); method = chain.getExecutable();
            args = chain.getArgs().toArray();
        }
        void setResult(Object value) { result = value; skipped = true; }
    }
    abstract static class Callback {
        protected void before(Call call) throws Throwable { }
        protected void after(Call call) throws Throwable { }
    }
    static void all(Class<?> type, String name, Callback callback) {
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name)) method(method, callback);
    }
    static void method(Method method, Callback callback) {
        framework.hook(method).intercept(chain -> {
            Call call = new Call(chain);
            callback.before(call);
            Object result = call.skipped ? call.result : chain.proceed(call.args);
            callback.after(call);
            return call.skipped ? call.result : result;
        });
    }
    static void exact(Class<?> type, String name, Class<?> argument, Callback callback) {
        try { method(type.getDeclaredMethod(name, argument), callback); }
        catch (NoSuchMethodException error) { log(error); }
    }
    static Class<?> find(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException | LinkageError unavailable) { return null; }
    }
    static Object original(Member member, Object owner, Object[] args) throws Throwable {
        return framework.getInvoker((Method) member).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(owner, args);
    }
    static void log(String message) { framework.log(android.util.Log.INFO, "P2C_X", message); }
    static void log(Throwable error) { framework.log(android.util.Log.ERROR, "P2C_X", "Hook error", error); }
}
