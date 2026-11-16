package app.biliclean.tests;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.*;

/** Entry identities + installed getter/lifecycle hooks; live playback runs only on the AVD. */
public final class ManualStoryEntryTest extends PlaybackControlsTest {
    private ClassLoader loader;
    private Class<?> entry;
    private Bundle arguments;
    private static final String BV = "BV17x411w7KC";
    private static final long AID = 170001L;
    private final List<Activity> fixtures = new ArrayList<>();
    @Override public void onCreate(Bundle args) { arguments = args; super.onCreate(args); }
    private Object json(String body) throws Exception {
        return loader.loadClass("com.alibaba.fastjson.JSON").getMethod("parseObject", String.class, Class.class)
            .invoke(null, "{" + body + "}", loader.loadClass("com.bilibili.video.story.StoryDetail"));
    }
    private List<?> items(List<?> source) throws Exception {
        Object data = loader.loadClass("com.bilibili.video.story.api.StoryFeedResponse$Data").getConstructor().newInstance();
        Field f = data.getClass().getDeclaredField("items"); f.setAccessible(true); f.set(data, source);
        List<?> result = (List<?>)data.getClass().getMethod("getItems").invoke(data);
        check(f.get(data) == source, "cached source list remains intact");
        return result;
    }
    private void enter(Activity screen, Intent route) throws Exception {
        screen.setIntent(route);
        entry.getMethod("entered", Activity.class).invoke(null, screen);
    }
    private void leave(Activity screen) throws Exception { entry.getMethod("leaving", Activity.class).invoke(null, screen); }
    private boolean registered(Activity screen) throws Exception {
        Field field = entry.getDeclaredField("entries"); field.setAccessible(true);
        Map<?, ?> entries = (Map<?, ?>)field.get(null);
        synchronized (entries) { return entries.containsKey(screen); }
    }
    private Activity fixture(Intent route) throws Exception {
        final Activity[] created = {null}; ui(() -> created[0] = new Activity());
        Activity a = created[0]; fixtures.add(a); enter(a, route); return a;
    }
    private void models() throws Exception {
        String[] metadata = {
            "\"goto\":\"vertical_ad_av\"",
            "\"goto\":\"av\",\"rcmd_reason\":\"创作推广\"",
            "\"goto\":\"av\",\"ad_info\":{\"is_ad\":false,\"cm_mark\":1}",
            "\"goto\":\"av\",\"uri\":\"bilibili://mall/home\"",
            "\"goto\":\"av\",\"story_cart_icon\":{\"goto\":\"cart\",\"icon_url\":\"https://example.org/bag.png\",\"icon_text\":\"购物\",\"icon_title\":\"查看商品\"}",
            "\"goto\":\"cheese\",\"uri\":\"bilibili://cheese/season/5526\"",
            "\"goto\":\"vertical_course\",\"course_info\":{\"free_season\":true}",
            "\"goto\":\"av\",\"upower_info\":{\"is_preview\":true}"
        };
        Object normal = json("\"goto\":\"av\",\"title\":\"ordinary video\"");
        for (int i = 0; i < metadata.length; i++) {
            Object clicked = json("\"bvid\":\"" + BV + "\",\"player_args\":{\"aid\":" + AID + "}," + metadata[i]);
            Object recommended = json("\"bvid\":\"BV1YpZkBDEVo\",\"player_args\":{\"aid\":170002}," + metadata[i]);
            List<?> source = Collections.unmodifiableList(Arrays.asList(recommended, clicked, normal));
            check(items(source).equals(Arrays.asList(normal)), "entry and recommendation both filtered before explicit open rule=" + i);
            Activity a = fixture(new Intent().putExtra("id", "av" + AID));
            check(items(source).equals(Arrays.asList(clicked, normal)), "clicked AV survives while earlier recommendation stays filtered rule=" + i);
            check(items(Collections.singletonList(recommended)).isEmpty(), "next-page first recommendation stays filtered rule=" + i);
            leave(a);
            check(items(source).equals(Arrays.asList(normal)), "entry exemption cleared on exit rule=" + i);
        }
        Object target = json("\"bvid\":\"" + BV + "\",\"player_args\":{\"aid\":" + AID + "}," + metadata[4]);
        List<?> one = Collections.singletonList(target);
        Intent[] routes = {
            new Intent().putExtra("id", Long.toString(AID)), new Intent().putExtra("avid", AID),
            new Intent().putExtra("aid", AID), new Intent().putExtra("id", BV), new Intent().putExtra("bvid", BV),
            new Intent().putExtra("story_item", "{\"player_args\":{\"aid\":" + AID + "}}"),
            new Intent().putExtra("story_item", "{\"bvid\":\"" + BV + "\"}")
        };
        for (int i = 0; i < routes.length; i++) {
            Activity a = fixture(routes[i]); check(items(one).equals(one), "supported native entry identity " + i); leave(a);
        }
        Object avOnly = json("\"player_args\":{\"aid\":" + AID + "}," + metadata[4]);
        Object bvOnly = json("\"bvid\":\"" + BV + "\"," + metadata[4]);
        Activity cross = fixture(new Intent().putExtra("id", BV));
        check(items(Collections.singletonList(avOnly)).equals(Collections.singletonList(avOnly)), "BV entry protects AV-only response");
        leave(cross); enter(cross, new Intent().putExtra("id", AID));
        check(items(Collections.singletonList(bvOnly)).equals(Collections.singletonList(bvOnly)), "AV entry protects BV-only cached response");
        leave(cross);
        Intent[] invalid = {new Intent(), new Intent().putExtra("id", "0"), new Intent().putExtra("id", "-1"),
            new Intent().putExtra("id", "BV"), new Intent().putExtra("story_item", "{malformed"),
            new Intent().putExtra("id", "170002").putExtra("story_item", "{\"bvid\":\"" + BV + "\"}"),
            new Intent().putExtra("id", "170002").putExtra("bvid", BV)};
        for (int i = 0; i < invalid.length; i++) {
            Activity a = fixture(invalid[i]); check(items(one).isEmpty(), "unknown/invalid/unrelated route grants no exemption " + i); leave(a);
        }
        Activity a = fixture(routes[0]), b = fixture(routes[4]);
        leave(a); check(items(one).equals(one), "closing one stacked entry keeps the other explicit entry");
        enter(b, new Intent().putExtra("id", "170002"));
        check(items(one).isEmpty(), "new entry replaces old identity without permanent whitelist");
        leave(b);
        check(items(Collections.emptyList()).isEmpty(), "empty Story response remains empty");
        Method filter = loader.loadClass("app.revanced.bilibili.clean.CleanContent").getMethod("filterStory", List.class);
        check(filter.invoke(null, new Object[]{null}) == null, "null Story response remains null");
    }
    private void live() throws Exception {
        playback = loader.loadClass("app.revanced.bilibili.clean.CleanPlayback");
        mediaType = loader.loadClass("tv.danmaku.ijk.media.player.IMediaPlayer");
        Intent route = new Intent().setClassName("tv.danmaku.bili", "com.bilibili.video.story.StoryVideoActivity")
            .putExtra("id", Long.toString(AID)).putExtra("avid", Long.toString(AID)).putExtra("bvid", BV)
            .putExtra("from_spmid", "main.search-result.0.0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = startActivitySync(route);
        check(activity.getClass().getName().equals("com.bilibili.video.story.StoryVideoActivity"), "native portrait Story screen opened");
        check(registered(activity), "installed onCreate hook registers the real entry owner");
        Object clicked = json("\"bvid\":\"" + BV + "\",\"player_args\":{\"aid\":" + AID + "},\"rcmd_reason\":\"创作推广\"");
        Object other = json("\"bvid\":\"BV1YpZkBDEVo\",\"rcmd_reason\":\"创作推广\"");
        check(items(Arrays.asList(other, clicked)).equals(Arrays.asList(clicked)), "installed onCreate hook protects entry before recommendations");
        long until = SystemClock.uptimeMillis() + 60000;
        while (SystemClock.uptimeMillis() < until) {
            ui(() -> player = ref("media"));
            if (player != null && ref("owner") == activity && ((Number)media("getDuration")).longValue() > 10000) break;
            SystemClock.sleep(500);
        }
        check(player != null && ref("owner") == activity && ((Number)media("getDuration")).longValue() > 10000, "portrait entry binds real native media");
        long before = position(); SystemClock.sleep(5000);
        check(position() > before + 1000, "portrait video playback advances for five seconds");
        check(!activity.isFinishing() && !activity.isDestroyed(), "portrait screen stays open without filtering exit");
        capture("manual-story-entry");
        ui(() -> activity.finish());
        for (int i = 0; i < 60 && !activity.isDestroyed(); i++) SystemClock.sleep(100);
        check(activity.isDestroyed(), "native portrait screen destroyed on exit");
        check(!registered(activity), "installed onDestroy hook releases the entry owner");
        check(items(Arrays.asList(other, clicked)).isEmpty(), "installed onDestroy hook removes entry exemption");
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Object[] settings = new Object[7], previous = new Object[7]; Field value = null;
        try {
            loader = getTargetContext().getClassLoader(); entry = loader.loadClass("app.revanced.bilibili.clean.CleanStoryEntry");
            Class<?> config = loader.loadClass(GeneratedNames.SETTINGS);
            value = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE); value.setAccessible(true);
            String[] names = {GeneratedNames.PROMOTION, GeneratedNames.MALL, GeneratedNames.PAID, GeneratedNames.SHOPPING,
                GeneratedNames.COURSES, GeneratedNames.STORY_COURSES, GeneratedNames.LOCKED_UPOWER};
            for (int i = 0; i < names.length; i++) { settings[i] = config.getField(names[i]).get(null); previous[i] = value.get(settings[i]); value.set(settings[i], true); }
            if ("true".equals(arguments.getString("live"))) live(); else models();
            result.putString("results", report.toString()); finish(Activity.RESULT_OK, result);
        } catch (Throwable e) { result.putString("failure", android.util.Log.getStackTraceString(e)); result.putString("results", report.toString()); finish(Activity.RESULT_CANCELED, result); }
        finally {
            for (Activity a : fixtures) try { leave(a); } catch (Throwable ignored) { }
            if (value != null) for (int i = 0; i < settings.length; i++) if (settings[i] != null) try { value.set(settings[i], previous[i]); } catch (Throwable ignored) { }
        }
    }
}
