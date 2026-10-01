package app.biliclean.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Real host models, feed hooks and course/PGC namespace counterexamples. AVD only. */
public final class CourseFilterTest extends Instrumentation {
    private ClassLoader loader;
    private Object gson;
    private final StringBuilder checks = new StringBuilder();
    private static Field field(Object value, String name) throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks.append("PASS ").append(name).append('\n');
    }
    private Object json(String type, String text) throws Exception {
        Class<?> target = loader.loadClass(type);
        if (!type.equals("Fs0.v")) return loader.loadClass("com.alibaba.fastjson.JSON")
            .getMethod("parseObject", String.class, Class.class).invoke(null, text, target);
        return gson.getClass().getMethod("fromJson", String.class, Class.class).invoke(gson, text, target);
    }
    private void home(List<Object> source, List<Object> expected, String name) throws Exception {
        Object response = loader.loadClass("com.bilibili.pegasus.data.base.PegasusResponse").getConstructor().newInstance();
        field(response, "a").set(response, Collections.unmodifiableList(source));
        loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filterModern", Object.class).invoke(null, response);
        check(expected.equals(field(response, "a").get(response)), name);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object[] settings = new Object[8], previous = new Object[8];
        Field value = null;
        try {
            loader = getTargetContext().getClassLoader();
            Class<?> settingsClass = loader.loadClass(GeneratedNames.SETTINGS);
            value = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE);
            value.setAccessible(true);
            String[] keys = {GeneratedNames.ADS, GeneratedNames.PROMOTION, GeneratedNames.PAID,
                GeneratedNames.BANNER, GeneratedNames.MALL, GeneratedNames.COURSES, GeneratedNames.STORY_COURSES,
                GeneratedNames.LOCKED_UPOWER};
            for (int i = 0; i < keys.length; i++) {
                settings[i] = settingsClass.getField(keys[i]).get(null); previous[i] = value.get(settings[i]);
                value.set(settings[i], false);
            }
            check(Boolean.TRUE.equals(previous[5]), "selected course switch defaults enabled");
            check(Boolean.TRUE.equals(previous[6]), "all Story courses switch defaults enabled");
            gson = loader.loadClass("com.google.gson.Gson").getConstructor().newInstance();
            String[] snippets = {
                "\"uri\":\"bilibili://cheese/season/5526\"",
                "\"uri\":\"https://www.bilibili.com/cheese/play/ss142392442?from=story\"",
                "\"uri\":\"https://m.bilibili.com/cheese/play/ep210340\"",
                "\"uri\":\"bilibili://cheese/season/ep/1863095\"",
                "\"player_args\":{\"aid\":958040759}",
                "\"player_args\":{\"aid\":115223134472472}",
                "\"uri\":\"bilibili://cheese/season/603028222\"",
                "\"uri\":\"https://www.bilibili.com/cheese/play/ss55260\"",
                "\"uri\":\"https://example.org/cheese/play/ss5526\"",
                "\"uri\":\"https://www.bilibili.com/video/BV1xx?season_id=5526\"",
                "\"uri\":\"bilibili://bangumi/season/5526\"",
                "\"uri\":\"bilibili://cheese/season/ep/210363\"",
                "\"uri\":\"https://www.bilibili.com/cheese/play/ss5526oops\"",
                "\"title\":\"聂辉华教授：基层中国的运行逻辑 陆铭教授 中国经济治理、结构与增长\"",
                "\"player_args\":{\"aid\":958040760,\"season_id\":5526,\"ep_id\":210340}",
                "\"player_args\":{}"
            };
            List<Object> home = new ArrayList<>(), story = new ArrayList<>(), keepHome = new ArrayList<>(), keepStory = new ArrayList<>();
            for (int i = 0; i < snippets.length; i++) {
                Object h = json("Fs0.v", "{\"card_goto\":\"av\"," + snippets[i] + "}");
                Object s = json("com.bilibili.video.story.StoryDetail", "{\"goto\":\"vertical_av\"," + snippets[i] + "}");
                home.add(h); story.add(s);
                if (i >= 6) { keepHome.add(h); keepStory.add(s); }
            }
            // Course-only metadata can identify a preview even when its title is a lesson title.
            String[] storyOnly = {
                "\"goto\":\"vertical_course\",\"player_args\":{\"season_id\":5526}",
                "\"goto\":\"vertical_course\",\"player_args\":{\"season_id\":142392442}",
                "\"goto\":\"vertical_course\",\"player_args\":{\"ep_id\":1861601}",
                "\"goto\":\"vertical_av\",\"course_info\":{\"desc_detail_uri\":\"https://www.bilibili.com/cheese/play/ss5526\"}",
                "\"goto\":\"vertical_pgc\",\"player_args\":{\"season_id\":5526,\"ep_id\":210340}",
                "\"goto\":\"vertical_course\",\"player_args\":{\"season_id\":603028222}",
                "\"goto\":\"vertical_course\",\"course_info\":{}",
                "\"goto\":\"vertical_course\",\"player_args\":{\"season_id\":55260,\"ep_id\":210363}"
            };
            for (int i = 0; i < storyOnly.length; i++) {
                Object s = json("com.bilibili.video.story.StoryDetail", "{" + storyOnly[i] + "}");
                story.add(s); if (i >= 4) keepStory.add(s);
            }
            Object data = loader.loadClass("com.bilibili.video.story.api.StoryFeedResponse$Data").getConstructor().newInstance();
            field(data, "items").set(data, Collections.unmodifiableList(story));
            value.set(settings[5], true);
            for (int i = 0; i < home.size(); i++) home(Arrays.asList(home.get(i)), i < 6 ? Collections.emptyList() : Arrays.asList(home.get(i)), "home identity/counterexample " + i);
            Method storyFilter = loader.loadClass("app.revanced.bilibili.clean.CleanContent").getMethod("filterStory", List.class);
            for (int i = 0; i < story.size(); i++) {
                boolean blocked = i < 6 || (i >= snippets.length && i < snippets.length + 4);
                check(((List<?>)storyFilter.invoke(null, Arrays.asList(story.get(i)))).size() == (blocked ? 0 : 1), "Story identity/counterexample " + i);
            }
            home(home, keepHome, "home mixed feed filtered with other filters disabled");
            check(keepStory.equals(data.getClass().getMethod("getItems").invoke(data)), "installed Story getter removes selected courses");
            check(story.equals(field(data, "items").get(data)), "Story source list retained");
            value.set(settings[5], false);
            home(home, home, "home disabled switch restores fresh response");
            check(story.equals(data.getClass().getMethod("getItems").invoke(data)), "Story disabled switch restores cached courses");
            value.set(settings[5], true);
            PaidPromotionTest.FeedData legacy = new PaidPromotionTest.FeedData();
            legacy.items.add(json("com.bilibili.pegasus.api.model.BasicIndexItem", "{\"card_goto\":\"cheese\",\"uri\":\"bilibili://cheese/season/5526\"}"));
            legacy.items.add(json("com.bilibili.pegasus.api.model.BasicIndexItem", "{\"card_goto\":\"av\",\"player_args\":{\"aid\":114832124674072}}"));
            Object ordinary = json("com.bilibili.pegasus.api.model.BasicIndexItem", "{\"card_goto\":\"cheese\",\"uri\":\"bilibili://cheese/season/603028222\"}");
            legacy.items.add(ordinary);
            PaidPromotionTest.Response wrapper = new PaidPromotionTest.Response(); wrapper.data = legacy;
            loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filter", Object.class).invoke(null, wrapper);
            check(legacy.items.equals(Arrays.asList(ordinary)), "legacy homepage course URI/preview filtered, other course retained");
            value.set(settings[5], false); value.set(settings[6], true);
            String[] allCourseFixtures = {
                "{\"goto\":\"vertical_course\",\"player_args\":{\"season_id\":603028222}}",
                "{\"goto\":\"vertical_course\",\"course_info\":{\"paid\":true}}",
                "{\"goto\":\"vertical_course\",\"course_info\":{\"free_season\":true}}",
                "{\"goto\":\"vertical_course\",\"course_info\":{\"preview_type\":1}}",
                "{\"goto\":\"vertical_course\",\"course_info\":{\"preview_type\":2}}",
                "{\"goto\":\"vertical_course\"}",
                "{\"goto\":\"vertical_av\",\"title\":\"免费课程 试听 领券购买\",\"course_info\":{}}",
                "{\"goto\":\"vertical_pgc\",\"player_args\":{\"season_id\":5526}}",
                "{\"goto\":\"vertical_av\",\"uri\":\"https://www.bilibili.com/cheese/play/ss5526\"}",
                "{\"goto\":\"vertical_live\"}"
            };
            List<Object> allCourseSource = new ArrayList<>(), allCourseKept = new ArrayList<>();
            for (int i = 0; i < allCourseFixtures.length; i++) {
                Object s = json("com.bilibili.video.story.StoryDetail", allCourseFixtures[i]);
                allCourseSource.add(s); if (i >= 6) allCourseKept.add(s);
                check(((List<?>)storyFilter.invoke(null, Arrays.asList(s))).size() == (i < 6 ? 0 : 1), "all Story courses classification/counterexample " + i);
            }
            field(data, "items").set(data, Collections.unmodifiableList(allCourseSource));
            check(allCourseKept.equals(data.getClass().getMethod("getItems").invoke(data)), "installed Story getter removes all course types");
            check(allCourseSource.equals(field(data, "items").get(data)), "all course filtering retains cached source");
            home(home, home, "all Story course switch leaves homepage unchanged");
            value.set(settings[6], false);
            check(allCourseSource.equals(data.getClass().getMethod("getItems").invoke(data)), "all Story course switch off restores cached courses");
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
