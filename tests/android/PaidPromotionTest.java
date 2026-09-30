package app.biliclean.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Real 9.12 Gson/Fastjson models and installed Story hook; no account or live feed needed. */
public final class PaidPromotionTest extends Instrumentation {
    private ClassLoader loader;
    private Object gson;
    private Method parse;
    private final StringBuilder checks = new StringBuilder();
    private static Field field(Object value, String name) throws Exception {
        Class<?> type = value.getClass();
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks.append("PASS ").append(name).append('\n');
    }
    private Object json(String type, String value) throws Exception {
        if (type.startsWith("com.bilibili.video.story."))
            return loader.loadClass("com.alibaba.fastjson.JSON")
                .getMethod("parseObject", String.class, Class.class).invoke(null, value, loader.loadClass(type));
        return parse.invoke(gson, value, loader.loadClass(type));
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object[] settings = new Object[5], previous = new Object[5];
        Field value = null;
        try {
            loader = getTargetContext().getClassLoader();
            Class<?> settingClass = loader.loadClass(GeneratedNames.SETTINGS);
            value = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE);
            value.setAccessible(true);
            String[] keys = {GeneratedNames.ADS, GeneratedNames.PROMOTION, GeneratedNames.PAID,
                GeneratedNames.BANNER, GeneratedNames.MALL};
            for (int i = 0; i < keys.length; i++) {
                settings[i] = settingClass.getField(keys[i]).get(null); previous[i] = value.get(settings[i]);
            }
            check(Boolean.TRUE.equals(previous[2]), "paid promotion setting defaults enabled");
            value.set(settings[3], false); value.set(settings[4], false);
            Class<?> gsonType = loader.loadClass("com.google.gson.Gson");
            gson = gsonType.getConstructor().newInstance();
            parse = gsonType.getMethod("fromJson", String.class, Class.class);
            String image = "{\"type\":4,\"img_url\":\"https://example.org/rocket.png\",\"img_width\":18,\"img_height\":18}";
            String[] metadata = {
                "null",
                "{\"is_ad\":false,\"is_ad_loc\":true,\"cm_mark\":0,\"nature_ad\":0,\"extra\":{\"card\":{}}}",
                "{\"is_ad\":false,\"cm_mark\":1}",
                "{\"is_ad\":false,\"cm_mark\":3}",
                "{\"is_ad\":false,\"nature_ad\":1}",
                "{\"is_ad\":false,\"extra\":{\"card\":{\"ad_tag_style\":" + image + "}}}",
                "{\"is_ad\":false,\"extra\":{\"card\":{\"ad_tag_style_full_screen\":" + image + "}}}",
                "{\"is_ad\":false,\"extra\":{\"card\":{\"ad_tag_style\":{\"type\":4,\"img_url\":\"https://example.org/rocket.png\"}}}}",
                "{\"is_ad\":false,\"cm_mark\":99,\"nature_ad\":2}",
                "{\"is_ad\":false,\"extra\":{\"card\":{\"ad_tag_style\":{\"type\":0,\"text\":\"广告\"}}}}",
                "{\"is_ad\":true}",
                "{\"is_ad\":true,\"cm_mark\":1}",
                "{\"is_ad\":false,\"cm_mark\":5}",
                "{\"is_ad\":false,\"cm_mark\":6}",
                "{\"is_ad\":false,\"cm_mark\":7}",
                "{\"is_ad\":false,\"cm_mark\":8}"
            };
            boolean[] paid = {false,false,true,true,true,true,true,false,false,false,false,true,true,true,true,true};
            boolean[] ad = {false,false,false,false,false,false,false,false,false,false,true,true,false,false,false,false};
            List<Object> home = new ArrayList<>(), story = new ArrayList<>();
            for (String info : metadata) {
                String suffix = ",\"title\":\"小火箭 付费推广 创作推广\",\"ad_info\":" + info + "}";
                home.add(json("Fs0.v", "{\"card_goto\":\"av\"" + suffix));
                story.add(json("com.bilibili.video.story.StoryDetail", "{\"goto\":\"vertical_av\"" + suffix));
            }
            for (List<Object> models : Arrays.asList(home, story)) {
                Object info = models.get(2).getClass().getMethod("getAdInfo").invoke(models.get(2));
                check(Integer.valueOf(1).equals(info.getClass().getMethod("getCmMark").invoke(info)) &&
                    Boolean.FALSE.equals(info.getClass().getMethod("isAd").invoke(info)),
                    "real JSON promotion metadata parsed " + info.getClass().getName());
            }
            Method filter = loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filterModern", Object.class);
            Object data = loader.loadClass("com.bilibili.video.story.api.StoryFeedResponse$Data").getConstructor().newInstance();
            field(data, "items").set(data, Collections.unmodifiableList(story));
            for (int mask = 0; mask < 8; mask++) {
                boolean ads = (mask & 1) != 0, promotion = (mask & 2) != 0, enabled = (mask & 4) != 0;
                value.set(settings[0], ads); value.set(settings[1], promotion); value.set(settings[2], enabled);
                Object response = loader.loadClass("com.bilibili.pegasus.data.base.PegasusResponse").getConstructor().newInstance();
                field(response, "a").set(response, Collections.unmodifiableList(home));
                filter.invoke(null, response);
                List<Object> expectedHome = new ArrayList<>(), expectedStory = new ArrayList<>();
                for (int i = 0; i < metadata.length; i++) {
                    if (!(enabled && paid[i]) && !(ads && ad[i])) expectedHome.add(home.get(i));
                    if (!(enabled && paid[i]) && !(promotion && ad[i])) expectedStory.add(story.get(i));
                }
                check(expectedHome.equals(field(response, "a").get(response)), "home paid/general switches mask=" + mask);
                check(expectedStory.equals(data.getClass().getMethod("getItems").invoke(data)), "installed Story paid/general switches mask=" + mask);
                check(((List<?>)field(data, "items").get(data)).equals(story), "Story cached source retained mask=" + mask);
            }
            // Legacy homepage model uses FeedAdInfo rather than the modern AdInfo.
            value.set(settings[0], false); value.set(settings[1], false); value.set(settings[2], true);
            Object legacy = json("com.bilibili.pegasus.api.model.BasicIndexItem", "{}");
            field(legacy, "adInfo").set(legacy, json("com.bilibili.adcommon.data.model.FeedAdInfo", metadata[3]));
            field(legacy, "cardGoto").set(legacy, "av");
            FeedData feedData = new FeedData(); feedData.items.add(legacy);
            Response wrapper = new Response(); wrapper.data = feedData;
            loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filter", Object.class).invoke(null, wrapper);
            check(feedData.items.isEmpty(), "legacy homepage promotion metadata filtered");
            value.set(settings[2], false); feedData.items.add(legacy);
            loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filter", Object.class).invoke(null, wrapper);
            check(feedData.items.size() == 1, "legacy homepage switch restores promotion on fresh response");
            check(loader.loadClass("app.revanced.bilibili.clean.CleanContent").getMethod("filterStory", List.class)
                .invoke(null, new Object[]{null}) == null, "null Story response passes through");
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
    public static final class FeedData { public List<Object> items = new ArrayList<>(); }
    public static final class Response { public FeedData data; }
}
