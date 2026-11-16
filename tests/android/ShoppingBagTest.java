package app.biliclean.tests;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.*;

/** Real Story models/getter: shopping entries, unrelated diversion entries and switch restoration. AVD only. */
public final class ShoppingBagTest extends Instrumentation {
    private ClassLoader loader;
    private final StringBuilder checks = new StringBuilder();
    private Bundle arguments;
    private static Field field(Object item, String name) throws Exception {
        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
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
        return loader.loadClass("com.alibaba.fastjson.JSON").getMethod("parseObject", String.class, Class.class)
            .invoke(null, text, loader.loadClass(type));
    }
    private View findText(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView)root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) {
            View found = findText(((ViewGroup)root).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private View awaitText(Activity screen, String label) {
        final View[] found = {null};
        for (int i = 0; i < 60 && found[0] == null; i++) {
            runOnMainSync(() -> found[0] = findText(screen.getWindow().getDecorView(), label));
            if (found[0] == null) SystemClock.sleep(250);
        }
        return found[0];
    }
    private void toggleUi(boolean enabled) throws Exception {
        Activity screen = startActivitySync(new Intent().setClassName("tv.danmaku.bili",
            "com.bilibili.app.preferences.BiliPreferencesActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        View entry = awaitText(screen, "去广告与空降助手");
        check(entry != null, "native patch settings entry visible");
        View clickable = entry;
        while (clickable != null && !clickable.isClickable())
            clickable = clickable.getParent() instanceof View ? (View)clickable.getParent() : null;
        check(clickable != null, "native settings entry clickable");
        final View open = clickable; runOnMainSync(() -> open.performClick());
        View row = awaitText(screen, "屏蔽购物袋视频");
        check(row instanceof Switch, "independent shopping toggle visible in real settings");
        Switch toggle = (Switch)row;
        runOnMainSync(() -> {
            toggle.requestRectangleOnScreen(new android.graphics.Rect(0, 0, toggle.getWidth(), toggle.getHeight()), true);
            if (toggle.isChecked() != enabled) toggle.performClick();
        });
        SystemClock.sleep(500);
        check(toggle.isChecked() == enabled, "native shopping switch changed to " + enabled);
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        if (screenshot == null) throw new AssertionError("settings screenshot unavailable");
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),
                "shopping-toggle-" + enabled + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { screenshot.recycle(); }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object[] settings = new Object[9], previous = new Object[9];
        Field value = null;
        try {
            loader = getTargetContext().getClassLoader();
            Class<?> settingsClass = loader.loadClass(GeneratedNames.SETTINGS);
            value = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE);
            value.setAccessible(true);
            String[] keys = {GeneratedNames.ADS, GeneratedNames.PROMOTION, GeneratedNames.PAID,
                GeneratedNames.BANNER, GeneratedNames.MALL, GeneratedNames.COURSES, GeneratedNames.STORY_COURSES,
                GeneratedNames.LOCKED_UPOWER, GeneratedNames.SHOPPING};
            for (int i = 0; i < keys.length; i++) {
                settings[i] = settingsClass.getField(keys[i]).get(null); previous[i] = value.get(settings[i]);
            }
            if (arguments != null && arguments.containsKey("toggle")) {
                toggleUi(Boolean.parseBoolean(arguments.getString("toggle")));
            } else if (arguments != null && arguments.containsKey("expected")) {
                check(Boolean.valueOf(arguments.getString("expected")).equals(previous[8]),
                    "shopping setting persisted across process restart: " + previous[8]);
            } else {
                check(Boolean.TRUE.equals(previous[8]), "shopping setting defaults enabled");
                for (Object setting : settings) value.set(setting, false);
                value.set(settings[8], true);
                String image = "\"icon_url\":\"https://example.org/bag.png\",\"icon_text\":\"购物\",\"icon_title\":\"视频同款 立即购买\",\"uri\":\"https://mall.bilibili.com/\"";
                String[] carts = {
                    "{\"goto\":\"cart\"," + image + "}",
                    "{" + image + "}",
                    "{\"goto\":\"ad\"," + image + "}",
                    "{\"goto\":\"anchor_ad\"," + image + "}",
                    "{\"goto\":\"cart\",\"icon_url\":\"https://example.org/cart.png\",\"icon_text\":\"商品\",\"icon_title\":\"查看商品\"}",
                    "{\"goto\":\"unknown\",\"icon_url\":\"https://example.org/cart.png\",\"icon_text\":\" 购物 \",\"icon_title\":\"立即购买\"}",
                    "null", "{}",
                    "{\"goto\":\"cart\",\"icon_text\":\"购物\",\"icon_title\":\"立即购买\"}",
                    "{\"goto\":\"cart\",\"icon_url\":\"https://example.org/bag.png\",\"icon_title\":\"立即购买\"}",
                    "{\"goto\":\"cart\",\"icon_url\":\"https://example.org/bag.png\",\"icon_text\":\"购物\"}",
                    "{\"goto\":\"game\",\"icon_url\":\"https://example.org/game.png\",\"icon_text\":\"游戏\",\"icon_title\":\"游戏详情\"}",
                    "{\"goto\":\"vip\",\"icon_url\":\"https://example.org/vip.png\",\"icon_text\":\"大会员\",\"icon_title\":\"立即开通\"}",
                    "{\"goto\":\"music\",\"icon_url\":\"https://example.org/music.png\",\"icon_text\":\"音乐\",\"icon_title\":\"立即播放\"}",
                    "{\"goto\":\"unknown\",\"icon_url\":\"https://example.org/other.png\",\"icon_text\":\"更多\",\"icon_title\":\"购物 视频同款 立即购买\",\"uri\":\"https://mall.bilibili.com/\"}"
                };
                Method filter = loader.loadClass("app.revanced.bilibili.clean.CleanContent").getMethod("filterStory", List.class);
                List<Object> source = new ArrayList<>(), kept = new ArrayList<>();
                for (int i = 0; i < carts.length; i++) {
                    Object item = json("com.bilibili.video.story.StoryDetail", "{\"goto\":\"vertical_av\",\"bvid\":\"BV1YpZkBDEVo\",\"story_cart_icon\":" + carts[i] + "}");
                    source.add(item); if (i >= 6) kept.add(item);
                    check(((List<?>)filter.invoke(null, Arrays.asList(item))).size() == (i < 6 ? 0 : 1),
                        "shopping/non-shopping/invalid entry " + i);
                }
                Object valid = source.get(0).getClass().getMethod("getCartIconInfo").invoke(source.get(0));
                check(Boolean.TRUE.equals(valid.getClass().getMethod("isValid").invoke(valid)), "actual JSON cart satisfies host visibility validity");
                check(Boolean.FALSE.equals(source.get(0).getClass().getMethod("isAd").invoke(source.get(0))), "shopping bag filtered even on native non-ad card");
                for (String text : Arrays.asList(
                    "{\"goto\":\"vertical_av\",\"bvid\":\"BV1zJHK6gEJn\",\"title\":\"卖点正常内裤\"}",
                    "{\"goto\":\"vertical_av\",\"title\":\"购物 视频同款 立即购买\",\"desc\":\"https://mall.bilibili.com/\"}",
                    "{\"goto\":\"vertical_av\",\"ad_info\":{\"is_ad\":false},\"owner\":{\"name\":\"猫娘内裤泡水喝\"}}")) {
                    Object item = json("com.bilibili.video.story.StoryDetail", text);
                    check(((List<?>)filter.invoke(null, Arrays.asList(item))).size() == 1, "ordinary link/title/uploader/BV alone never matches");
                }
                Object data = loader.loadClass("com.bilibili.video.story.api.StoryFeedResponse$Data").getConstructor().newInstance();
                field(data, "items").set(data, Collections.unmodifiableList(source));
                check(kept.equals(data.getClass().getMethod("getItems").invoke(data)), "installed Story getter removes every valid shopping entry");
                check(source.equals(field(data, "items").get(data)), "cached source preserved");
                value.set(settings[8], false);
                check(source.equals(data.getClass().getMethod("getItems").invoke(data)), "switch off restores cached shopping videos");
                value.set(settings[8], true);
                check(kept.equals(data.getClass().getMethod("getItems").invoke(data)), "switch on reapplies to same cached list");
                Object ad = json("com.bilibili.video.story.StoryDetail", "{\"goto\":\"vertical_av\",\"ad_info\":{\"is_ad\":true},\"story_cart_icon\":" + carts[0] + "}");
                value.set(settings[8], false); value.set(settings[1], true);
                check(((List<?>)filter.invoke(null, Arrays.asList(ad))).isEmpty(), "other promotion filter remains independent when shopping switch is off");
                value.set(settings[1], false);
                check(((List<?>)filter.invoke(null, Arrays.asList(ad))).size() == 1, "all applicable switches off restore shopping ad");
                check(filter.invoke(null, new Object[]{null}) == null, "null response preserved");
            }
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
