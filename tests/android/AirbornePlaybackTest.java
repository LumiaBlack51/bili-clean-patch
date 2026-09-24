package app.biliclean.tests;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** AVD-only tests: real public video, real community markers and the host's actual media player. */
public final class AirbornePlaybackTest extends Instrumentation {
    private Object player;
    private Activity activity;
    private Class<?> mediaType;
    private SharedPreferences prefs;
    private final StringBuilder evidence = new StringBuilder();
    private long start = 884079, end = 1035468;
    private String video = "BV1YpZkBDEVo";
    private long introEnd = 8000, outroStart = 1965500, outroEnd = 1973525;
    private boolean fixtures = true;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        if ("1047".equals(args.getString("episode"))) {
            video = "BV1DuZFB8EZi"; start = 204911; end = 298888;
            introEnd = 8027; outroStart = 1078500; outroEnd = 1101120;
        }
        fixtures = !"true".equals(args.getString("baseline"));
        start();
    }
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private Object call(String method) throws Exception { return mediaType.getMethod(method).invoke(player); }
    private long position() throws Exception { return ((Number)call("getCurrentPosition")).longValue(); }
    private void control(String method) { runOnMainSync(() -> { try { call(method); } catch (Exception e) { throw new RuntimeException(e); } }); }
    private void seek(long pos) { runOnMainSync(() -> { try { mediaType.getMethod("seekTo", long.class).invoke(player, pos); } catch (Exception e) { throw new RuntimeException(e); } }); }
    private void mode(String mode) { prefs.edit().putString("sponsor", mode).commit(); SystemClock.sleep(400); }
    private void inside() throws Exception { seek(start + 4000); control("start"); SystemClock.sleep(1800); }
    private void assertInside(String label) throws Exception {
        long p = position(); check(p >= start && p < start + 30000, label + " unexpected position=" + p);
        evidence.append("PASS ").append(label).append(" position=").append(p).append('\n');
    }
    private void awaitSkipped(String label) throws Exception {
        long until = SystemClock.uptimeMillis() + 15000;
        while (position() < end - 500 && SystemClock.uptimeMillis() < until) SystemClock.sleep(200);
        long p = position(); check(p >= end - 500 && p < end + 30000, label + " seek not observed=" + p);
        evidence.append("PASS ").append(label).append(" position=").append(p).append('\n');
    }
    private TextView findText(View v, String prefix) {
        if (v instanceof TextView && ((TextView)v).getText().toString().startsWith(prefix) && v.isShown()) return (TextView)v;
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            TextView result = findText(((ViewGroup)v).getChildAt(i), prefix); if (result != null) return result;
        }
        return null;
    }
    private boolean hasCategoryTitle(View v) {
        if (v instanceof TextView && safeName(v).equals("title") && v.isShown() &&
            ((TextView)v).getText().toString().contains("赞助/恰饭")) return true;
        if (v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++)
            if(hasCategoryTitle(((ViewGroup)v).getChildAt(i))) return true;
        return false;
    }
    private void snapshot(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot(); check(image != null, "screenshot unavailable");
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }
    private void tree(View v, StringBuilder text, int level) {
        String id = v.getId() == View.NO_ID ? "" : safeName(v);
        text.append(" ".repeat(Math.min(level, 40))).append(v.getClass().getName()).append(" ").append(id)
            .append(" ").append(v.getWidth()).append('x').append(v.getHeight()).append(" shown=").append(v.isShown()).append('\n');
        if (v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) tree(((ViewGroup)v).getChildAt(i),text,level+1);
    }
    private String safeName(View v) { try { return v.getResources().getResourceEntryName(v.getId()); } catch(Exception e) { return ""; } }
    private View findId(View v, String name) {
        if (name.equals(safeName(v)) && v.isShown()) return v;
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            View found = findId(((ViewGroup)v).getChildAt(i), name); if (found != null) return found;
        }
        return null;
    }
    private void tap(int x, int y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0);
        getUiAutomation().injectInputEvent(down, true); getUiAutomation().injectInputEvent(up, true);
        down.recycle(); up.recycle();
    }
    private void manualCategory(String title, long from, long target, String screen) throws Exception {
        control("pause"); seek(from); SystemClock.sleep(800); control("start"); SystemClock.sleep(600);
        Rect bounds = new Rect();
        final boolean[] visible = {false};
        runOnMainSync(() -> {
            TextView button = findText(activity.getWindow().getDecorView(), "跳过" + title);
            visible[0] = button != null && button.getGlobalVisibleRect(bounds);
        });
        check(visible[0], screen + " manual " + title + " missing");
        // Freeze natural playback so reaching target proves the touch actually caused a seek.
        control("pause"); SystemClock.sleep(500);
        if (screen.contains("reattach")) {
            runOnMainSync(() -> {
                TextView old = findText(activity.getWindow().getDecorView(), "跳过" + title);
                if (old != null) ((ViewGroup)old.getParent()).removeView(old);
            });
            SystemClock.sleep(600);
            final boolean[] restored = {false};
            runOnMainSync(() -> {
                TextView replacement = findText(activity.getWindow().getDecorView(), "跳过" + title);
                restored[0] = replacement != null && replacement.getGlobalVisibleRect(bounds);
            });
            check(restored[0], "Manual button lost after host detaches overlay");
        }
        snapshot("airborne-manual-" + screen);
        tap(bounds.centerX(), bounds.centerY()); SystemClock.sleep(900);
        long until = SystemClock.uptimeMillis() + 7000;
        while (position() < target - 500 && SystemClock.uptimeMillis() < until) SystemClock.sleep(200);
        long p = position(); check(p >= target - 500, screen + " manual button not touchable position=" + p);
        evidence.append("PASS manual ").append(title).append(' ').append(screen).append(" actual touch position=").append(p).append('\n');
    }
    /** Controlled mute annotation in the real player; this is NOT a live community mute sample. */
    private void muteFixture(Class<?> runtime, ClassLoader loader) throws Exception {
        Field engineField = runtime.getDeclaredField(GeneratedNames.ENGINE_FIELD); engineField.setAccessible(true);
        Object engine = engineField.get(null);
        Class<?> segmentType = loader.loadClass(GeneratedNames.SEGMENT);
        Field category = segmentType.getDeclaredField(GeneratedNames.SEGMENT_CATEGORY); category.setAccessible(true);
        Field action = segmentType.getDeclaredField(GeneratedNames.SEGMENT_ACTION); action.setAccessible(true);
        Object intro = null;
        for (Field field : engine.getClass().getDeclaredFields()) {
            field.setAccessible(true); Object values = field.get(engine);
            if (values instanceof List) for (Object segment : (List<?>)values)
                if (segmentType.isInstance(segment) && "intro".equals(category.get(segment))) intro = segment;
        }
        check(intro != null, "No intro for controlled mute fixture");
        Object original = action.get(intro);
        try {
            action.set(intro, "mute");
            manualCategory("过场/开场动画", 1500, introEnd, "controlled-mute");
        } finally { action.set(intro, original); }
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Map<String, ?> previous = null;
        try {
            prefs = getTargetContext().getSharedPreferences("clean_airborne_categories", Context.MODE_PRIVATE);
            previous = new HashMap<>(prefs.getAll()); mode("SHOW");
            ClassLoader loader = getTargetContext().getClassLoader();
            mediaType = loader.loadClass("tv.danmaku.ijk.media.player.IMediaPlayer");
            Class<?> runtime = loader.loadClass("app.revanced.bilibili.clean.CleanRuntime");
            getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/" + video))
                .setPackage("tv.danmaku.bili").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until = SystemClock.uptimeMillis() + 45000;
            while (SystemClock.uptimeMillis() < until) {
                runOnMainSync(() -> { try {
                    for (Field f : runtime.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == WeakReference.class) {
                        f.setAccessible(true); Object object = ((WeakReference<?>)f.get(null)).get();
                        if (mediaType.isInstance(object)) player = object;
                        if (object instanceof Activity) activity = (Activity)object;
                    }
                } catch(Exception e) { throw new RuntimeException(e); } });
                if (player != null && activity != null && ((Number)call("getDuration")).longValue() > end) break;
                SystemClock.sleep(300);
            }
            check(player != null && activity != null, "Real host player not ready");
            SystemClock.sleep(10000);
            runOnMainSync(() -> check(hasCategoryTitle(activity.getWindow().getDecorView()), "Detail title category missing"));
            evidence.append("PASS real-video detail category\n");
            StringBuilder viewTree = new StringBuilder();
            runOnMainSync(() -> tree(activity.getWindow().getDecorView(), viewTree, 0));
            try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), "airborne-view-tree.txt"))) {
                out.write(viewTree.toString().getBytes("UTF-8"));
            }
            snapshot("airborne-test-detail");
            inside(); assertInside("show-only does not seek");
            mode("DISABLED"); inside(); assertInside("disabled does not seek");
            runOnMainSync(() -> check(!hasCategoryTitle(activity.getWindow().getDecorView()), "Disabled title still shown"));
            snapshot("airborne-test-disabled");
            control("pause"); seek(start - 10000); SystemClock.sleep(700);
            mode("ONCE"); inside(); awaitSkipped("once skips first encounter");
            inside(); assertInside("once allows rewind after first skip");
            mode("MANUAL"); inside(); assertInside("manual waits for click");
            snapshot("airborne-test-manual-before");
            runOnMainSync(() -> {
                TextView button = findText(activity.getWindow().getDecorView(), "跳过赞助/恰饭");
                check(button != null, "Manual skip button absent"); button.performClick();
            });
            awaitSkipped("manual button moves actual player"); snapshot("airborne-test-manual-after");
            mode("ONCE"); inside(); assertInside("once retains already handled marker");
            mode("ALWAYS"); inside(); awaitSkipped("always skips marked interval");
            inside(); awaitSkipped("always skips again after rewind");
            mode("ONCE"); inside(); assertInside("once allows rewind");
            mode("ALWAYS"); control("pause"); seek(start + 6000); SystemClock.sleep(2500);
            assertInside("paused playback never auto-skips"); snapshot("airborne-test-paused");
            control("start"); awaitSkipped("resume triggers auto-skip");
            SharedPreferences.Editor modes = prefs.edit().putString("sponsor", "ALWAYS");
            for (String category : Arrays.asList("selfpromo", "interaction", "intro", "outro", "preview", "padding", "filler", "music_offtopic"))
                modes.putString(category, "MANUAL");
            modes.commit();
            manualCategory("过场/开场动画", 1500, introEnd, "portrait-intro");
            manualCategory("鸣谢/结束画面", outroStart, outroEnd, "portrait-outro");
            if (fixtures) {
                manualCategory("过场/开场动画", 1500, introEnd, "portrait-reattach");
                muteFixture(runtime, loader);
            }
            control("pause"); seek(20000); SystemClock.sleep(600);
            tap(540, 400); SystemClock.sleep(500);
            runOnMainSync(() -> {
                View expand = findId(activity.getWindow().getDecorView(), "gemini_halfscreen_expand");
                check(expand != null, "Native fullscreen control missing"); expand.performClick();
            });
            SystemClock.sleep(2500);
            check(activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                "Native fullscreen did not enter landscape");
            manualCategory("过场/开场动画", 1500, introEnd, "landscape-intro");
            manualCategory("鸣谢/结束画面", outroStart, outroEnd, "landscape-outro");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(1800);
            getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/BV1zT411K7Y9"))
                .setPackage("tv.danmaku.bili").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(12000);
            runOnMainSync(() -> { try {
                for(Field f : runtime.getDeclaredFields()) if(Modifier.isStatic(f.getModifiers()) && f.getType() == WeakReference.class) {
                    f.setAccessible(true); Object value = ((WeakReference<?>)f.get(null)).get();
                    if(value instanceof Activity) activity = (Activity)value;
                }
                check(!hasCategoryTitle(activity.getWindow().getDecorView()), "Previous video badge leaked");
                TextView marker = findText(activity.getWindow().getDecorView(), "空降助手 · 此视频暂无");
                check(marker != null, "Unmarked video response has not arrived");
            } catch(Exception e) { throw new RuntimeException(e); } });
            evidence.append("PASS switch to unmarked video clears previous category\n");
            snapshot("airborne-test-unmarked");
            result.putString("stream", evidence.toString());
        } catch(Throwable failure) {
            result.putString("stream", evidence + "FAIL " + failure + " cause=" + failure.getCause() + "\n");
            result.putString("failure", failure.toString());
            try { snapshot("airborne-test-failure"); } catch(Exception ignored) {}
        } finally {
            if (prefs != null && previous != null) {
                SharedPreferences.Editor editor = prefs.edit().clear();
                for (Map.Entry<String, ?> entry : previous.entrySet()) editor.putString(entry.getKey(), (String)entry.getValue());
                editor.commit();
            }
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
