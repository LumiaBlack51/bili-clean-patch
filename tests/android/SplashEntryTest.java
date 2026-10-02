package app.biliclean.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;

/** Emulator-only: exercise actual patched entries with populated host memory caches. */
public final class SplashEntryTest extends Instrumentation {
    private ClassLoader loader;
    private final StringBuilder checks = new StringBuilder();
    private int passed;
    private boolean baseline;
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks.append("PASS ").append(label).append('\n');
        passed++;
    }
    private Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private Class<?> type(String name) throws Exception { return loader.loadClass(name); }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        baseline = arguments != null && "true".equals(arguments.getString("baseline"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object setting = null, previous = null, legacyCache = null, previousLegacyOrder = null;
        Object modernCache = null, previousModernOrder = null;
        Field settingValue = null, legacyOrderField = null, modernOrderField = null;
        try {
            loader = getTargetContext().getClassLoader();
            setting = type(GeneratedNames.SETTINGS).getField(GeneratedNames.ADS).get(null);
            settingValue = field(type(GeneratedNames.SETTING), GeneratedNames.VALUE);
            previous = settingValue.get(setting);
            Class<?> oldOrder = type("tv.danmaku.bili.splash.ad.model.SplashOrder");
            Object gson = type("com.google.gson.Gson").getConstructor().newInstance();
            Object legacy = gson.getClass().getMethod("fromJson", String.class, Class.class)
                .invoke(gson, "{\"id\":987654321,\"card_type\":14}", oldOrder);
            check(((Number)oldOrder.getMethod("getCardType").invoke(legacy)).intValue() == 14,
                "fixture uses actual SplashOrder JSON annotations");
            Object legacyState = field(type("tv.danmaku.bili.splash.ad.core.b"), "a").get(null);
            legacyCache = field(legacyState.getClass(), "a").get(legacyState);
            legacyOrderField = field(legacyCache.getClass(), "b");
            previousLegacyOrder = legacyOrderField.get(legacyCache);
            Class<?> source = type("tv.danmaku.bili.splash.ad.model.SplashSource");
            Method select = type("tv.danmaku.bili.splash.ad.core.SplashManager").getMethod("b", boolean.class, source);
            Method factory = type("tv.danmaku.bili.splash.ad.core.SplashManager").getMethod("a", oldOrder);

            Class<?> vm = type("kntr.srcs.app.splash.core.vm.BusinessSplashViewModel");
            Object flow = field(vm, "e").get(null);
            Object state = type("kotlinx.coroutines.flow.StateFlow").getMethod("getValue").invoke(flow);
            modernCache = field(state.getClass(), "b").get(state);
            modernOrderField = field(modernCache.getClass(), "b");
            previousModernOrder = modernOrderField.get(modernCache);
            Object modern = type("oi1.q").getConstructor().newInstance();
            Object runtime = field(modern.getClass(), "x0").get(modern);
            if (runtime == null) {
                runtime = type("oi1.m").getConstructor().newInstance();
                field(modern.getClass(), "x0").set(modern, runtime);
            }
            field(runtime.getClass(), "g").setBoolean(runtime, true);
            Method hot = vm.getMethod("c");
            Method modernSelect = vm.getMethod("a", type("kntr.srcs.app.splash.model.SplashSource"));
            if (baseline) {
                settingValue.set(setting, true);
                legacyOrderField.set(legacyCache, legacy);
                modernOrderField.set(modernCache, modern);
                check(select.invoke(null, false, source.getField("COLD").get(null)) == legacy,
                    "baseline: enabled ads filter still returns legacy cached order");
                check(hot.invoke(null) == modern,
                    "baseline: enabled ads filter still returns KSplash prepared hot order");
                result.putInt("passed", passed);
                result.putString("stream", checks.toString());
                return;
            }
            Method guard = type("app.revanced.bilibili.clean.CleanSplash").getMethod("block");
            Object lambda = type("bf.d").getConstructor(Object.class, int.class).newInstance(null, Integer.MAX_VALUE);
            Method hotStart = lambda.getClass().getMethod("invoke");

            // Toggle twice against the same populated caches, with no JSON/network filtering.
            for (int repeat = 0; repeat < 2; repeat++) {
                settingValue.set(setting, false);
                check(Boolean.FALSE.equals(guard.invoke(null)), "disabled splash guard repeat=" + repeat);
                legacyOrderField.set(legacyCache, legacy);
                check(select.invoke(null, false, source.getField("COLD").get(null)) == legacy,
                    "disabled legacy selector returns preloaded order repeat=" + repeat);
                modernOrderField.set(modernCache, modern);
                check(hot.invoke(null) == modern,
                    "disabled KSplash hot selector returns cached order repeat=" + repeat);

                settingValue.set(setting, true);
                check(Boolean.TRUE.equals(guard.invoke(null)), "enabled splash guard repeat=" + repeat);
                for (String name : new String[]{"COLD", "HOT", "CALL_UP"})
                    check(select.invoke(null, false, source.getField(name).get(null)) == null,
                        "legacy cached order blocked for " + name + " repeat=" + repeat);
                check(factory.invoke(null, legacy) == null,
                    "already selected legacy order cannot create ad page repeat=" + repeat);
                check(hot.invoke(null) == null,
                    "KSplash prepared memory order blocked repeat=" + repeat);
                check(modernOrderField.get(modernCache) == modern,
                    "KSplash guard does not mutate shared cache repeat=" + repeat);
                // Null source would crash the original selector; the guard must execute first.
                check(modernSelect.invoke(null, new Object[]{null}) == null,
                    "KSplash cold/hot selection intercepted before original repeat=" + repeat);
                check(hotStart.invoke(lambda) == type("kotlin.Unit").getField("INSTANCE").get(null),
                    "shared hot-start branch returns before routing or wait repeat=" + repeat);
            }
            result.putInt("passed", passed);
            result.putString("stream", checks.toString());
        } catch (Throwable failure) {
            result.putString("stream", checks + "FAIL " + failure + " cause=" + failure.getCause() + "\n");
            result.putString("failure", failure.toString());
        } finally {
            try {
                if (legacyOrderField != null) legacyOrderField.set(legacyCache, previousLegacyOrder);
                if (modernOrderField != null) modernOrderField.set(modernCache, previousModernOrder);
                if (settingValue != null && previous != null) settingValue.set(setting, previous);
            } catch (Throwable failure) { result.putString("failure", "restore: " + failure); }
            finish(result.containsKey("failure") ? 0 : -1, result);
        }
    }
}
