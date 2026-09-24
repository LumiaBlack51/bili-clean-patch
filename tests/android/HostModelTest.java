package app.biliclean.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Controlled fixtures inside the actual host process; never evidence of live ad delivery. */
public final class HostModelTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object setting = null, previous = null;
        Field valueField = null;
        try {
            ClassLoader loader = getTargetContext().getClassLoader();
            Class<?> settings = loader.loadClass(GeneratedNames.SETTINGS);
            setting = settings.getField(GeneratedNames.ADS).get(null);
            valueField = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE);
            valueField.setAccessible(true);
            previous = valueField.get(setting);
            Class<?> gsonType = loader.loadClass("com.google.gson.Gson");
            Object gson = gsonType.getConstructor().newInstance();
            Method parse = gsonType.getMethod("fromJson", String.class, Class.class);
            Class<?> cardType = loader.loadClass("Fs0.v");
            Object normal = parse.invoke(gson, "{\"card_goto\":\"av\",\"title\":\"fixture normal\"}", cardType);
            Object ad = parse.invoke(gson, "{\"card_goto\":\"cm\",\"title\":\"fixture ad\"}", cardType);
            Object infoAd = parse.invoke(gson, "{\"card_goto\":\"av\",\"ad_info\":{\"is_ad\":true}}", cardType);
            Object placeholder = parse.invoke(gson, "{\"card_goto\":\"av\",\"ad_info\":{\"is_ad\":false}}", cardType);
            if (!"cm".equals(cardType.getMethod("getCardGoto").invoke(ad)) ||
                cardType.getMethod("getAdInfo").invoke(infoAd) == null)
                throw new AssertionError("Host JSON annotations do not match inspected version");
            Class<?> responseType = loader.loadClass("com.bilibili.pegasus.data.base.PegasusResponse");
            Field items = responseType.getDeclaredField("a"); items.setAccessible(true);
            Method filter = loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filterModern", Object.class);
            for (boolean enabled : new boolean[]{true, false}) {
                valueField.set(setting, enabled);
                Object response = responseType.getConstructor().newInstance();
                items.set(response, new ArrayList<>(Arrays.asList(normal, placeholder, ad, infoAd)));
                filter.invoke(null, response);
                List<?> output = (List<?>) items.get(response);
                if (output.size() != (enabled ? 2 : 4) || output.get(0) != normal || output.get(1) != placeholder)
                    throw new AssertionError("Unexpected filter output, enabled=" + enabled);
            }
            StringBuilder checks = new StringBuilder("PASS controlled host-model fixtures: actual Gson annotations, ad type, is_ad, ordinary and non-ad placeholder retention, disabled pass-through. Not live-ad/UI acceptance.\n");
            Class<?> pauseType = loader.loadClass("com.bilibili.ad.adview.videodetail.pausedpage.VDPausedPage");
            Object service = pauseType.getField("INSTANCE").get(null);
            valueField.set(setting, true);
            int pausedEntries = 0;
            for (Method entry : pauseType.getMethods()) {
                if (!entry.getName().equals("requestPausedPage") && !entry.getName().equals("decodeViewEndPagePausedPage")) continue;
                Object[] args = new Object[entry.getParameterCount()];
                Class<?>[] types = entry.getParameterTypes();
                for (int i=0;i<types.length;i++) {
                    if (types[i] == long.class) args[i] = 0L;
                    else if (types[i] == int.class) args[i] = 0;
                }
                if (entry.invoke(service, args) != null) throw new AssertionError("Pause ad not suppressed: " + entry.getName());
                pausedEntries++;
                checks.append("PASS installed host pause-ad entry blocked: ").append(entry.getName()).append('\n');
            }
            if (pausedEntries != 2) throw new AssertionError("Missing pause-ad entrypoints");
            valueField.set(setting, false);
            Method guard = loader.loadClass("app.revanced.bilibili.clean.CleanPauseAds").getMethod("block");
            if (!Boolean.FALSE.equals(guard.invoke(null))) throw new AssertionError("Pause-ad disabled guard failed");
            checks.append("PASS pause-ad guard respects disabled ads setting\n");
            result.putString("stream", checks.toString());
        } catch (Throwable failure) {
            result.putString("stream", "FAIL " + failure + " cause=" + failure.getCause() + "\n");
            result.putString("failure", failure.toString());
        } finally {
            try { if (valueField != null && previous != null) valueField.set(setting, previous); }
            catch (Throwable error) { result.putString("failure", "Could not restore setting: " + error); }
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
