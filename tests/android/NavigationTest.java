package app.biliclean.tests;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;

/** Exercise the real bottom button and navigation, not a direct settings activity launch. */
public final class NavigationTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        ActivityMonitor monitor=null;
        Activity settings=null;
        try {
            Activity home=startActivitySync(new Intent().setClassName("tv.danmaku.bili","tv.danmaku.bili.MainActivityV2")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final View[] button={null};
            int id="bilibili://pegasus/promo?biliclean_settings=1".hashCode();
            for(int i=0;i<60 && button[0]==null;i++) {
                runOnMainSync(()->button[0]=home.getWindow().getDecorView().findViewById(id));
                if(button[0]==null)SystemClock.sleep(500);
            }
            if(button[0]==null || !button[0].isShown()) throw new AssertionError("Settings bottom tab not visible");
            View parent=button[0];
            while(parent!=null && !parent.getClass().getName().equals("com.bilibili.lib.homepage.widget.TabHost"))
                parent=parent.getParent() instanceof View ? (View)parent.getParent():null;
            if(parent==null)throw new AssertionError("Not a native bottom tab");
            final View tabHost=parent;
            final Object before=tabHost.getClass().getMethod("getCurrentItem").invoke(tabHost);
            monitor=addMonitor("com.bilibili.app.preferences.BiliPreferencesActivity",null,false);
            runOnMainSync(()->button[0].performClick());
            settings=waitForMonitorWithTimeout(monitor,12000);
            if(settings==null)throw new AssertionError("Bottom click did not open native settings");
            Object after=tabHost.getClass().getMethod("getCurrentItem").invoke(tabHost);
            if(!before.equals(after))throw new AssertionError("Settings click changed selected home tab");
            Activity close=settings; runOnMainSync(close::finish);
            waitForIdleSync();
            if(home.isFinishing())throw new AssertionError("Returning from settings closed home");
            result.putString("stream","PASS real home bottom Settings button visible\nPASS actual native click opens BiliPreferencesActivity\nPASS selected home tab preserved\nPASS return from settings retains home\n");
        } catch(Throwable e) {
            result.putString("failure",e.toString()); result.putString("stream","FAIL "+e+" cause="+e.getCause()+"\n");
        } finally { if(monitor!=null)removeMonitor(monitor); }
        finish(result.containsKey("failure")?0:-1,result);
    }
}
