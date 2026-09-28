package app.biliclean.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Real host model + installed DEX hooks, using controlled data, not live ad delivery. */
public final class ContentFilterTest extends Instrumentation {
    private ClassLoader loader;
    private Object gson;
    private Method parse;
    private final StringBuilder checks = new StringBuilder();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Object json(String type, String json) throws Exception {
        if (type.startsWith("com.bilibili.video.story."))
            return loader.loadClass("com.alibaba.fastjson.JSON").getMethod("parseObject",String.class,Class.class)
                .invoke(null,json,loader.loadClass(type));
        return parse.invoke(gson, json, loader.loadClass(type));
    }
    private static Field field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        checks.append("PASS ").append(text).append('\n');
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object[] settings = new Object[4], previous = new Object[4];
        Field value = null;
        try {
            loader = getTargetContext().getClassLoader();
            Class<?> settingClass = loader.loadClass(GeneratedNames.SETTINGS);
            value = loader.loadClass(GeneratedNames.SETTING).getDeclaredField(GeneratedNames.VALUE);
            value.setAccessible(true);
            String[] names = {GeneratedNames.ADS, GeneratedNames.BANNER, GeneratedNames.PROMOTION, GeneratedNames.MALL};
            for (int i=0;i<4;i++) { settings[i]=settingClass.getField(names[i]).get(null); previous[i]=value.get(settings[i]); }
            for (int i=1;i<4;i++) check(Boolean.TRUE.equals(previous[i]), "new setting defaults enabled " + names[i]);
            Class<?> gsonType = loader.loadClass("com.google.gson.Gson");
            gson=gsonType.getConstructor().newInstance(); parse=gsonType.getMethod("fromJson",String.class,Class.class);
            Method feed = loader.loadClass("app.revanced.bilibili.clean.CleanFeed").getMethod("filterModern",Object.class);
            Method tabs = loader.loadClass("app.revanced.bilibili.clean.CleanContent").getMethod("filterTabs",List.class);
            String card="Fs0.v", story="com.bilibili.video.story.StoryDetail";
            Object normal=json(card,"{\"card_goto\":\"av\",\"title\":\"创作推广 会员购 banner\"}");
            Object placeholder=json(card,"{\"card_goto\":\"av\",\"ad_info\":{\"is_ad\":false}}");
            Object banner=json(card,"{\"card_goto\":\"banner\",\"card_type\":\"banner_v8\"}");
            Object promoted=json(card,"{\"card_goto\":\"av\",\"rcmd_reason_style\":{\"text\":\"创作推广\"}}");
            Object mallCard=json(card,"{\"card_goto\":\"av\",\"uri\":\"https://mall.bilibili.com/test\"}");
            Object normalStory=json(story,"{\"goto\":\"av\",\"title\":\"创作推广 会员购\"}");
            Object adStory=json(story,"{\"goto\":\"vertical_ad_av\"}");
            Object softStory=json(story,"{\"goto\":\"av\",\"rcmd_reason\":\"创作推广\"}");
            Object infoStory=json(story,"{\"goto\":\"av\",\"ad_info\":{\"is_ad\":true}}");
            Object placeholderStory=json(story,"{\"goto\":\"av\",\"ad_info\":{\"is_ad\":false}}");
            Object mallStory=json(story,"{\"goto\":\"av\",\"uri\":\"bilibili://mall/home\"}");
            String tab="tv.danmaku.bili.ui.main2.resource.MainResourceManager$Tab";
            Object home=json(tab,"{\"name\":\"首页\",\"uri\":\"bilibili://pegasus/promo\"}");
            Object shop=json(tab,"{\"name\":\"会员购\",\"uri\":\"bilibili://mall/home\"}");
            Object vip=json(tab,"{\"name\":\"大会员\",\"uri\":\"bilibili://vip/home\"}");
            Object similar=json(tab,"{\"name\":\"普通入口\",\"uri\":\"https://mall.bilibili.com.example.org/test\"}");
            value.set(settings[0],false);
            for(int mask=0;mask<8;mask++) {
                boolean b=(mask&1)!=0,p=(mask&2)!=0,m=(mask&4)!=0;
                value.set(settings[1],b); value.set(settings[2],p); value.set(settings[3],m);
                Object response=loader.loadClass("com.bilibili.pegasus.data.base.PegasusResponse").getConstructor().newInstance();
                field(response,"a").set(response,new ArrayList<>(Arrays.asList(normal,placeholder,banner,promoted,mallCard)));
                feed.invoke(null,response);
                List<?> out=(List<?>)field(response,"a").get(response);
                check(out.equals(expected(new Object[]{normal,placeholder,banner,promoted,mallCard},new boolean[]{true,true,!b,!p,!m})),"home independent switches mask="+mask);
                Object data=loader.loadClass("com.bilibili.video.story.api.StoryFeedResponse$Data").getConstructor().newInstance();
                List<?> input=Arrays.asList(normalStory,placeholderStory,adStory,softStory,infoStory,mallStory);
                field(data,"items").set(data,input);
                out=(List<?>)data.getClass().getMethod("getItems").invoke(data);
                check(out.equals(expected(input.toArray(),new boolean[]{true,true,!p,!p,!p,!m})),"installed Story getter hook mask="+mask);
                check(((List<?>)field(data,"items").get(data)).size()==6,"Story source retained for switch restore mask="+mask);
                out=(List<?>)tabs.invoke(null,Arrays.asList(home,shop,vip,similar));
                check(out.equals(expected(new Object[]{home,shop,vip,similar},new boolean[]{true,!m,true,true})),"mall switch preserves VIP and similar domain mask="+mask);
            }
            // Actual obfuscated UI navigation models, including cached/default routes.
            value.set(settings[3],true);
            Object uiHome=loader.loadClass("tv.danmaku.bili.ui.main2.resource.x").getConstructor().newInstance();
            Object uiShop=loader.loadClass("tv.danmaku.bili.ui.main2.resource.x").getConstructor().newInstance();
            field(uiHome,"b").set(uiHome,"首页"); field(uiHome,"d").set(uiHome,"bilibili://pegasus/promo");
            field(uiShop,"b").set(uiShop,"会员购"); field(uiShop,"d").set(uiShop,"bilibili://mall/home");
            List<?> replaced=(List<?>)tabs.invoke(null,Arrays.asList(uiHome,uiShop));
            check(replaced.size()==2 && replaced.get(0)==uiHome && "设置".equals(field(replaced.get(1),"b").get(replaced.get(1))),"native bottom membership shop replaced with settings");
            check("会员购".equals(field(uiShop,"b").get(uiShop)),"cached source navigation retained");
            for(Object entry:Arrays.asList(home,shop)) {
                field(entry,"tabId").set(entry,entry==home?"home":"mall");
                field(entry,"icon").set(entry,"https://example.org/icon.png");
                field(entry,"iconSelected").set(entry,"https://example.org/selected.png");
            }
            Method nativeTabs=loader.loadClass("tv.danmaku.bili.ui.main2.resource.MainResourceManager")
                .getDeclaredMethod("e",int.class,List.class);
            nativeTabs.setAccessible(true);
            List<?> filteredNative=(List<?>)nativeTabs.invoke(null,0,new ArrayList<>(Arrays.asList(home,shop)));
            value.set(settings[3],false);
            List<?> originalNative=(List<?>)nativeTabs.invoke(null,0,new ArrayList<>(Arrays.asList(home,shop)));
            check(filteredNative.size()==2 && originalNative.size()==2 &&
                "设置".equals(field(filteredNative.get(1),"b").get(filteredNative.get(1))) &&
                "会员购".equals(field(originalNative.get(1),"b").get(originalNative.get(1))),"installed native navigation hook enabled/disabled");
            check(tabs.invoke(null,new Object[]{null})==null,"null list passes through");
            result.putString("stream",checks.toString());
        } catch(Throwable e) {
            result.putString("stream",checks+"FAIL "+e+" cause="+e.getCause()+"\n"); result.putString("failure",e.toString());
        } finally {
            try { if(value!=null) for(int i=0;i<4;i++) if(settings[i]!=null && previous[i]!=null) value.set(settings[i],previous[i]); }
            catch(Exception e) { result.putString("failure","restore: "+e); }
        }
        finish(result.containsKey("failure")?0:-1,result);
    }
    private static List<Object> expected(Object[] objects,boolean[] retain) {
        List<Object> result=new ArrayList<>(); for(int i=0;i<objects.length;i++) if(retain[i]) result.add(objects[i]); return result;
    }
}
