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
        Method save = null;
        try {
            ClassLoader loader = getTargetContext().getClassLoader();
            Class<?> settings = loader.loadClass("app.revanced.bilibili.settings.Settings");
            setting = settings.getField("CleanAds").get(null);
            previous = setting.getClass().getMethod("get").invoke(setting);
            save = Arrays.stream(setting.getClass().getMethods())
                .filter(m -> m.getName().equals("save") && m.getParameterCount() == 1).findFirst().orElseThrow();
            Class<?> gsonType = loader.loadClass("com.google.gson.Gson");
            Object gson = gsonType.getConstructor().newInstance();
            Method parse = gsonType.getMethod("fromJson", String.class, Class.class);
            Class<?> cardType = loader.loadClass("Fs0.v");
            Object normal = parse.invoke(gson, "{\"card_goto\":\"av\",\"title\":\"fixture normal\"}", cardType);
            Object ad = parse.invoke(gson, "{\"card_goto\":\"cm\",\"title\":\"fixture ad\"}", cardType);
            Object infoAd = parse.invoke(gson, "{\"card_goto\":\"av\",\"ad_info\":{}}", cardType);
            if (!"cm".equals(cardType.getMethod("getCardGoto").invoke(ad)) ||
                cardType.getMethod("getAdInfo").invoke(infoAd) == null)
                throw new AssertionError("Host JSON annotations do not match inspected version");
            Class<?> responseType = loader.loadClass("com.bilibili.pegasus.data.base.PegasusResponse");
            Field items = responseType.getDeclaredField("a"); items.setAccessible(true);
            Method filter = loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filterModern", Object.class);
            for (boolean enabled : new boolean[]{true, false}) {
                save.invoke(setting, enabled);
                Object response = responseType.getConstructor().newInstance();
                items.set(response, new ArrayList<>(Arrays.asList(normal, ad, infoAd)));
                filter.invoke(null, response);
                List<?> output = (List<?>) items.get(response);
                if (output.size() != (enabled ? 1 : 3) || output.get(0) != normal)
                    throw new AssertionError("Unexpected filter output, enabled=" + enabled);
            }
            result.putString("stream", "PASS controlled host-model fixtures: actual Gson annotations, ad type, ad_info, normal retention, disabled pass-through. Not live-ad/UI acceptance.\n");
        } catch (Throwable failure) {
            result.putString("stream", "FAIL " + failure + " cause=" + failure.getCause() + "\n");
            result.putString("failure", failure.toString());
        } finally {
            try { if (save != null && previous != null) save.invoke(setting, previous); }
            catch (Throwable error) { result.putString("failure", "Could not restore setting: " + error); }
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
