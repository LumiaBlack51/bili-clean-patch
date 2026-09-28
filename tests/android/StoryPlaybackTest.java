package app.biliclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.graphics.Rect;

/** Exercises home routing and a stale-owner handoff without relying on another preparation. */
public final class StoryPlaybackTest extends PlaybackControlsTest {
    volatile Activity current;
    android.view.accessibility.AccessibilityNodeInfo scrollable(android.view.accessibility.AccessibilityNodeInfo node) {
        if(node==null)return null;
        if(node.isScrollable())return node;
        for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo found=scrollable(node.getChild(i));if(found!=null)return found;}
        return null;
    }
    View storyCard(View v) {
        CharSequence desc=v.createAccessibilityNodeInfo().getContentDescription();
        Rect visible=new Rect();
        if(desc!=null && desc.toString().startsWith("竖版视频") && v.isShown()
            && v.getGlobalVisibleRect(visible) && visible.height()>v.getHeight()/2) return v;
        if(v instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++) {
            View found=storyCard(((android.view.ViewGroup)v).getChildAt(i)); if(found!=null)return found;
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result=new Bundle(); boolean previous=false;
        Application app=(Application)getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity a){current=a;}
            public void onActivityCreated(Activity a,Bundle b){}
            public void onActivityStarted(Activity a){}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){}
        };
        app.registerActivityLifecycleCallbacks(callbacks);
        try {
            ClassLoader loader=getTargetContext().getClassLoader();
            playback=loader.loadClass("app.revanced.bilibili.clean.CleanPlayback");
            mediaType=loader.loadClass("tv.danmaku.ijk.media.player.IMediaPlayer");
            previous=(Boolean)api("enabled");ui(()->api("setEnabled",new Class<?>[]{boolean.class},true));
            getTargetContext().startActivity(new Intent().setClassName("tv.danmaku.bili","tv.danmaku.bili.MainActivityV2").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<40&&current==null;i++)SystemClock.sleep(500);
            activity=current;check(activity!=null,"Home activity resumed");
            final Rect card=new Rect();
            for(int i=0;i<40&&card.isEmpty();i++) {
                ui(()->{View v=storyCard(activity.getWindow().getDecorView());if(v!=null&&v.getGlobalVisibleRect(card))screenBounds(v,card);});
                if(card.isEmpty()&&i>0&&i%4==0) {
                    android.view.accessibility.AccessibilityNodeInfo list=scrollable(getUiAutomation().getRootInActiveWindow());
                    if(list!=null)list.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                }
                SystemClock.sleep(500);
            }
            check(!card.isEmpty(),"Home recommendation contains a real Story video");
            tap(card.centerX(),card.top+card.height()/4);
            long until=SystemClock.uptimeMillis()+45000;
            while(SystemClock.uptimeMillis()<until) {
                ui(()->player=ref("media"));
                if(current!=null&&(current.getClass().getName().contains("StoryVideoActivity")||current.getClass().getName().contains("UnitedBizDetailsActivity"))&&player!=null&&((Number)media("getDuration")).longValue()>15000)break;
                SystemClock.sleep(500);
            }
            Activity home=activity;activity=current;
            boolean isStory=activity.getClass().getName().contains("StoryVideoActivity");
            check(isStory||activity.getClass().getName().contains("UnitedBizDetailsActivity"),"Home recommendation opens native video player");
            report.append("INFO Home route: ").append(isStory?"Story then details":"direct details").append('\n');
            final Activity storyOwner=isStory?activity:home;
            Object storyPlayer=player;
            if(isStory) {
                final Rect details=new Rect();
                ui(()->{View router=find(activity.getWindow().getDecorView(),"story_ctrl_router");check(router!=null,"Story details button exists");screenBounds(router,details);});
                tap(details.centerX(),details.centerY());
            }
            until=SystemClock.uptimeMillis()+15000;
            while(SystemClock.uptimeMillis()<until) {
                if(current!=null&&current.getClass().getName().contains("UnitedBizDetailsActivity")) {
                    activity=current;
                    if(ref("owner")==activity)break;
                }
                SystemClock.sleep(300);
            }
            check(activity.getClass().getName().contains("UnitedBizDetailsActivity"),"Details route opened from homepage");
            check(ref("owner")==activity,"Transferred player belongs to details activity");
            player=ref("media");check(player!=null,"Details has a bound media player after handoff");
            report.append("INFO Host ").append(player==storyPlayer?"reused":"replaced").append(" the Story media player\n");
            // Some host routes repeat onPrepared; force the stale-owner condition while paused
            // to also cover handoffs that do not emit another preparation or clock event.
            pause();
            ui(()->{
                java.lang.reflect.Field ownerField=playback.getDeclaredField("owner");ownerField.setAccessible(true);
                ownerField.set(null,new java.lang.ref.WeakReference<Activity>(storyOwner));
                api("resumed",new Class<?>[]{Activity.class},activity);
            });SystemClock.sleep(1400);
            check(ref("owner")==activity,"Paused handoff repairs stale owner without another onPrepared event");
            pause();controlsVisible();capture("story-details-controls");
            long initial=position();doubleTap(.9f);check(Math.abs(position()-initial-10000)<1500,"Details right double tap +10 seconds after handoff");
            controlsVisible();final Rect play=new Rect();ui(()->screenBounds(find(activity.getWindow().getDecorView(),"biliclean_暂停或播放"),play));
            tap(play.centerX(),play.centerY());SystemClock.sleep(600);check((Boolean)media("isPlaying"),"Details center control resumes transferred player");pause();
            final Dialog[] dialog={null};
            ui(()->{
                dialog[0]=(Dialog)loader.loadClass("com.bilibili.app.comm.timing.ui.TimingReminderSelectDialog")
                    .getConstructor(loader.loadClass("androidx.activity.ComponentActivity")).newInstance(activity);
                dialog[0].show();
            });SystemClock.sleep(900);
            ui(()->{View row=find(dialog[0].getWindow().getDecorView(),"播放到当前视频结束");check(row!=null,"Current shared timer menu includes end-of-video option");((View)row.getParent()).performClick();});
            check((Boolean)api("isEndTimerArmed"),"Shared timer row arms end-of-video mode");
            ui(()->dialog[0].dismiss());
            long duration=((Number)media("getDuration")).longValue();seek(duration-2000);ui(()->core("resume"));
            until=SystemClock.uptimeMillis()+18000;
            while(!activity.isFinishing()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(300);
            check(activity.isFinishing()||activity.isDestroyed(),"Transferred video completion closes details before autoplay");
            result.putString("results",report.toString());finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {try{capture("story-failure");}catch(Throwable ignored){}result.putString("failure",android.util.Log.getStackTraceString(e));result.putString("results",report.toString());finish(Activity.RESULT_CANCELED,result);}
        finally {
            app.unregisterActivityLifecycleCallbacks(callbacks);
            final boolean restore=previous;try{ui(()->api("setEnabled",new Class<?>[]{boolean.class},restore));}catch(Throwable ignored){}
        }
    }
}
