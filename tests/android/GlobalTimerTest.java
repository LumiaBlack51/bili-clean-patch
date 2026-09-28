package app.biliclean.tests;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.graphics.Rect;
import java.lang.reflect.*;
import java.util.List;

/** Verifies the shared native timer used by the current Theseus details menu. */
public final class GlobalTimerTest extends PlaybackControlsTest {
    Object field(Object obj,String name)throws Exception {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    Dialog open()throws Exception {
        ClassLoader loader=getTargetContext().getClassLoader();
        Dialog dialog=(Dialog)loader.loadClass("com.bilibili.app.comm.timing.ui.TimingReminderSelectDialog")
            .getConstructor(loader.loadClass("androidx.activity.ComponentActivity")).newInstance(activity);
        dialog.show();return dialog;
    }
    void select(Dialog dialog,String title) {
        Rect row=new Rect();ui(()->{View v=find(dialog.getWindow().getDecorView(),title);check(v!=null,"Shared timer option visible: "+title);screenBounds(v,row);});
        tap(row.centerX(),row.centerY());SystemClock.sleep(500);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            ClassLoader loader=getTargetContext().getClassLoader();playback=loader.loadClass("app.revanced.bilibili.clean.CleanPlayback");mediaType=loader.loadClass("tv.danmaku.ijk.media.player.IMediaPlayer");
            getTargetContext().startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("bilibili://video/BV1DuZFB8EZi")).setPackage("tv.danmaku.bili").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.uptimeMillis()+60000;
            while(SystemClock.uptimeMillis()<until){ui(()->{player=ref("media");activity=(Activity)ref("owner");});if(player!=null&&activity!=null&&((Number)media("getDuration")).longValue()>100000)break;SystemClock.sleep(500);}
            check(player!=null&&activity!=null,"Native video ready for shared timer");pause();
            final Dialog[] dialog={null};ui(()->dialog[0]=open());SystemClock.sleep(900);capture("shared-timer-menu");
            select(dialog[0],"播放到当前视频结束");check((Boolean)api("isEndTimerArmed"),"Shared timer touch arms end-of-video mode");
            ui(()->dialog[0]=open());SystemClock.sleep(900);
            ui(()->{
                List<?> rows=(List<?>)field(field(dialog[0],"l"),"a");int selected=0;
                for(Object row:rows)if((Boolean)field(row,"c")){selected++;check(((Long)field(row,"a"))==-2L,"Only end-of-video row selected on reopen");}
                check(selected==1,"Exactly one timer option selected");
            });
            select(dialog[0],"title");check(!(Boolean)api("isEndTimerArmed"),"Native Off row cancels end-of-video mode");
            ui(()->dialog[0]=open());SystemClock.sleep(900);select(dialog[0],"播放到当前视频结束");
            SystemClock.sleep(1500);check((Boolean)api("isEndTimerArmed")&&!activity.isFinishing(),"Paused video keeps shared end timer armed");
            long duration=((Number)media("getDuration")).longValue();seek(duration-3000);ui(()->core("resume"));
            until=SystemClock.uptimeMillis()+20000;while(!activity.isFinishing()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(200);
            check(activity.isFinishing()||activity.isDestroyed(),"Actual completion closes details for shared timer");check(!(Boolean)api("isEndTimerArmed"),"Shared end timer clears after completion");
            result.putString("results",report.toString());finish(Activity.RESULT_OK,result);
        }catch(Throwable e){try{capture("shared-timer-failure");}catch(Throwable ignored){}result.putString("failure",android.util.Log.getStackTraceString(e));result.putString("results",report.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
