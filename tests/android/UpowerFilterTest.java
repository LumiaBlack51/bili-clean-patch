package app.biliclean.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Installed host models and Story hook, with explicit access and stale-lock counterexamples. */
public final class UpowerFilterTest extends Instrumentation {
    private ClassLoader loader;
    private final StringBuilder checks = new StringBuilder();
    private void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks.append("PASS ").append(name).append('\n');
    }
    private Object json(String type, String text) throws Exception {
        return loader.loadClass("com.alibaba.fastjson.JSON").getMethod("parseObject", String.class, Class.class)
            .invoke(null, text, loader.loadClass(type));
    }
    private static Field field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object[] settings = new Object[8], previous = new Object[8]; Field value = null;
        try {
            loader = getTargetContext().getClassLoader();
            Class<?> settingsClass = loader.loadClass(GeneratedNames.SETTINGS);
            value = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE);
            value.setAccessible(true);
            String[] keys = {GeneratedNames.ADS, GeneratedNames.PROMOTION, GeneratedNames.PAID,
                GeneratedNames.BANNER, GeneratedNames.MALL, GeneratedNames.COURSES,
                GeneratedNames.STORY_COURSES, GeneratedNames.LOCKED_UPOWER};
            for (int i = 0; i < keys.length; i++) {
                settings[i] = settingsClass.getField(keys[i]).get(null); previous[i] = value.get(settings[i]);
                value.set(settings[i], false);
            }
            check(Boolean.TRUE.equals(previous[7]), "locked charging filter defaults enabled");
            value.set(settings[7], true);
            String[] fixtures = {
                "\"upower_info\":{\"is_preview\":true}",
                "\"is_unlocked\":false,\"upower_info\":{\"is_preview\":true}",
                "\"upower_info\":{\"is_preview\":true,\"duration\":60,\"watch_time_length\":15000}",
                "\"upower_info\":{\"show_paywall\":true}",
                "\"upower_info\":{\"is_preview\":false,\"show_paywall\":true,\"duration\":60,\"watch_time_length\":59999}",
                "\"upower_info\":{\"is_preview\":true,\"duration\":0,\"watch_time_length\":0}",
                "\"upower_info\":{\"is_preview\":true,\"duration\":2147483647,\"watch_time_length\":2147483646999}",
                "\"is_unlocked\":true,\"upower_info\":{\"is_preview\":true,\"show_paywall\":true}",
                "\"upower_info\":{\"is_preview\":false}",
                "\"upower_info\":{\"is_preview\":true,\"show_paywall\":true,\"duration\":60,\"watch_time_length\":60000}",
                "\"is_unlocked\":false,\"upower_info\":{\"show_paywall\":true,\"duration\":60,\"watch_time_length\":60001}",
                "\"upower_info\":{}",
                "\"upower_info\":null",
                "\"is_unlocked\":false",
                "\"title\":\"充电专属 尚未解锁 试看\",\"upower_url\":\"https://www.bilibili.com/\"",
                "\"upower_info\":{\"is_iaa\":true,\"is_preview\":true,\"show_paywall\":true}",
                "\"upower_info\":{\"is_iaa\":true,\"is_preview\":false}",
                "\"is_unlocked\":false,\"upower_info\":{\"is_preview\":false}",
                "\"upower_info\":{\"is_preview\":true,\"duration\":2147483647,\"watch_time_length\":2147483647000}",
                "\"upower_info\":{\"archive_paywall_new\":{},\"duration\":60,\"watch_time_length\":1000}"
            };
            String[] names = {
                "charging preview locked", "explicit locked preview", "partial access locked", "charging paywall locked",
                "partial paywall just below duration locked", "unknown duration cannot prove full access", "duration multiplication uses long",
                "explicit unlocked beats stale preview/paywall", "full charging exclusive retained",
                "full access exactly at duration retained", "full access beats false unlocked flag",
                "empty metadata retained", "null metadata retained", "false unlock without charging metadata retained",
                "title and support URL alone retained", "ad-unlock preview outside charging scope retained",
                "ad-unlock full access retained", "non-preview charging retained even with false unlock flag",
                "large full duration retained", "paywall object alone is not locked"
            };
            Method filter = loader.loadClass("app.revanced.bilibili.clean.CleanContent").getMethod("filterStory", List.class);
            List<Object> source = new ArrayList<>(), kept = new ArrayList<>();
            for (int i = 0; i < fixtures.length; i++) {
                Object s = json("com.bilibili.video.story.StoryDetail", "{\"goto\":\"vertical_av\"," + fixtures[i] + "}");
                source.add(s); if (i >= 7) kept.add(s);
                check(((List<?>)filter.invoke(null, Arrays.asList(s))).size() == (i < 7 ? 0 : 1), names[i]);
            }
            source.add(null); kept.add(null); source.add(new Object()); kept.add(source.get(source.size()-1));
            Object data = loader.loadClass("com.bilibili.video.story.api.StoryFeedResponse$Data").getConstructor().newInstance();
            field(data, "items").set(data, Collections.unmodifiableList(source));
            Method getter = data.getClass().getMethod("getItems");
            check(kept.equals(getter.invoke(data)), "installed Story getter filters mixed charging feed");
            check(source.equals(field(data, "items").get(data)), "charging filter retains immutable cached source");
            source.get(0).getClass().getMethod("setUnlocked", Boolean.class).invoke(source.get(0), Boolean.TRUE);
            List<Object> newlyUnlocked = new ArrayList<>(kept); newlyUnlocked.add(0, source.get(0));
            check(newlyUnlocked.equals(getter.invoke(data)), "newly unlocked cached video restored without refetch");
            value.set(settings[7], false);
            check(source.equals(getter.invoke(data)), "disabled charging filter restores cached locked videos");
            check(filter.invoke(null, new Object[]{null}) == null, "null Story response retained");
            value.set(settings[7], true);
            Object homeItem = json("com.bilibili.pegasus.api.model.BasicIndexItem", "{\"card_goto\":\"av\",\"upower_info\":{\"is_preview\":true}}");
            PaidPromotionTest.FeedData legacy = new PaidPromotionTest.FeedData(); legacy.items.add(homeItem);
            PaidPromotionTest.Response wrapper = new PaidPromotionTest.Response(); wrapper.data = legacy;
            loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filter", Object.class).invoke(null, wrapper);
            check(legacy.items.equals(Arrays.asList(homeItem)), "charging filter leaves homepage unchanged");
            result.putString("stream", checks.toString());
        } catch (Throwable failure) {
            result.putString("stream", checks + "FAIL " + failure + " cause=" + failure.getCause() + "\n");
            result.putString("failure", failure.toString());
        } finally {
            try { if (value != null) for (int i = 0; i < settings.length; i++)
                if (settings[i] != null && previous[i] != null) value.set(settings[i], previous[i]); }
            catch (Exception failure) { result.putString("failure", "restore: " + failure); }
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
