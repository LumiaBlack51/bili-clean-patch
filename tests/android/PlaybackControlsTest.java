package app.biliclean.tests;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;

/** Runs only on the AVD, against actual host playback and injected touchscreen events. */
public class PlaybackControlsTest extends Instrumentation {
    Class<?> playback, mediaType;
    Object player;
    Activity activity;
    boolean updateMode;
    long installDownload=-1;
    StringBuilder report = new StringBuilder();
    interface Work { void run() throws Exception; }
    void ui(Work work) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { failure.set(e); } });
        if(failure.get()!=null)throw new RuntimeException(failure.get());
    }
    Object api(String name, Class<?>[] types, Object... args) throws Exception { return playback.getMethod(name, types).invoke(null, args); }
    Object api(String name) throws Exception { return api(name, new Class<?>[0]); }
    Object value(String field) throws Exception { Field f = playback.getDeclaredField(field); f.setAccessible(true); return f.get(null); }
    Object ref(String field) throws Exception { return ((WeakReference<?>)value(field)).get(); }
    Object media(String name) throws Exception { return mediaType.getMethod(name).invoke(player); }
    Object container() throws Exception { Object c=ref("core");Field f=c.getClass().getDeclaredField("a");f.setAccessible(true);return f.get(c); }
    Object core(String name) throws Exception { Object box=container();Method getter=box.getClass().getMethod("getPlayerCoreService");getter.setAccessible(true);Object c=getter.invoke(box);Method m=c.getClass().getMethod(name);m.setAccessible(true);return m.invoke(c); }
    long position() throws Exception { return ((Number)media("getCurrentPosition")).longValue(); }
    void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); report.append("PASS ").append(label).append('\n');android.util.Log.i("BiliCleanTest","PASS "+label); }
    String name(View v) { try { return v.getResources().getResourceEntryName(v.getId()); } catch(Exception e) { return ""; } }
    View find(View v, String query) {
        if (query.equals(name(v)) || query.equals(v.getTag()) || v instanceof TextView && query.equals(((TextView)v).getText().toString())) return v;
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) { View f=find(((ViewGroup)v).getChildAt(i),query); if(f!=null)return f; }
        return null;
    }
    void screenBounds(View view,Rect r) { int[] p=new int[2];view.getLocationOnScreen(p);r.set(p[0],p[1],p[0]+view.getWidth(),p[1]+view.getHeight()); }
    Rect bounds(View view) { Rect r=new Rect(); ui(() -> screenBounds(view,r)); return r; }
    Rect playerBounds() { final Rect r = new Rect(); ui(() -> { View v=find(activity.getWindow().getDecorView(),"control_container"); if(v!=null)screenBounds(v,r); }); return r; }
    void tap(float x,float y) {
        long now=SystemClock.uptimeMillis(); MotionEvent down=MotionEvent.obtain(now,now,0,x,y,0),up=MotionEvent.obtain(now,now+45,1,x,y,0);
        getUiAutomation().injectInputEvent(down,true); getUiAutomation().injectInputEvent(up,true); down.recycle();up.recycle();
    }
    void doubleTap(float fraction) { Rect r=playerBounds(); float x=r.left+r.width()*fraction,y=r.top+r.height()*.70f;tap(x,y);SystemClock.sleep(85);tap(x,y);SystemClock.sleep(1100); }
    void seek(long position) throws Exception { ui(() -> mediaType.getMethod("seekTo",long.class).invoke(player,position));SystemClock.sleep(1300); }
    void pause() { ui(() -> core("pause"));SystemClock.sleep(250); }
    void capture(String file) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),file+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    void controlsVisible() {
        for(int tries=0;tries<3;tries++) {
            final boolean[] shown={false}; ui(()->{View v=find(activity.getWindow().getDecorView(),"biliclean_center_controls");shown[0]=v!=null&&v.isShown();});if(shown[0])return;
            Rect r=playerBounds();tap(r.centerX(),r.top+r.height()*.22f);
            for(int poll=0;poll<8;poll++) {
                SystemClock.sleep(200);
                ui(()->{View v=find(activity.getWindow().getDecorView(),"biliclean_center_controls");shown[0]=v!=null&&v.isShown()&&v.getWidth()>0;});
                if(shown[0])return;
            }
        }
        throw new AssertionError("Single tap did not reveal middle controls");
    }
    Dialog timerDialog() throws Exception {
        ClassLoader loader=getTargetContext().getClassLoader();Object container=container();
        Method getter=container.getClass().getMethod("getPlayerServiceManager");getter.setAccessible(true);Object manager=getter.invoke(container);
        Class<?> callback=loader.loadClass("Du0.c$b");
        Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{callback},(p,m,a)->{
            if(m.getName().equals("bindService")||m.getName().equals("unbindService"))for(Method target:manager.getClass().getMethods())if(target.getName().equals(m.getName())&&target.getParameterTypes().length==2){target.setAccessible(true);return target.invoke(manager,a);}
            return null;
        });
        Dialog dialog=(Dialog)loader.loadClass("Du0.c").getConstructor(Context.class,callback).newInstance(activity,proxy);dialog.show();return dialog;
    }
    void navigationDispatchFixture() throws Exception {
        Object box=container();Method getter=box.getClass().getMethod("getPlayDirectorServiceV3");getter.setAccessible(true);Object service=getter.invoke(box);
        Method controller=service.getClass().getMethod("c");controller.setAccessible(true);Object original=controller.invoke(service);
        Class<?> controllerType=getTargetContext().getClassLoader().loadClass("tv.danmaku.biliplayerv2.service.k$a");
        Method setter=service.getClass().getMethod("b",controllerType);setter.setAccessible(true);
        int[] calls={0,0};
        Object proxy=Proxy.newProxyInstance(getTargetContext().getClassLoader(),new Class<?>[]{controllerType},(p,m,a)->{
            if(m.getName().equals("hasNext")||m.getName().equals("hasPrevious"))return true;
            if(m.getName().equals("switchToPrevious")){calls[0]++;return null;}
            if(m.getName().equals("switchToNext")){calls[1]++;return null;}
            return m.invoke(original,a);
        });
        try {
            ui(()->setter.invoke(service,proxy));SystemClock.sleep(600);controlsVisible();
            for(String label:new String[]{"上一集","下一集"}){
                controlsVisible();
                final Rect r=new Rect();ui(()->{View v=find(activity.getWindow().getDecorView(),"biliclean_"+label);check(v!=null&&v.isEnabled(),label+" enabled when native playlist reports availability");int[] point=new int[2];v.getLocationOnScreen(point);r.set(point[0],point[1],point[0]+v.getWidth(),point[1]+v.getHeight());});
                tap(r.centerX(),r.centerY());SystemClock.sleep(250);
            }
            check(calls[0]==1&&calls[1]==1,"Controlled playlist fixture: actual previous/next touches dispatch to matching native API ("+calls[0]+","+calls[1]+")");
        } finally {ui(()->setter.invoke(service,original));SystemClock.sleep(400);}
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args);updateMode="true".equals(args.getString("update"));if(args.containsKey("installDownload"))installDownload=Long.parseLong(args.getString("installDownload"));start(); }
    void openDownloadedInstaller() {
        Bundle result=new Bundle();try {
            Intent intent=new Intent().setClassName("tv.danmaku.bili","tv.danmaku.bili.MainActivityV2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);SystemClock.sleep(1500);
            SharedPreferences prefs=getTargetContext().getSharedPreferences("biliclean_playback",0);
            long pending=prefs.getLong("update_download",-1);
            if(pending>=0&&pending!=installDownload)((android.app.DownloadManager)getTargetContext().getSystemService(Context.DOWNLOAD_SERVICE)).remove(pending);
            prefs.edit().putLong("update_download",installDownload).commit();
            Class<?> update=getTargetContext().getClassLoader().loadClass("app.revanced.bilibili.clean.CleanUpdate");
            ui(()->update.getMethod("resumed",Activity.class).invoke(null,activity));SystemClock.sleep(2000);
            result.putString("results","Reopened completed download in system installer; duplicate test request removed");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("failure",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
    void updateCheck() {
        Bundle result=new Bundle();
        try {
            ClassLoader loader=getTargetContext().getClassLoader();
            Class<?> update=loader.loadClass("app.revanced.bilibili.clean.CleanUpdate");
            Intent intent=new Intent().setClassName("tv.danmaku.bili","tv.danmaku.bili.MainActivityV2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);SystemClock.sleep(6000);
            java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new java.net.URL("https://api.github.com/repos/LumiaBlack51/bili-clean-patch/releases/latest").openConnection();
            connection.setConnectTimeout(20000);connection.setReadTimeout(30000);connection.setRequestProperty("User-Agent","BiliClean-AVD-Acceptance");
            org.json.JSONObject release;
            try {check(connection.getResponseCode()==200,"AVD anonymously reads public GitHub latest Release");try(java.io.InputStream in=connection.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);release=new org.json.JSONObject(out.toString("UTF-8"));}} finally {connection.disconnect();}
            org.json.JSONArray assets=release.getJSONArray("assets");org.json.JSONObject asset=null;
            for(int i=0;i<assets.length();i++)if(assets.getJSONObject(i).getString("name").endsWith(".apk"))asset=assets.getJSONObject(i);
            check(asset!=null,"Release provides installable APK asset");
            final org.json.JSONObject selected=asset;
            Method download=update.getDeclaredMethod("download",Activity.class,org.json.JSONObject.class);download.setAccessible(true);
            SharedPreferences prefs=getTargetContext().getSharedPreferences("biliclean_playback",0);
            if(prefs.getLong("update_download",-1)<0)ui(()->download.invoke(update.getField("INSTANCE").get(null),activity,selected));
            long id=prefs.getLong("update_download",-1);check(id>=0,"Updater enqueues real release APK download");
            android.app.DownloadManager manager=(android.app.DownloadManager)getTargetContext().getSystemService(Context.DOWNLOAD_SERVICE);
            long until=SystemClock.uptimeMillis()+300000;int status=0;
            while(SystemClock.uptimeMillis()<until){try(android.database.Cursor c=manager.query(new android.app.DownloadManager.Query().setFilterById(id))){if(c.moveToFirst()){
                status=c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                android.util.Log.i("BiliCleanTest","download status="+status+" reason="+c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON))+" bytes="+c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
            }}if(status==android.app.DownloadManager.STATUS_SUCCESSFUL||status==android.app.DownloadManager.STATUS_FAILED)break;SystemClock.sleep(5000);}
            check(status==android.app.DownloadManager.STATUS_SUCCESSFUL,"GitHub APK download completes on AVD");
            SystemClock.sleep(4000);check(prefs.getLong("update_download",-1)==-1,"Updater hands downloaded APK to system installer");
            result.putString("results",report.toString());finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("failure",android.util.Log.getStackTraceString(e));result.putString("results",report.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    @Override public void onStart() {
        if(installDownload>=0){openDownloadedInstaller();return;}
        if(updateMode){updateCheck();return;}
        Bundle result=new Bundle(); boolean previous=false;
        try {
            ClassLoader loader=getTargetContext().getClassLoader(); playback=loader.loadClass("app.revanced.bilibili.clean.CleanPlayback");
            mediaType=loader.loadClass("tv.danmaku.ijk.media.player.IMediaPlayer");previous=(Boolean)api("enabled");
            ui(()->api("setEnabled",new Class<?>[]{boolean.class},true));
            // Disable automatic skipping only in this AVD test, so gesture deltas are measurable.
            getTargetContext().getSharedPreferences("clean_airborne_categories",0).edit().putString("sponsor","SHOW").putString("intro","SHOW").putString("outro","SHOW").commit();
            getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("bilibili://video/BV1DuZFB8EZi")).setPackage("tv.danmaku.bili").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.uptimeMillis()+60000;
            while(SystemClock.uptimeMillis()<until) {
                ui(()->{player=ref("media");activity=(Activity)ref("owner");});
                if(player!=null&&activity!=null&&((Number)media("getDuration")).longValue()>100000)break;
                SystemClock.sleep(500);
            }
            check(player!=null&&activity!=null,"Native Bilibili player bound");SystemClock.sleep(4000);
            pause();seek(120000); long start=position();doubleTap(.90f);long after=position();
            check(Math.abs(after-start-10000)<1500,"Portrait right double tap +10s: "+start+" -> "+after);
            start=position();doubleTap(.10f);after=position();check(Math.abs(after-start+10000)<1500,"Portrait left double tap -10s");
            check(!(Boolean)media("isPlaying"),"YouTube double taps preserve paused state");
            start=position();doubleTap(.5f);check(Math.abs(position()-start)<1500&&!(Boolean)media("isPlaying"),"Middle double tap neither seeks nor pauses/resumes");
            controlsVisible();capture("youtube-portrait");
            final Rect play=new Rect();ui(()->screenBounds(find(activity.getWindow().getDecorView(),"biliclean_暂停或播放"),play));
            tap(play.centerX(),play.centerY());SystemClock.sleep(900);check((Boolean)media("isPlaying"),"Center play button starts native playback");pause();
            // Both playlist controls exist; unavailable directions are explicitly disabled.
            ui(()->{check(find(activity.getWindow().getDecorView(),"biliclean_上一集")!=null,"Previous episode control present");check(find(activity.getWindow().getDecorView(),"biliclean_下一集")!=null,"Next episode control present");});
            navigationDispatchFixture();
            final Rect expand=new Rect();ui(()->{View v=find(activity.getWindow().getDecorView(),"gemini_halfscreen_expand");if(v!=null)v.getGlobalVisibleRect(expand);});
            check(!expand.isEmpty(),"Native fullscreen control available");ui(()->find(activity.getWindow().getDecorView(),"gemini_halfscreen_expand").performClick());SystemClock.sleep(2500);
            check(activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"Fullscreen really entered landscape");
            pause();seek(120000);start=position();doubleTap(.9f);check(Math.abs(position()-start-10000)<1500,"Fullscreen right double tap +10s");
            start=position();doubleTap(.1f);check(Math.abs(position()-start+10000)<1500,"Fullscreen left double tap -10s");controlsVisible();capture("youtube-fullscreen");
            ui(()->api("setEnabled",new Class<?>[]{boolean.class},false));SystemClock.sleep(700);
            ui(()->{View v=find(activity.getWindow().getDecorView(),"biliclean_center_controls");check(v==null||!v.isShown(),"Optional switch hides middle controls");});
            pause();doubleTap(.9f);check((Boolean)media("isPlaying"),"Disabling feature restores native double-tap play/pause");pause();
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(2000);
            // Open the exact native portrait timer dialog and select its newly injected row.
            final Dialog[] dialog={null};
            ui(()->{
                Object container=container();
                Method getter=container.getClass().getMethod("getPlayerServiceManager");getter.setAccessible(true);Object manager=getter.invoke(container);
                Class<?> callback=loader.loadClass("Du0.c$b");
                Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{callback},(p,m,a)->{
                    if(m.getName().equals("bindService")||m.getName().equals("unbindService"))for(Method target:manager.getClass().getMethods())if(target.getName().equals(m.getName())&&target.getParameterTypes().length==2){target.setAccessible(true);return target.invoke(manager,a);}
                    return null;
                });
                dialog[0]=(Dialog)loader.loadClass("Du0.c").getConstructor(Context.class,callback).newInstance(activity,proxy);dialog[0].show();
            });SystemClock.sleep(900);
            ui(()->{ View list=find(dialog[0].getWindow().getDecorView(),"timing_list");if(list!=null)list.getClass().getMethod("scrollToPosition",int.class).invoke(list,5); });
            SystemClock.sleep(700);capture("timer-video-end");
            final Rect endRow=new Rect();ui(()->{View v=find(dialog[0].getWindow().getDecorView(),"播放到当前视频结束");if(v!=null){int[] p=new int[2];v.getLocationOnScreen(p);endRow.set(p[0],p[1],p[0]+v.getWidth(),p[1]+v.getHeight());}});
            check(!endRow.isEmpty(),"End-of-video option in native timer menu");
            tap(endRow.centerX(),endRow.centerY());SystemClock.sleep(500);check((Boolean)api("isEndTimerArmed"),"Native timer row arms completion timer");
            ui(()->dialog[0]=timerDialog());SystemClock.sleep(500);
            ui(()->{
                Field adapterField=dialog[0].getClass().getDeclaredField("b");adapterField.setAccessible(true);Object adapter=adapterField.get(dialog[0]);
                Field selected=adapter.getClass().getDeclaredField("c");selected.setAccessible(true);check(selected.getInt(adapter)==6,"Reopening timer remembers end-of-video selection");
                Field clientField=dialog[0].getClass().getDeclaredField("f");clientField.setAccessible(true);Object client=clientField.get(dialog[0]);
                Method serviceGetter=client.getClass().getMethod("getService");serviceGetter.setAccessible(true);Object service=serviceGetter.invoke(client);
                service.getClass().getMethod("startShutOffTiming",long.class,boolean.class).invoke(service,0L,false);
                check(!(Boolean)api("isEndTimerArmed"),"Native timer off cancels end-of-video mode");dialog[0].dismiss();api("armEndTimer");
            });
            SystemClock.sleep(2200);check(!activity.isFinishing(),"Paused video does not trigger end timer");
            long duration=((Number)media("getDuration")).longValue();seek(duration-4000);ui(()->core("resume"));
            until=SystemClock.uptimeMillis()+20000;while(!activity.isFinishing()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(200);
            check(activity.isFinishing()||activity.isDestroyed(),"Actual video completion closes playback page");check(!(Boolean)api("isEndTimerArmed"),"Completion timer clears after firing");
            Class<?> update=loader.loadClass("app.revanced.bilibili.clean.CleanUpdate");Method newer=update.getMethod("newer",String.class,String.class);
            check((Boolean)newer.invoke(null,"v0.4.0","0.3.0")&&!(Boolean)newer.invoke(null,"v0.3.0","0.3.0")&&!(Boolean)newer.invoke(null,"v0.4.0-preview","0.3.0"),"Release comparison rejects current and prerelease versions");
            result.putString("results",report.toString());finish(Activity.RESULT_OK,result);
        } catch(Throwable e) { try{capture("playback-failure");}catch(Throwable ignored){}result.putString("failure",android.util.Log.getStackTraceString(e));result.putString("results",report.toString());finish(Activity.RESULT_CANCELED,result); }
        finally { try {final boolean restore=previous;ui(()->api("setEnabled",new Class<?>[]{boolean.class},restore));}catch(Exception ignored){} }
    }
}
